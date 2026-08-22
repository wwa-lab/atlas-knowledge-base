package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.CancellationSource;
import com.atlas.knowledgebase.adapters.CancellationToken;
import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.adapters.RetrieverException;
import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final KbAccessService access;
    private final RetrieverRegistry retrievers;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RetrievalProperties properties;
    private final ProviderExecution providerExecution;

    public RetrievalOrchestrator(
            LogicalKnowledgeBaseRepository knowledgeBases,
            KbAccessService access,
            RetrieverRegistry retrievers,
            AuditEventRepository auditEvents,
            ObjectMapper objectMapper,
            Clock clock,
            RetrievalProperties properties,
            ProviderExecution providerExecution) {
        this.knowledgeBases = knowledgeBases;
        this.access = access;
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
        Map<String, String> retryAfter = new LinkedHashMap<>();
        List<ReciprocalRankFusion.RankedList> ranked = new ArrayList<>();
        Set<String> accessDeniedKbs = new LinkedHashSet<>();
        Set<String> unavailableKbs = new LinkedHashSet<>();
        Set<String> quotaKbs = new LinkedHashSet<>();
        Set<String> securityKbs = new LinkedHashSet<>();
        Set<String> unknownKbs = new LinkedHashSet<>();
        Map<String, List<BindingRecord>> currentBindings = new LinkedHashMap<>();
        String blockKb = null;
        String blockBinding = null;

        List<BindingWork> authorizationWork = new ArrayList<>();
        for (RetrievalScope.KnowledgeBaseSnapshot snapshot : scope.knowledgeBases()) {
            cancellation.throwIfCancelled();
            LogicalKnowledgeBaseRecord kb = snapshot.knowledgeBase();
            String logicalKbId = kb.logicalKbId();
            if (!access.authorized(user, kb) || !access.chatEligible(user, kb)) {
                continue;
            }
            List<BindingRecord> current = snapshot.bindings();
            currentBindings.put(logicalKbId, current);
            boolean runtimeUnavailable = false;
            for (BindingRecord binding : current) {
                if (!runtimeEligible(kb, binding)) {
                    runtimeUnavailable = true;
                    blockKb = logicalKbId;
                    blockBinding = binding.bindingId();
                    failed.add(binding.bindingId());
                }
            }
            if (runtimeUnavailable) {
                unavailableKbs.add(logicalKbId);
                markWholeKbFailed(current, failed, timedOut);
                continue;
            }
            for (BindingRecord binding : current) {
                authorizationWork.add(new BindingWork(kb, binding));
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
                    retrievalWork.add(item);
                }
                case ACCESS_DENIED -> {
                    failed.add(bindingId);
                    accessDeniedKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                }
                case QUOTA -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.QUOTA,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    quotaLimited.add(bindingId);
                    quotaKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                }
                case TIMEOUT -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.TIMEOUT,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    timedOut.add(bindingId);
                    unavailableKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                }
                case FAILED -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.RETRIEVAL,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    failed.add(bindingId);
                    unavailableKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                }
                case SECURITY -> {
                    failed.add(bindingId);
                    securityKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                    knowledgeBases.suspend(logicalKbId);
                    audit(user.userId(), logicalKbId, bindingId, "authorize", "deny", "fail_closed");
                }
                case UNKNOWN -> {
                    recordFailure(
                            item.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.UNKNOWN,
                            authorization.result().retryAfter(),
                            authorization.providerRejected());
                    failed.add(bindingId);
                    unknownKbs.add(logicalKbId);
                    if (blockKb == null) {
                        blockKb = logicalKbId;
                        blockBinding = bindingId;
                    }
                    audit(user.userId(), logicalKbId, bindingId, "authorize", "deny", "unknown");
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
                }
                case TIMEOUT -> {
                    recordFailure(
                            outcome.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.TIMEOUT,
                            outcome.result().retryAfter(),
                            outcome.providerRejected());
                    timedOut.add(bindingId);
                }
                case FAILED -> {
                    recordFailure(
                            outcome.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.RETRIEVAL,
                            outcome.result().retryAfter(),
                            outcome.providerRejected());
                    failed.add(bindingId);
                }
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
                    recordFailure(
                            outcome.binding().providerProfile(),
                            ProviderExecution.UnavailabilityCause.UNKNOWN,
                            outcome.result().retryAfter(),
                            outcome.providerRejected());
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
                    providerExecution.recordSuccess(outcome.binding().providerProfile());
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

        RetrievalTurn.Block block = RetrievalTurn.Block.NONE;
        boolean anyEvidence = !fused.isEmpty();
        if (!securityKbs.isEmpty()) {
            block = RetrievalTurn.Block.SECURITY;
        } else if (!unknownKbs.isEmpty()) {
            block = RetrievalTurn.Block.UNKNOWN;
        } else if (!accessDeniedKbs.isEmpty()) {
            block = RetrievalTurn.Block.BINDING_ACCESS;
        } else if (!unavailableKbs.isEmpty()) {
            block = RetrievalTurn.Block.BINDING_UNAVAILABLE;
        } else if (!quotaKbs.isEmpty() && !anyEvidence) {
            block = RetrievalTurn.Block.QUOTA;
        } else if (!anyEvidence) {
            block = RetrievalTurn.Block.NO_EVIDENCE;
        }

        return new RetrievalTurn(
                coverage, fused, List.of(), null, scope, block, blockKb, blockBinding);
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
            return providerExecution.await(pending.call());
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
            return providerExecution.await(pending.call());
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
        return binding.enabled()
                && !binding.killSwitch()
                && binding.featureFlag()
                && properties.enabled(binding.providerProfile())
                && binding.health() != null
                && !"unavailable".equals(binding.health())
                && !kb.freshnessRequired();
    }

    private void markWholeKbFailed(
            List<BindingRecord> kbBindings, Set<String> failed, Set<String> timedOut) {
        kbBindings.stream()
                .map(BindingRecord::bindingId)
                .filter(bindingId -> !timedOut.contains(bindingId))
                .forEach(failed::add);
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
