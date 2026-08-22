package com.atlas.knowledgebase.chat;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.CancellationSource;
import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.providers.ProviderConnectionRecord;
import com.atlas.knowledgebase.providers.ProviderConnectionRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.retrieval.ProviderExecution;
import com.atlas.knowledgebase.retrieval.RetrievalEligibility;
import com.atlas.knowledgebase.retrieval.RetrievalProperties;
import com.atlas.knowledgebase.retrieval.RetrievalScope;
import com.atlas.knowledgebase.retrieval.RetrieverRegistry;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Current, fail-closed authorization for reopening or replaying source-derived Chat content.
 * Answer-time snapshots are evidence of provenance, never a durable access grant.
 */
@Component
final class ChatHistoryAuthorizationService {

    private static final int MAX_SCOPE = 5;

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final KbAccessService access;
    private final RetrievalProperties retrievalProperties;
    private final RetrieverRegistry retrievers;
    private final ProviderExecution providerExecution;
    private final ProviderConnectionRepository providerConnections;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ChatHistoryAuthorizationService(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            KbAccessService access,
            RetrievalProperties retrievalProperties,
            RetrieverRegistry retrievers,
            ProviderExecution providerExecution,
            ProviderConnectionRepository providerConnections,
            ObjectMapper objectMapper,
            Clock clock) {
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.access = access;
        this.retrievalProperties = retrievalProperties;
        this.retrievers = retrievers;
        this.providerExecution = providerExecution;
        this.providerConnections = providerConnections;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    boolean canExpose(AtlasUserRecord user, ChatMessageRecord message) {
        if (user == null
                || message == null
                || !"assistant".equals(message.role())
                || !"completed".equals(message.status())) {
            return false;
        }
        try {
            List<String> storedScope = readScope(message.logicalKbScopeJson());
            RetrievalScope current = currentScope(user, storedScope);
            if (!sameAnswerTimeScope(message, current)) {
                return false;
            }
            for (RetrievalScope.KnowledgeBaseSnapshot snapshot : current.knowledgeBases()) {
                if (snapshot.bindings().isEmpty()) {
                    return false;
                }
                for (BindingRecord binding : snapshot.bindings()) {
                    if (!RetrievalEligibility.isEligible(
                            snapshot.knowledgeBase(), binding, retrievalProperties)) {
                        return false;
                    }
                    if (!providerConnectionUsable(binding)) {
                        return false;
                    }
                    if (!adapterAuthorized(user, snapshot.knowledgeBase(), binding)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException deniedOrDrifted) {
            return false;
        }
    }

    private RetrievalScope currentScope(AtlasUserRecord user, List<String> requestedIds) {
        if (requestedIds.isEmpty() || requestedIds.size() > MAX_SCOPE) {
            throw new IllegalArgumentException("invalid stored Chat scope");
        }
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("duplicate stored Chat scope");
        }
        List<RetrievalScope.KnowledgeBaseSnapshot> snapshots = new ArrayList<>();
        for (String logicalKbId : requestedIds) {
            LogicalKnowledgeBaseRecord kb =
                    knowledgeBases.findById(logicalKbId).orElseThrow();
            if (!access.chatEligible(user, kb)) {
                throw new IllegalArgumentException("stored Chat KB is no longer eligible");
            }
            snapshots.add(
                    new RetrievalScope.KnowledgeBaseSnapshot(
                            kb, bindings.findByLogicalKbId(logicalKbId)));
        }
        return new RetrievalScope(snapshots);
    }

    private boolean sameAnswerTimeScope(ChatMessageRecord message, RetrievalScope current) {
        try {
            List<Map<String, String>> storedBindings =
                    objectMapper.readValue(
                            message.bindingSetJson(),
                            new TypeReference<List<Map<String, String>>>() {});
            Set<Map<String, String>> storedBindingSet =
                    storedBindings == null ? Set.of() : new HashSet<>(storedBindings);
            Set<Map<String, String>> currentBindingSet =
                    new HashSet<>(current.bindingSnapshots());
            if (storedBindingSet.isEmpty()
                    || storedBindingSet.size() != currentBindingSet.size()
                    || !storedBindingSet.equals(currentBindingSet)) {
                return false;
            }
            JsonNode storedVersions = objectMapper.readTree(message.configVersionsJson());
            JsonNode currentVersions = objectMapper.valueToTree(current.configVersions());
            return storedVersions != null && storedVersions.equals(currentVersions);
        } catch (JsonProcessingException | RuntimeException malformedSnapshot) {
            return false;
        }
    }

    private boolean providerConnectionUsable(BindingRecord binding) {
        String provider = providerConnectionName(binding.providerProfile());
        if (provider == null) {
            return true;
        }
        String owner = binding.credentialOwner();
        if (owner == null || owner.isBlank()) {
            return false;
        }
        ProviderConnectionRecord connection =
                providerConnections.findByUserAndProvider(owner, provider).orElse(null);
        if (connection == null || !"connected".equals(connection.status())) {
            return false;
        }
        Instant expiresAt = connection.expiresAt();
        return expiresAt == null || expiresAt.isAfter(clock.instant());
    }

    private String providerConnectionName(String providerProfile) {
        return switch (providerProfile) {
            case "git_markdown" -> "github";
            case "confluence" -> "confluence";
            default -> null;
        };
    }

    private boolean adapterAuthorized(
            AtlasUserRecord user,
            LogicalKnowledgeBaseRecord kb,
            BindingRecord binding) {
        Retriever retriever = retrievers.find(binding.providerProfile()).orElse(null);
        if (retriever == null) {
            return false;
        }
        CancellationSource cancellation = new CancellationSource();
        try {
            ProviderExecution.TimedCall<Retriever.AuthorizationResult> call =
                    providerExecution.submit(
                            binding.providerProfile(),
                            cancellation,
                            timeout ->
                                    retriever.authorize(
                                            new Retriever.AuthorizationRequest(
                                                    "hist_" + SessionService.randomToken().substring(0, 16),
                                                    user.userId(),
                                                    kb.logicalKbId(),
                                                    binding.bindingId(),
                                                    binding.providerProfile(),
                                                    binding.sourceIdentityJson(),
                                                    timeout,
                                                    cancellation)));
            Retriever.AuthorizationResult result = providerExecution.await(call);
            return result != null
                    && result.outcome() == Retriever.AuthorizationOutcome.AUTHORIZED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException deniedOrUnknown) {
            return false;
        } finally {
            cancellation.cancel();
        }
    }

    private List<String> readScope(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return ids == null || ids.stream().anyMatch(id -> id == null || id.isBlank())
                    ? List.of()
                    : List.copyOf(ids);
        } catch (JsonProcessingException malformed) {
            return List.of();
        }
    }
}
