package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.CancellationSource;
import com.atlas.knowledgebase.adapters.CancellationToken;
import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.adapters.RetrieverException;
import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.audit.ConnectorTelemetry;
import com.atlas.knowledgebase.chat.ChatClassificationPolicy;
import com.atlas.knowledgebase.chat.ChatValidationException;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.security.UntrustedContentContainment;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/**
 * Fans out to stub retrievers in parallel, builds a coverage map, and fuses hits with in-process
 * RRF. Browse-only / model-ineligible bindings are never retrieved.
 */
@Service
public class RetrievalOrchestrator {

    private static final UntrustedContentContainment CONTENT_CONTAINMENT =
            new UntrustedContentContainment();

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final KbAccessService access;
    private final ChatClassificationPolicy classificationPolicy;
    private final RetrieverRegistry retrievers;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RetrievalProperties properties;
    private final ProviderExecution providerExecution;

    public RetrievalOrchestrator(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            KbAccessService access,
            ChatClassificationPolicy classificationPolicy,
            RetrieverRegistry retrievers,
            AuditEventRepository auditEvents,
            ObjectMapper objectMapper,
            Clock clock,
            RetrievalProperties properties,
            ProviderExecution providerExecution) {
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.access = access;
        this.classificationPolicy = classificationPolicy;
        this.retrievers = retrievers;
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.properties = properties;
        this.providerExecution = providerExecution;
    }

    public RetrievalTurn retrieve(AtlasUserRecord user, String question, RetrievalScope scope) {
        return retrieve(user, question, scope, new CancellationSource());
    }

    public RetrievalTurn retrieve(
            AtlasUserRecord user,
            String question,
            RetrievalScope scope,
            CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        Set<String> successful = new LinkedHashSet<>();
        Set<String> failed = new LinkedHashSet<>();
        Set<String> timedOut = new LinkedHashSet<>();
        Set<String> quotaLimited = new LinkedHashSet<>();
        Set<String> promptInjectionContained = new LinkedHashSet<>();
        Map<String, String> retryAfter = new LinkedHashMap<>();
        List<ReciprocalRankFusion.RankedList> ranked = new ArrayList<>();
        Set<String> accessDeniedKbs = new LinkedHashSet<>();
        Set<String> unavailableKbs = new LinkedHashSet<>();
        Set<String> quotaKbs = new LinkedHashSet<>();
        Set<String> securityKbs = new LinkedHashSet<>();
        Set<String> unknownKbs = new LinkedHashSet<>();
        Map<String, List<BindingRecord>> currentBindings = new LinkedHashMap<>();
        EnumMap<RetrievalTurn.Block, BlockCandidate> blockCandidates =
                new EnumMap<>(RetrievalTurn.Block.class);

        List<BindingWork> authorizationWork = new ArrayList<>();
        List<RetrievalScope.KnowledgeBaseSnapshot> authoritativeSnapshots = new ArrayList<>();
        for (RetrievalScope.KnowledgeBaseSnapshot snapshot : scope.knowledgeBases()) {
            cancellation.throwIfCancelled();
            String logicalKbId = snapshot.knowledgeBase().logicalKbId();
            List<BindingRecord> authoritativeBindings =
                    bindings.findByLogicalKbId(logicalKbId);
            List<BindingRecord> coverageBindings =
                    RetrievalDispatchGuard.mergeBindings(
                            snapshot.bindings(), authoritativeBindings);
            currentBindings.put(logicalKbId, coverageBindings);
            LogicalKnowledgeBaseRecord kb = knowledgeBases.findById(logicalKbId).orElse(null);
            if (kb == null) {
                unavailableKbs.add(logicalKbId);
                markWholeKbFailed(coverageBindings, failed, timedOut);
                recordBlock(
                        blockCandidates,
                        RetrievalTurn.Block.BINDING_UNAVAILABLE,
                        logicalKbId,
                        firstBindingId(coverageBindings));
                continue;
            }
            authoritativeSnapshots.add(
                    new RetrievalScope.KnowledgeBaseSnapshot(kb, authoritativeBindings));
            boolean invalid = false;
            if (!Objects.equals(
                    snapshot.knowledgeBase().classification(), kb.classification())) {
                securityKbs.add(logicalKbId);
                markWholeKbFailed(coverageBindings, failed, timedOut);
                recordBlock(
                        blockCandidates,
                        RetrievalTurn.Block.SECURITY,
                        logicalKbId,
                        firstBindingId(coverageBindings));
                invalid = true;
            }
            if (!access.authorized(user, kb)) {
                accessDeniedKbs.add(logicalKbId);
                markWholeKbFailed(coverageBindings, failed, timedOut);
                recordBlock(
                        blockCandidates,
                        RetrievalTurn.Block.BINDING_ACCESS,
                        logicalKbId,
                        firstBindingId(coverageBindings));
                invalid = true;
            } else if (!access.chatEligible(user, kb)) {
                unavailableKbs.add(logicalKbId);
                markWholeKbFailed(coverageBindings, failed, timedOut);
                recordBlock(
                        blockCandidates,
                        RetrievalTurn.Block.BINDING_UNAVAILABLE,
                        logicalKbId,
                        firstBindingId(coverageBindings));
                invalid = true;
            }
            if (snapshot.knowledgeBase().configVersion() != kb.configVersion()
                    || !RetrievalDispatchGuard.sameBindings(
                            snapshot.bindings(), authoritativeBindings)) {
                unavailableKbs.add(logicalKbId);
                markWholeKbFailed(coverageBindings, failed, timedOut);
                recordBlock(
                        blockCandidates,
                        RetrievalTurn.Block.BINDING_UNAVAILABLE,
                        logicalKbId,
                        firstBindingId(coverageBindings));
                invalid = true;
            }
            if (invalid) {
                continue;
            }
            boolean runtimeUnavailable = false;
            for (BindingRecord binding : authoritativeBindings) {
                if (!runtimeEligible(kb, binding)) {
                    runtimeUnavailable = true;
                    failed.add(binding.bindingId());
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.BINDING_UNAVAILABLE,
                            logicalKbId,
                            binding.bindingId());
                }
            }
            if (runtimeUnavailable) {
                unavailableKbs.add(logicalKbId);
                markWholeKbFailed(authoritativeBindings, failed, timedOut);
                continue;
            }
            for (BindingRecord binding : authoritativeBindings) {
                authorizationWork.add(new BindingWork(kb, binding));
            }
        }

        try {
            if (!authoritativeSnapshots.isEmpty()) {
                classificationPolicy.resolve(authoritativeSnapshots);
            }
        } catch (ChatValidationException invalidClassification) {
            authorizationWork.clear();
            for (RetrievalScope.KnowledgeBaseSnapshot snapshot : authoritativeSnapshots) {
                String logicalKbId = snapshot.knowledgeBase().logicalKbId();
                securityKbs.add(logicalKbId);
                List<BindingRecord> kbBindings =
                        currentBindings.getOrDefault(logicalKbId, List.of());
                markWholeKbFailed(kbBindings, failed, timedOut);
                recordBlock(
                        blockCandidates,
                        RetrievalTurn.Block.SECURITY,
                        logicalKbId,
                        firstBindingId(kbBindings));
            }
        }

        List<BindingWork> retrievalWork = new ArrayList<>();
        List<PendingAuthorization> authorizationCalls = new ArrayList<>();
        for (BindingWork item : authorizationWork) {
            authorizationCalls.add(
                    new PendingAuthorization(
                            item,
                            providerExecution.submit(
                                    item.binding().providerProfile(),
                                    "authorize",
                                    cancellation,
                                    timeout ->
                                            authorizeOne(
                                                    user,
                                                    item,
                                                    timeout,
                                                    cancellation))));
        }
        for (PendingAuthorization pending : authorizationCalls) {
            cancellation.throwIfCancelled();
            BindingAuthorization authorization = awaitAuthorization(pending);
            cancellation.throwIfCancelled();
            BindingWork item = authorization.item();
            String logicalKbId = item.kb().logicalKbId();
            String bindingId = item.binding().bindingId();
            captureRetryAfter(retryAfter, bindingId, authorization.result().retryAfter());
            switch (authorization.result().outcome()) {
                case AUTHORIZED -> {
                    providerExecution.recordSuccess(item.binding().providerProfile());
                    retrievalWork.add(item);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            item.binding().providerProfile(),
                            "authorize",
                            "allow",
                            "ok",
                            pending.call().latencyMs(),
                            null);
                }
                case ACCESS_DENIED -> {
                    failed.add(bindingId);
                    accessDeniedKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.BINDING_ACCESS,
                            logicalKbId,
                            bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            item.binding().providerProfile(),
                            "authorize",
                            "deny",
                            "denied",
                            pending.call().latencyMs(),
                            "authorization");
                }
                case QUOTA -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.QUOTA,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    quotaLimited.add(bindingId);
                    quotaKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.QUOTA,
                            logicalKbId,
                            bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            item.binding().providerProfile(),
                            "authorize",
                            "unknown",
                            "quota",
                            pending.call().latencyMs(),
                            "quota");
                }
                case TIMEOUT -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.TIMEOUT,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    timedOut.add(bindingId);
                    unavailableKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.BINDING_UNAVAILABLE,
                            logicalKbId,
                            bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            item.binding().providerProfile(),
                            "authorize",
                            "unknown",
                            "timeout",
                            pending.call().latencyMs(),
                            "timeout");
                }
                case FAILED -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.RETRIEVAL,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    failed.add(bindingId);
                    unavailableKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.BINDING_UNAVAILABLE,
                            logicalKbId,
                            bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            item.binding().providerProfile(),
                            "authorize",
                            "unknown",
                            "failed",
                            pending.call().latencyMs(),
                            "retrieval");
                }
                case SECURITY -> {
                    failed.add(bindingId);
                    securityKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.SECURITY,
                            logicalKbId,
                            bindingId);
                    knowledgeBases.suspend(logicalKbId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            item.binding().providerProfile(),
                            "authorize",
                            "deny",
                            "fail_closed",
                            pending.call().latencyMs(),
                            "security");
                }
                case UNKNOWN -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.UNKNOWN,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    failed.add(bindingId);
                    unknownKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.UNKNOWN,
                            logicalKbId,
                            bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            item.binding().providerProfile(),
                            "authorize",
                            "unknown",
                            "unknown",
                            pending.call().latencyMs(),
                            "unknown");
                }
            }
        }

        Set<String> preRetrievalBlocked = new LinkedHashSet<>(accessDeniedKbs);
        preRetrievalBlocked.addAll(quotaKbs);
        preRetrievalBlocked.addAll(unavailableKbs);
        preRetrievalBlocked.addAll(securityKbs);
        preRetrievalBlocked.addAll(unknownKbs);
        for (String blockedKb : preRetrievalBlocked) {
            markWholeKbFailed(currentBindings.getOrDefault(blockedKb, List.of()), failed, timedOut);
        }
        retrievalWork.removeIf(item -> preRetrievalBlocked.contains(item.kb().logicalKbId()));

        List<PendingRetrieval> retrievalCalls = new ArrayList<>();
        for (BindingWork item : retrievalWork) {
            retrievalCalls.add(
                    new PendingRetrieval(
                            item,
                            providerExecution.submit(
                                    item.binding().providerProfile(),
                                    "retrieve",
                                    cancellation,
                                    timeout ->
                                            retrieveOne(
                                                    user,
                                                    question,
                                                    item,
                                                    timeout,
                                                    cancellation))));
        }
        for (PendingRetrieval pending : retrievalCalls) {
            cancellation.throwIfCancelled();
            BindingOutcome outcome = awaitRetrieval(pending);
            cancellation.throwIfCancelled();
            String bindingId = outcome.binding().bindingId();
            String logicalKbId = outcome.logicalKbId();
            captureRetryAfter(retryAfter, bindingId, outcome.result().retryAfter());
            switch (outcome.result().outcome()) {
                case QUOTA -> {
                    recordFailure(
                            outcome.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.QUOTA,
                            outcome.result().retryAfter(),
                            outcome.providerRejected());
                    quotaLimited.add(bindingId);
                    quotaKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.QUOTA,
                            logicalKbId,
                            bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            outcome.binding().providerProfile(),
                            "retrieve",
                            "unknown",
                            "quota",
                            pending.call().latencyMs(),
                            "quota");
                }
                case TIMEOUT -> {
                    recordFailure(
                            outcome.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.TIMEOUT,
                            outcome.result().retryAfter(),
                            outcome.providerRejected());
                    timedOut.add(bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            outcome.binding().providerProfile(),
                            "retrieve",
                            "unknown",
                            "timeout",
                            pending.call().latencyMs(),
                            "timeout");
                }
                case FAILED -> {
                    recordFailure(
                            outcome.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.RETRIEVAL,
                            outcome.result().retryAfter(),
                            outcome.providerRejected());
                    failed.add(bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            outcome.binding().providerProfile(),
                            "retrieve",
                            "unknown",
                            "failed",
                            pending.call().latencyMs(),
                            "retrieval");
                }
                case SECURITY -> {
                    failed.add(bindingId);
                    securityKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.SECURITY,
                            logicalKbId,
                            bindingId);
                    knowledgeBases.suspend(logicalKbId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            outcome.binding().providerProfile(),
                            "retrieve",
                            "deny",
                            "fail_closed",
                            pending.call().latencyMs(),
                            "security");
                }
                case UNKNOWN -> {
                    recordFailure(
                            outcome.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.UNKNOWN,
                            outcome.result().retryAfter(),
                            outcome.providerRejected());
                    failed.add(bindingId);
                    unknownKbs.add(logicalKbId);
                    recordBlock(
                            blockCandidates,
                            RetrievalTurn.Block.UNKNOWN,
                            logicalKbId,
                            bindingId);
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            outcome.binding().providerProfile(),
                            "retrieve",
                            "unknown",
                            "unknown",
                            pending.call().latencyMs(),
                            "unknown");
                }
                case SUCCESS -> {
                    providerExecution.recordSuccess(outcome.binding().providerProfile());
                    if (securityKbs.contains(logicalKbId) || unknownKbs.contains(logicalKbId)) {
                        break;
                    }
                    successful.add(bindingId);
                    List<Retriever.Hit> safeHits = new ArrayList<>();
                    boolean contained = false;
                    for (Retriever.Hit hit : outcome.result().hits()) {
                        UntrustedContentContainment.Decision decision =
                                CONTENT_CONTAINMENT.inspect(hit);
                        if (decision.contained()) {
                            contained = true;
                            continue;
                        }
                        safeHits.add(hit);
                    }
                    if (contained) {
                        promptInjectionContained.add(bindingId);
                        audit(
                                user.userId(),
                                logicalKbId,
                                bindingId,
                                outcome.binding().providerProfile(),
                                "prompt_injection_contained",
                                "deny",
                                "contained",
                                pending.call().latencyMs(),
                                "security");
                    }
                    if (!safeHits.isEmpty()) {
                        ranked.add(
                                new ReciprocalRankFusion.RankedList(
                                        logicalKbId,
                                        bindingId,
                                        outcome.binding().providerProfile(),
                                        safeHits));
                    }
                    audit(
                            user.userId(),
                            logicalKbId,
                            bindingId,
                            outcome.binding().providerProfile(),
                            "retrieve",
                            "allow",
                            "ok",
                            pending.call().latencyMs(),
                            null);
                }
            }
        }

        Set<String> blockedKbs = new LinkedHashSet<>(securityKbs);
        blockedKbs.addAll(unknownKbs);
        if (!blockedKbs.isEmpty()) {
            Set<String> blockedBindingIds = new LinkedHashSet<>();
            blockedKbs.forEach(
                    blockedKb ->
                            currentBindings.getOrDefault(blockedKb, List.of()).stream()
                                    .map(BindingRecord::bindingId)
                                    .forEach(blockedBindingIds::add));
            successful.removeAll(blockedBindingIds);
            ranked.removeIf(list -> blockedKbs.contains(list.logicalKbId()));
            for (String blockedKb : blockedKbs) {
                markWholeKbFailed(
                        currentBindings.getOrDefault(blockedKb, List.of()), failed, timedOut);
            }
        }

        cancellation.throwIfCancelled();
        List<ReciprocalRankFusion.FusedHit> fused = ReciprocalRankFusion.fuse(ranked);
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("successful", List.copyOf(successful));
        coverage.put("failed", List.copyOf(failed));
        coverage.put("timed_out", List.copyOf(timedOut));
        coverage.put("quota_limited", List.copyOf(quotaLimited));
        coverage.put("retry_after", Map.copyOf(retryAfter));
        coverage.put("prompt_injection_contained", List.copyOf(promptInjectionContained));

        boolean anyEvidence = !fused.isEmpty();
        BlockSelection selection = selectBlock(blockCandidates, anyEvidence);

        return new RetrievalTurn(
                coverage,
                fused,
                List.of(),
                null,
                scope,
                selection.block(),
                selection.logicalKbId(),
                selection.bindingId());
    }

    private BindingAuthorization authorizeOne(
            AtlasUserRecord user,
            BindingWork item,
            Duration timeout,
            CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        BindingRecord binding = item.binding();
        Retriever retriever = retrievers.find(binding.providerProfile()).orElse(null);
        if (retriever == null) {
            return new BindingAuthorization(
                    item, Retriever.AuthorizationResult.failed(), false);
        }
        Retriever.AuthorizationResult result =
                retriever.authorize(
                        new Retriever.AuthorizationRequest(
                                "auth_" + SessionService.randomToken().substring(0, 12),
                                user.userId(),
                                item.kb().logicalKbId(),
                                binding.bindingId(),
                                binding.providerProfile(),
                                binding.sourceIdentityJson(),
                                timeout,
                                cancellation));
        return new BindingAuthorization(item, result, false);
    }

    private BindingOutcome retrieveOne(
            AtlasUserRecord user,
            String question,
            BindingWork item,
            Duration timeout,
            CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        BindingRecord binding = item.binding();
        LogicalKnowledgeBaseRecord kb = item.kb();
        // Governance controls are authoritative at the adapter dispatch boundary as well as
        // during the earlier authorization read. A disable/kill-switch/config mutation that
        // lands while authorization is in flight must not start a new provider retrieval.
        BindingRecord authoritativeBinding = authoritativeBinding(binding);
        LogicalKnowledgeBaseRecord authoritativeKb =
                knowledgeBases.findById(kb.logicalKbId()).orElse(null);
        if (authoritativeBinding == null
                || authoritativeKb == null
                || authoritativeKb.configVersion() != kb.configVersion()
                || !RetrievalDispatchGuard.sameBindings(
                        List.of(binding), List.of(authoritativeBinding))
                || !access.chatEligible(user, authoritativeKb)
                || !runtimeEligible(authoritativeKb, authoritativeBinding)) {
            return new BindingOutcome(
                    kb.logicalKbId(), binding, Retriever.Result.failed(), false);
        }
        if (!"chat_ready".equals(kb.capability()) || !kb.modelEligible()) {
            return new BindingOutcome(
                    kb.logicalKbId(), binding, Retriever.Result.failed(), false);
        }
        Retriever retriever = retrievers.find(binding.providerProfile()).orElse(null);
        if (retriever == null) {
            return new BindingOutcome(
                    kb.logicalKbId(), binding, Retriever.Result.failed(), false);
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
                                timeout,
                                cancellation));
        return new BindingOutcome(kb.logicalKbId(), binding, result, false);
    }

    private BindingAuthorization awaitAuthorization(PendingAuthorization pending) {
        try {
            BindingAuthorization authorization = providerExecution.await(pending.call());
            pending.call()
                    .reclassify(
                            telemetryOutcome(authorization.result().outcome()),
                            authorization.result().retryAfter());
            return authorization;
        } catch (TimeoutException e) {
            return new BindingAuthorization(
                    pending.item(), Retriever.AuthorizationResult.timeout(), false);
        } catch (ExecutionException e) {
            Throwable cause = unwrap(e.getCause());
            if (cause instanceof ProviderExecution.ProviderUnavailableException unavailable) {
                return new BindingAuthorization(
                        pending.item(), classifyUnavailableAuthorization(unavailable), true);
            }
            return new BindingAuthorization(
                    pending.item(), classifyAuthorizationFailure(cause), false);
        } catch (InterruptedException e) {
            return new BindingAuthorization(
                    pending.item(), Retriever.AuthorizationResult.unknown(), false);
        }
    }

    private BindingOutcome awaitRetrieval(PendingRetrieval pending) {
        try {
            BindingOutcome outcome = providerExecution.await(pending.call());
            pending.call()
                    .reclassify(
                            telemetryOutcome(outcome.result().outcome()),
                            outcome.result().retryAfter());
            return outcome;
        } catch (TimeoutException e) {
            return new BindingOutcome(
                    pending.item().kb().logicalKbId(),
                    pending.item().binding(),
                    Retriever.Result.timeout(),
                    false);
        } catch (ExecutionException e) {
            Throwable cause = unwrap(e.getCause());
            if (cause instanceof ProviderExecution.ProviderUnavailableException unavailable) {
                return new BindingOutcome(
                        pending.item().kb().logicalKbId(),
                        pending.item().binding(),
                        classifyUnavailable(unavailable),
                        true);
            }
            return new BindingOutcome(
                    pending.item().kb().logicalKbId(),
                    pending.item().binding(),
                    classifyFailure(cause),
                    false);
        } catch (InterruptedException e) {
            return new BindingOutcome(
                    pending.item().kb().logicalKbId(),
                    pending.item().binding(),
                    Retriever.Result.unknown(),
                    false);
        }
    }

    private Retriever.AuthorizationResult classifyAuthorizationFailure(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof TimeoutException) {
            return Retriever.AuthorizationResult.timeout();
        }
        if (cause instanceof RetrieverException retrieverException) {
            return switch (retrieverException.outcome()) {
                case SECURITY -> Retriever.AuthorizationResult.security();
                case FAILED -> Retriever.AuthorizationResult.failed();
                default -> Retriever.AuthorizationResult.unknown();
            };
        }
        return Retriever.AuthorizationResult.unknown();
    }

    private ConnectorTelemetry.Outcome telemetryOutcome(Retriever.AuthorizationOutcome outcome) {
        return switch (outcome) {
            case AUTHORIZED -> ConnectorTelemetry.Outcome.SUCCESS;
            case QUOTA -> ConnectorTelemetry.Outcome.QUOTA;
            case TIMEOUT -> ConnectorTelemetry.Outcome.TIMEOUT;
            case ACCESS_DENIED, FAILED, SECURITY, UNKNOWN -> ConnectorTelemetry.Outcome.FAILURE;
        };
    }

    private ConnectorTelemetry.Outcome telemetryOutcome(Retriever.Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> ConnectorTelemetry.Outcome.SUCCESS;
            case QUOTA -> ConnectorTelemetry.Outcome.QUOTA;
            case TIMEOUT -> ConnectorTelemetry.Outcome.TIMEOUT;
            case FAILED, SECURITY, UNKNOWN -> ConnectorTelemetry.Outcome.FAILURE;
        };
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

    private Retriever.AuthorizationResult classifyUnavailableAuthorization(
            ProviderExecution.ProviderUnavailableException unavailable) {
        return switch (unavailable.cause()) {
            case QUOTA -> Retriever.AuthorizationResult.quota(unavailable.retryAfter());
            case TIMEOUT -> Retriever.AuthorizationResult.timeout(unavailable.retryAfter());
            case RETRIEVAL -> Retriever.AuthorizationResult.failed(unavailable.retryAfter());
            case UNKNOWN -> Retriever.AuthorizationResult.unknown(unavailable.retryAfter());
        };
    }

    private Retriever.Result classifyUnavailable(
            ProviderExecution.ProviderUnavailableException unavailable) {
        return switch (unavailable.cause()) {
            case QUOTA -> Retriever.Result.quota(unavailable.retryAfter());
            case TIMEOUT -> Retriever.Result.timeout(unavailable.retryAfter());
            case RETRIEVAL -> Retriever.Result.failed(unavailable.retryAfter());
            case UNKNOWN -> Retriever.Result.unknown(unavailable.retryAfter());
        };
    }

    private void captureRetryAfter(
            Map<String, String> retryAfter, String bindingId, Duration duration) {
        if (duration != null) {
            retryAfter.put(bindingId, duration.toString());
        }
    }

    private void recordFailure(
            String providerProfile,
            ProviderExecution.UnavailabilityCause cause,
            Duration retryAfter,
            boolean providerRejected) {
        if (!providerRejected) {
            providerExecution.recordFailure(providerProfile, cause, retryAfter);
        }
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean runtimeEligible(LogicalKnowledgeBaseRecord kb, BindingRecord binding) {
        return RetrievalEligibility.isEligible(kb, binding, properties);
    }

    private BindingRecord authoritativeBinding(BindingRecord snapshot) {
        java.util.Optional<BindingRecord> byId = bindings.findById(snapshot.bindingId());
        if (byId != null && byId.isPresent()) {
            return byId.get();
        }
        return bindings.findByLogicalKbId(snapshot.logicalKbId()).stream()
                .filter(binding -> snapshot.bindingId().equals(binding.bindingId()))
                .findFirst()
                .orElse(null);
    }

    private void markWholeKbFailed(
            List<BindingRecord> kbBindings, Set<String> failed, Set<String> timedOut) {
        kbBindings.stream()
                .map(BindingRecord::bindingId)
                .filter(bindingId -> !timedOut.contains(bindingId))
                .forEach(failed::add);
    }

    private String firstBindingId(List<BindingRecord> bindings) {
        return bindings.isEmpty() ? null : bindings.getFirst().bindingId();
    }

    private void recordBlock(
            EnumMap<RetrievalTurn.Block, BlockCandidate> candidates,
            RetrievalTurn.Block block,
            String logicalKbId,
            String bindingId) {
        candidates.putIfAbsent(block, new BlockCandidate(logicalKbId, bindingId));
    }

    private BlockSelection selectBlock(
            EnumMap<RetrievalTurn.Block, BlockCandidate> candidates, boolean anyEvidence) {
        List<RetrievalTurn.Block> precedence =
                List.of(
                        RetrievalTurn.Block.SECURITY,
                        RetrievalTurn.Block.UNKNOWN,
                        RetrievalTurn.Block.BINDING_ACCESS,
                        RetrievalTurn.Block.BINDING_UNAVAILABLE);
        for (RetrievalTurn.Block block : precedence) {
            BlockCandidate candidate = candidates.get(block);
            if (candidate != null) {
                return new BlockSelection(block, candidate.logicalKbId(), candidate.bindingId());
            }
        }
        BlockCandidate quota = candidates.get(RetrievalTurn.Block.QUOTA);
        if (!anyEvidence && quota != null) {
            return new BlockSelection(
                    RetrievalTurn.Block.QUOTA, quota.logicalKbId(), quota.bindingId());
        }
        if (!anyEvidence) {
            return new BlockSelection(RetrievalTurn.Block.NO_EVIDENCE, null, null);
        }
        return new BlockSelection(RetrievalTurn.Block.NONE, null, null);
    }

    private void audit(
            String userId,
            String logicalKbId,
            String bindingId,
            String connector,
            String action,
            String authorization,
            String status,
            long latencyMs,
            String errorCategory) {
        try {
            auditEvents.insert(
                    new AuditEventRecord(
                            "aud_" + SessionService.randomToken().substring(0, 16),
                            clock.instant(),
                            userId,
                            logicalKbId,
                            bindingId,
                            connector,
                            action,
                            authorization,
                            null,
                            null,
                            latencyMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latencyMs,
                            status,
                            errorCategory,
                            objectMapper.writeValueAsString(Map.of("action", action))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize content-free audit details", e);
        }
    }

    private record BindingWork(LogicalKnowledgeBaseRecord kb, BindingRecord binding) {}

    private record BlockCandidate(String logicalKbId, String bindingId) {}

    private record BlockSelection(
            RetrievalTurn.Block block, String logicalKbId, String bindingId) {}

    private record BindingAuthorization(
            BindingWork item,
            Retriever.AuthorizationResult result,
            boolean providerRejected) {}

    private record PendingAuthorization(
            BindingWork item, ProviderExecution.TimedCall<BindingAuthorization> call) {}

    private record PendingRetrieval(
            BindingWork item, ProviderExecution.TimedCall<BindingOutcome> call) {}

    private record BindingOutcome(
            String logicalKbId,
            BindingRecord binding,
            Retriever.Result result,
            boolean providerRejected) {}
}
