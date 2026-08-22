package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.adapters.RetrieverException;
import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/**
 * Fans out to stub retrievers in parallel, builds a coverage map, and fuses hits with in-process
 * RRF. Browse-only / model-ineligible bindings are never retrieved.
 */
@Service
public class RetrievalOrchestrator {

    static final Duration BINDING_TIMEOUT = Duration.ofSeconds(2);

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final KbAccessService access;
    private final RetrieverRegistry retrievers;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public RetrievalOrchestrator(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            KbAccessService access,
            RetrieverRegistry retrievers,
            AuditEventRepository auditEvents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.access = access;
        this.retrievers = retrievers;
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public RetrievalTurn retrieve(AtlasUserRecord user, String question, List<String> logicalKbIds) {
        List<String> successful = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> timedOut = new ArrayList<>();
        List<ReciprocalRankFusion.RankedList> ranked = new ArrayList<>();
        Set<String> securityKbs = new LinkedHashSet<>();
        Set<String> unknownKbs = new LinkedHashSet<>();
        String blockKb = null;
        String blockBinding = null;

        List<BindingWork> work = new ArrayList<>();
        for (String logicalKbId : logicalKbIds) {
            LogicalKnowledgeBaseRecord kb = knowledgeBases.findById(logicalKbId).orElse(null);
            if (kb == null || !access.authorized(user, kb) || !access.chatEligible(user, kb)) {
                continue;
            }
            List<BindingRecord> current =
                    bindings.findByLogicalKbId(logicalKbId).stream()
                            .filter(binding -> binding.enabled() && !binding.killSwitch())
                            .toList();
            boolean missingBinding = false;
            for (BindingRecord binding : current) {
                if (!bindingAuthorized(user, kb, binding)) {
                    missingBinding = true;
                    blockKb = logicalKbId;
                    blockBinding = binding.bindingId();
                    failed.add(binding.bindingId());
                }
            }
            if (missingBinding) {
                continue;
            }
            for (BindingRecord binding : current) {
                work.add(new BindingWork(kb, binding));
            }
        }

        List<CompletableFuture<BindingOutcome>> futures = new ArrayList<>();
        for (BindingWork item : work) {
            futures.add(
                    CompletableFuture.supplyAsync(
                                    () -> retrieveOne(user, question, item), workers)
                            .orTimeout(BINDING_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                            .exceptionally(
                                    error ->
                                            new BindingOutcome(
                                                    item.kb().logicalKbId(),
                                                    item.binding(),
                                                    classifyFailure(error))));
        }
        for (CompletableFuture<BindingOutcome> future : futures) {
            BindingOutcome outcome = future.join();
            String bindingId = outcome.binding().bindingId();
            String logicalKbId = outcome.logicalKbId();
            switch (outcome.result().outcome()) {
                case TIMEOUT -> timedOut.add(bindingId);
                case FAILED -> failed.add(bindingId);
                case SECURITY -> {
                    failed.add(bindingId);
                    securityKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                    knowledgeBases.suspend(logicalKbId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            "retrieve",
                            "deny",
                            "fail_closed");
                }
                case UNKNOWN -> {
                    failed.add(bindingId);
                    unknownKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            "retrieve",
                            "deny",
                            "unknown");
                }
                case SUCCESS -> {
                    if (securityKbs.contains(logicalKbId) || unknownKbs.contains(logicalKbId)) {
                        break;
                    }
                    successful.add(bindingId);
                    ranked.add(
                            new ReciprocalRankFusion.RankedList(
                                    logicalKbId,
                                    bindingId,
                                    outcome.binding().providerProfile(),
                                    outcome.result().hits()));
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            "retrieve",
                            "allow",
                            "ok");
                }
            }
        }

        Set<String> blockedKbs = new LinkedHashSet<>(securityKbs);
        blockedKbs.addAll(unknownKbs);
        if (!blockedKbs.isEmpty()) {
            successful.removeIf(
                    bindingId -> {
                        BindingRecord binding = bindings.findById(bindingId).orElse(null);
                        return binding != null && blockedKbs.contains(binding.logicalKbId());
                    });
            ranked.removeIf(list -> blockedKbs.contains(list.logicalKbId()));
        }

        List<ReciprocalRankFusion.FusedHit> fused = ReciprocalRankFusion.fuse(ranked);
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("successful", List.copyOf(successful));
        coverage.put("failed", List.copyOf(failed));
        coverage.put("timed_out", List.copyOf(timedOut));

        RetrievalTurn.Block block = RetrievalTurn.Block.NONE;
        boolean anyEvidence = !fused.isEmpty();
        if (!securityKbs.isEmpty()) {
            block = RetrievalTurn.Block.SECURITY;
        } else if (!unknownKbs.isEmpty()) {
            block = RetrievalTurn.Block.UNKNOWN;
        } else if (blockBinding != null) {
            block = RetrievalTurn.Block.BINDING_ACCESS;
        } else if (!anyEvidence) {
            block = RetrievalTurn.Block.NO_EVIDENCE;
        }

        return new RetrievalTurn(
                coverage, fused, List.of(), null, block, blockKb, blockBinding);
    }

    @PreDestroy
    void closeWorkers() {
        workers.close();
    }

    private BindingOutcome retrieveOne(AtlasUserRecord user, String question, BindingWork item) {
        BindingRecord binding = item.binding();
        LogicalKnowledgeBaseRecord kb = item.kb();
        if (!"chat_ready".equals(kb.capability()) || !kb.modelEligible()) {
            return new BindingOutcome(kb.logicalKbId(), binding, Retriever.Result.failed());
        }
        Retriever retriever = retrievers.find(binding.providerProfile()).orElse(null);
        if (retriever == null) {
            return new BindingOutcome(kb.logicalKbId(), binding, Retriever.Result.failed());
        }
        Retriever.Result result =
                retriever.retrieve(
                        new Retriever.Request(
                                "ret_" + SessionService.randomToken().substring(0, 12),
                                question,
                                user.userId(),
                                kb.logicalKbId(),
                                binding.bindingId(),
                                binding.providerProfile(),
                                binding.sourceIdentityJson(),
                                BINDING_TIMEOUT));
        return new BindingOutcome(kb.logicalKbId(), binding, result);
    }

    private Retriever.Result classifyFailure(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof TimeoutException) {
            return Retriever.Result.timeout();
        }
        if (cause instanceof RetrieverException retrieverException) {
            return switch (retrieverException.outcome()) {
                case SECURITY -> Retriever.Result.security();
                case FAILED -> Retriever.Result.failed();
                default -> Retriever.Result.unknown();
            };
        }
        return Retriever.Result.unknown();
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean bindingAuthorized(
            AtlasUserRecord user, LogicalKnowledgeBaseRecord kb, BindingRecord binding) {
        if (!access.authorized(user, kb)) {
            return false;
        }
        return !"binding_denied".equals(fixture(binding.sourceIdentityJson()));
    }

    private String fixture(String sourceIdentityJson) {
        if (sourceIdentityJson == null || sourceIdentityJson.isBlank()) {
            return "success";
        }
        try {
            JsonNode node = objectMapper.readTree(sourceIdentityJson);
            String named = node.path("retrieval_fixture").asText("");
            return named.isBlank() ? "success" : named;
        } catch (JsonProcessingException e) {
            return "success";
        }
    }

    private void audit(
            String userId,
            String logicalKbId,
            String bindingId,
            String action,
            String authorization,
            String status) {
        try {
            auditEvents.insert(
                    new AuditEventRecord(
                            "aud_" + SessionService.randomToken().substring(0, 16),
                            clock.instant(),
                            userId,
                            logicalKbId,
                            bindingId,
                            null,
                            action,
                            authorization,
                            null,
                            null,
                            null,
                            status,
                            null,
                            objectMapper.writeValueAsString(Map.of("action", action))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize content-free audit details", e);
        }
    }

    private record BindingWork(LogicalKnowledgeBaseRecord kb, BindingRecord binding) {}

    private record BindingOutcome(
            String logicalKbId, BindingRecord binding, Retriever.Result result) {}
}
