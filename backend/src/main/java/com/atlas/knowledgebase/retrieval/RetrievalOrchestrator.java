package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.Retriever;
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
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private final List<Retriever> retrievers;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public RetrievalOrchestrator(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            KbAccessService access,
            List<Retriever> retrievers,
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
        Set<String> remainingKbs = new LinkedHashSet<>();
        Set<String> excludedKbs = new LinkedHashSet<>();
        Set<String> securityKbs = new LinkedHashSet<>();
        String blockKb = null;
        String blockBinding = null;

        List<BindingWork> work = new ArrayList<>();
        for (String logicalKbId : logicalKbIds) {
            LogicalKnowledgeBaseRecord kb = knowledgeBases.findById(logicalKbId).orElse(null);
            if (kb == null || !access.authorized(user, kb) || !access.chatEligible(user, kb)) {
                excludedKbs.add(logicalKbId);
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
                excludedKbs.add(logicalKbId);
                continue;
            }
            remainingKbs.add(logicalKbId);
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
                                                    Retriever.Result.timeout())));
        }
        for (CompletableFuture<BindingOutcome> future : futures) {
            BindingOutcome outcome = future.join();
            String bindingId = outcome.binding().bindingId();
            String logicalKbId = outcome.logicalKbId();
            switch (outcome.result().outcome()) {
                case TIMEOUT -> timedOut.add(bindingId);
                case FAILED -> failed.add(bindingId);
                case SECURITY -> {
                    securityKbs.add(logicalKbId);
                    remainingKbs.remove(logicalKbId);
                    knowledgeBases.suspend(logicalKbId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            "retrieve",
                            "deny",
                            "fail_closed");
                }
                case SUCCESS -> {
                    if (securityKbs.contains(logicalKbId)) {
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

        if (!securityKbs.isEmpty()) {
            successful.removeIf(
                    bindingId -> {
                        BindingRecord binding = bindings.findById(bindingId).orElse(null);
                        return binding != null && securityKbs.contains(binding.logicalKbId());
                    });
            ranked.removeIf(list -> securityKbs.contains(list.logicalKbId()));
        }

        List<ReciprocalRankFusion.FusedHit> fused = ReciprocalRankFusion.fuse(ranked);
        List<Map<String, Object>> citations = new ArrayList<>();
        int index = 1;
        for (ReciprocalRankFusion.FusedHit fusedHit : fused) {
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("citation_id", "cit_" + index++);
            citation.put("logical_kb_id", fusedHit.logicalKbId());
            citation.put("binding_id", fusedHit.bindingId());
            citation.put("provider", fusedHit.provider());
            citation.put("title", fusedHit.hit().title());
            citations.add(citation);
        }

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("successful", List.copyOf(successful));
        coverage.put("failed", List.copyOf(failed));
        coverage.put("timed_out", List.copyOf(timedOut));

        RetrievalTurn.Block block = RetrievalTurn.Block.NONE;
        boolean anySuccess = !successful.isEmpty();
        if (!anySuccess && !excludedKbs.isEmpty() && securityKbs.isEmpty() && timedOut.isEmpty()) {
            block = RetrievalTurn.Block.BINDING_ACCESS;
        } else if (!anySuccess && !securityKbs.isEmpty()) {
            block = RetrievalTurn.Block.SECURITY;
        }

        return new RetrievalTurn(
                coverage, fused, citations, null, block, blockKb, blockBinding);
    }

    private BindingOutcome retrieveOne(AtlasUserRecord user, String question, BindingWork item) {
        BindingRecord binding = item.binding();
        LogicalKnowledgeBaseRecord kb = item.kb();
        if (!"chat_ready".equals(kb.capability()) || !kb.modelEligible()) {
            return new BindingOutcome(kb.logicalKbId(), binding, Retriever.Result.failed());
        }
        Retriever retriever =
                retrievers.stream()
                        .filter(candidate -> candidate.supports(binding.providerProfile()))
                        .findFirst()
                        .orElse(null);
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
