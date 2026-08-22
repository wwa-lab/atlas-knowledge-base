package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RetrievalOrchestratorRetryAfterTest {

    private static final Duration ADVERTISED_RETRY_AFTER = Duration.ofSeconds(2);
    private static final Instant NOW = Instant.parse("2026-08-22T05:00:00Z");

    private ControlledRetriever retriever;
    private ProviderExecution providerExecution;
    private RetrievalOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        RetrievalProperties properties = properties();
        retriever = new ControlledRetriever();
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever));
        providerExecution = new ProviderExecution(properties, registry);
        LogicalKnowledgeBaseRepository knowledgeBases =
                mock(LogicalKnowledgeBaseRepository.class);
        KbAccessService access = mock(KbAccessService.class);
        when(access.authorized(any(), any())).thenReturn(true);
        when(access.chatEligible(any(), any())).thenReturn(true);
        orchestrator =
                new RetrievalOrchestrator(
                        knowledgeBases,
                        access,
                        registry,
                        mock(AuditEventRepository.class),
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        properties,
                        providerExecution);
    }

    @AfterEach
    void closeExecution() {
        providerExecution.close();
    }

    @ParameterizedTest
    @MethodSource("authorizationFailures")
    void authorizationRetryAfterControlsBackoffAndPreservesProviderIsolation(
            ProviderExecution.UnavailabilityCause cause,
            Retriever.AuthorizationResult failure) throws Exception {
        retriever.authorizations.put("dify", failure);
        RetrievalScope scope =
                scope(
                        snapshot("lkb_auth_retry", "bnd_auth_retry", "dify"),
                        snapshot("lkb_auth_safe", "bnd_auth_safe", "confluence"));

        RetrievalTurn first = orchestrator.retrieve(user(), "question", scope);

        assertOutcome(first, "bnd_auth_retry", cause);
        assertThat(first.fused())
                .extracting(hit -> hit.hit().documentId())
                .contains("bnd_auth_safe:primary");
        retriever.authorizations.put("dify", Retriever.AuthorizationResult.authorized());
        Thread.sleep(50);

        RetrievalTurn stillBackedOff =
                orchestrator.retrieve(
                        user(),
                        "question",
                        scope(snapshot("lkb_auth_retry", "bnd_auth_retry", "dify")));

        assertOutcome(stillBackedOff, "bnd_auth_retry", cause);
        assertRemainingRetryAfter(stillBackedOff, "bnd_auth_retry");
    }

    @ParameterizedTest
    @MethodSource("retrievalFailures")
    void retrievalRetryAfterControlsBackoffAndPreservesProviderIsolation(
            ProviderExecution.UnavailabilityCause cause,
            Retriever.Result failure) throws Exception {
        retriever.results.put("dify", failure);
        RetrievalScope scope =
                scope(
                        snapshot("lkb_retrieve_retry", "bnd_retrieve_retry", "dify"),
                        snapshot("lkb_retrieve_safe", "bnd_retrieve_safe", "confluence"));

        RetrievalTurn first = orchestrator.retrieve(user(), "question", scope);

        assertOutcome(first, "bnd_retrieve_retry", cause);
        assertThat(first.fused())
                .extracting(hit -> hit.hit().documentId())
                .contains("bnd_retrieve_safe:primary");
        retriever.results.put("dify", success("bnd_retrieve_retry", "dify"));
        Thread.sleep(50);

        RetrievalTurn stillBackedOff =
                orchestrator.retrieve(
                        user(),
                        "question",
                        scope(
                                snapshot(
                                        "lkb_retrieve_retry",
                                        "bnd_retrieve_retry",
                                        "dify")));

        assertOutcome(stillBackedOff, "bnd_retrieve_retry", cause);
        assertRemainingRetryAfter(stillBackedOff, "bnd_retrieve_retry");
    }

    @Test
    void authorizationQuotaExcludesEveryBindingOfTheAffectedKnowledgeBase() {
        retriever.authorizations.put(
                "dify", Retriever.AuthorizationResult.quota(ADVERTISED_RETRY_AFTER));
        LogicalKnowledgeBaseRecord knowledgeBase = knowledgeBase("lkb_auth_quota_complete");
        RetrievalScope scope =
                new RetrievalScope(
                        List.of(
                                new RetrievalScope.KnowledgeBaseSnapshot(
                                        knowledgeBase,
                                        List.of(
                                                binding(
                                                        "bnd_auth_quota",
                                                        knowledgeBase.logicalKbId(),
                                                        "dify"),
                                                binding(
                                                        "bnd_auth_other",
                                                        knowledgeBase.logicalKbId(),
                                                        "confluence")))));

        RetrievalTurn turn = orchestrator.retrieve(user(), "question", scope);

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.QUOTA);
        assertThat(turn.fused()).isEmpty();
        assertThat(strings(turn, "successful")).isEmpty();
        assertThat(strings(turn, "quota_limited")).containsExactly("bnd_auth_quota");
        assertThat(strings(turn, "failed")).contains("bnd_auth_other");
    }

    static Stream<Arguments> authorizationFailures() {
        return Stream.of(
                Arguments.of(
                        ProviderExecution.UnavailabilityCause.TIMEOUT,
                        Retriever.AuthorizationResult.timeout(ADVERTISED_RETRY_AFTER)),
                Arguments.of(
                        ProviderExecution.UnavailabilityCause.RETRIEVAL,
                        Retriever.AuthorizationResult.failed(ADVERTISED_RETRY_AFTER)),
                Arguments.of(
                        ProviderExecution.UnavailabilityCause.UNKNOWN,
                        Retriever.AuthorizationResult.unknown(ADVERTISED_RETRY_AFTER)));
    }

    static Stream<Arguments> retrievalFailures() {
        return Stream.of(
                Arguments.of(
                        ProviderExecution.UnavailabilityCause.TIMEOUT,
                        Retriever.Result.timeout(ADVERTISED_RETRY_AFTER)),
                Arguments.of(
                        ProviderExecution.UnavailabilityCause.RETRIEVAL,
                        Retriever.Result.failed(ADVERTISED_RETRY_AFTER)),
                Arguments.of(
                        ProviderExecution.UnavailabilityCause.UNKNOWN,
                        Retriever.Result.unknown(ADVERTISED_RETRY_AFTER)));
    }

    private void assertOutcome(
            RetrievalTurn turn,
            String bindingId,
            ProviderExecution.UnavailabilityCause cause) {
        String coverageKey =
                cause == ProviderExecution.UnavailabilityCause.TIMEOUT
                        ? "timed_out"
                        : "failed";
        assertThat(strings(turn, coverageKey)).contains(bindingId);
        assertThat(strings(turn, "quota_limited")).doesNotContain(bindingId);
    }

    private void assertRemainingRetryAfter(RetrievalTurn turn, String bindingId) {
        @SuppressWarnings("unchecked")
        Map<String, String> retryAfter =
                (Map<String, String>) turn.coverage().get("retry_after");
        assertThat(retryAfter).containsKey(bindingId);
        assertThat(Duration.parse(retryAfter.get(bindingId)))
                .isGreaterThan(Duration.ofSeconds(1));
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(RetrievalTurn turn, String key) {
        return (List<String>) turn.coverage().get(key);
    }

    private RetrievalScope scope(RetrievalScope.KnowledgeBaseSnapshot... snapshots) {
        return new RetrievalScope(List.of(snapshots));
    }

    private RetrievalScope.KnowledgeBaseSnapshot snapshot(
            String logicalKbId, String bindingId, String provider) {
        return new RetrievalScope.KnowledgeBaseSnapshot(
                knowledgeBase(logicalKbId),
                List.of(binding(bindingId, logicalKbId, provider)));
    }

    private LogicalKnowledgeBaseRecord knowledgeBase(String logicalKbId) {
        return new LogicalKnowledgeBaseRecord(
                logicalKbId,
                "Chat KB",
                "desc",
                "usr_retry_after",
                "private",
                "support",
                "internal",
                true,
                "chat_ready",
                "active",
                "healthy",
                1,
                null,
                false,
                null,
                NOW,
                NOW,
                NOW);
    }

    private BindingRecord binding(String bindingId, String logicalKbId, String provider) {
        return new BindingRecord(
                bindingId,
                logicalKbId,
                provider,
                "{}",
                "canonical",
                "delegated_user",
                "healthy",
                true,
                false,
                true,
                null,
                "{}",
                "owner@example.com",
                null,
                1,
                NOW,
                NOW);
    }

    private AtlasUserRecord user() {
        return new AtlasUserRecord(
                "usr_retry_after",
                "sso-retry-after",
                "Owner",
                null,
                "[\"end_user\",\"kb_owner\"]",
                true,
                NOW,
                NOW);
    }

    private static RetrievalProperties properties() {
        RetrievalProperties properties = new RetrievalProperties();
        Set<String> providers = Set.of("dify", "confluence");
        properties.setProviderTimeouts(values(providers, Duration.ofSeconds(2)));
        properties.setProviderConcurrency(values(providers, 4));
        properties.setProviderQuotaLimits(values(providers, 100));
        properties.setProviderQuotaWindows(values(providers, Duration.ofMinutes(1)));
        properties.setProviderEnabled(values(providers, true));
        properties.setProviderBackoffs(values(providers, Duration.ofMillis(5)));
        properties.setProviderCircuitFailureThresholds(values(providers, 10));
        properties.setProviderCircuitOpenDurations(values(providers, Duration.ofSeconds(1)));
        return properties;
    }

    private static <T> Map<String, T> values(Set<String> providers, T value) {
        return providers.stream()
                .collect(java.util.stream.Collectors.toMap(provider -> provider, provider -> value));
    }

    private static Retriever.Result success(String bindingId, String provider) {
        return Retriever.Result.success(
                List.of(
                        new Retriever.Hit(
                                provider + ":" + bindingId,
                                "https://example.test/" + bindingId,
                                bindingId + ":primary",
                                "Fixture",
                                "Fixture evidence",
                                "v1",
                                "{}",
                                1,
                                provider + ":" + bindingId + ":v1")),
                List.of());
    }

    private static final class ControlledRetriever implements Retriever {
        private final Map<String, AuthorizationResult> authorizations =
                new ConcurrentHashMap<>();
        private final Map<String, Result> results = new ConcurrentHashMap<>();

        @Override
        public Set<String> providerProfiles() {
            return Set.of("dify", "confluence");
        }

        @Override
        public AuthorizationResult authorize(AuthorizationRequest request) {
            return authorizations.getOrDefault(
                    request.providerProfile(), AuthorizationResult.authorized());
        }

        @Override
        public Result retrieve(Request request) {
            return results.getOrDefault(
                    request.providerProfile(),
                    success(request.bindingId(), request.providerProfile()));
        }
    }
}
