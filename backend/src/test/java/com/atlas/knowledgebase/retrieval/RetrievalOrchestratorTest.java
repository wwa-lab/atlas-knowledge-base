package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@ActiveProfiles("local")
class RetrievalOrchestratorTest {

    @Autowired private RetrievalOrchestrator retrieval;
    @Autowired private AtlasUserRepository users;
    @Autowired private LogicalKnowledgeBaseRepository knowledgeBases;
    @Autowired private BindingRepository bindings;
    @Autowired private RetrievalProperties properties;
    @Autowired private ProviderExecution providerExecution;

    @Test
    void timeoutIsPartialCoverageAndDoesNotFailClosed() {
        Instant now = Instant.parse("2026-08-22T03:00:00Z");
        AtlasUserRecord user = owner("usr_ret_timeout", now);
        LogicalKnowledgeBaseRecord ok = chatReadyKb("lkb_ret_ok", user.userId(), now);
        LogicalKnowledgeBaseRecord slow = chatReadyKb("lkb_ret_slow", user.userId(), now);
        BindingRecord okBinding = binding("bnd_ret_ok", ok.logicalKbId(), "dify", "{}", now);
        BindingRecord slowBinding =
                binding(
                        "bnd_ret_slow",
                        slow.logicalKbId(),
                        "dify",
                        "{\"retrieval_fixture\":\"timeout\"}",
                        now);

        RetrievalTurn turn =
                retrieve(
                        user, "How do we rotate the gateway cert?", List.of(ok.logicalKbId(), slow.logicalKbId()));

        assertThat(turn.blocked()).isFalse();
        @SuppressWarnings("unchecked")
        List<String> successful = (List<String>) turn.coverage().get("successful");
        @SuppressWarnings("unchecked")
        List<String> timedOut = (List<String>) turn.coverage().get("timed_out");
        assertThat(successful).contains(okBinding.bindingId()).doesNotContain(slowBinding.bindingId());
        assertThat(timedOut).contains(slowBinding.bindingId());
        assertThat(turn.fused()).isNotEmpty();
        assertThat(turn.fused().getFirst().hit().excerpt()).contains("fixture");
    }

    @Test
    void allOrdinaryFailuresBlockGenerationWithoutGroundedEvidence() {
        Instant now = Instant.parse("2026-08-22T03:00:30Z");
        AtlasUserRecord user = owner("usr_ret_all_timeout", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_all_timeout", user.userId(), now);
        BindingRecord binding =
                binding(
                        "bnd_ret_all_timeout",
                        kb.logicalKbId(),
                        "dify",
                        "{\"retrieval_fixture\":\"timeout\"}",
                        now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.NO_EVIDENCE);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> timedOut = (List<String>) turn.coverage().get("timed_out");
        assertThat(timedOut).containsExactly(binding.bindingId());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quotaOutcomeCarriesRetryAfterAndBlocksOnlyWhenNoSafeEvidenceExists() {
        Instant now = Instant.parse("2026-08-22T03:00:45Z");
        AtlasUserRecord user = owner("usr_ret_quota", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_quota", user.userId(), now);
        BindingRecord binding =
                binding(
                        "bnd_ret_quota",
                        kb.logicalKbId(),
                        "dify",
                        "{\"retrieval_fixture\":\"quota\"}",
                        now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.QUOTA);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> quotaLimited = (List<String>) turn.coverage().get("quota_limited");
        assertThat(quotaLimited).containsExactly(binding.bindingId());
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> retryAfter =
                (java.util.Map<String, String>) turn.coverage().get("retry_after");
        assertThat(retryAfter).containsEntry(binding.bindingId(), "PT1S");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void quotaOnOneConnectorAllowsUnrelatedSafeEvidenceToContinue() {
        Instant now = Instant.parse("2026-08-22T03:00:50Z");
        AtlasUserRecord user = owner("usr_ret_partial_quota", now);
        LogicalKnowledgeBaseRecord safe =
                chatReadyKb("lkb_ret_partial_quota_safe", user.userId(), now);
        LogicalKnowledgeBaseRecord limited =
                chatReadyKb("lkb_ret_partial_quota_limited", user.userId(), now);
        BindingRecord safeBinding =
                binding("bnd_ret_partial_quota_safe", safe.logicalKbId(), "dify", "{}", now);
        BindingRecord limitedBinding =
                binding(
                        "bnd_ret_partial_quota_limited",
                        limited.logicalKbId(),
                        "confluence",
                        "{\"retrieval_fixture\":\"quota\"}",
                        now);

        RetrievalTurn turn =
                retrieve(
                        user,
                        "question",
                        List.of(safe.logicalKbId(), limited.logicalKbId()));

        assertThat(turn.blocked()).isFalse();
        assertThat(turn.fused()).isNotEmpty();
        @SuppressWarnings("unchecked")
        List<String> successful = (List<String>) turn.coverage().get("successful");
        assertThat(successful).contains(safeBinding.bindingId());
        @SuppressWarnings("unchecked")
        List<String> quotaLimited = (List<String>) turn.coverage().get("quota_limited");
        assertThat(quotaLimited).containsExactly(limitedBinding.bindingId());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void providerNeutralQuotaBudgetProducesQuotaCoverage() {
        Instant now = Instant.parse("2026-08-22T03:00:55Z");
        AtlasUserRecord user = owner("usr_ret_budget_quota", now);
        LogicalKnowledgeBaseRecord kb =
                chatReadyKb("lkb_ret_budget_quota", user.userId(), now);
        BindingRecord binding =
                binding(
                        "bnd_ret_budget_quota",
                        kb.logicalKbId(),
                        "confluence",
                        "{}",
                        now);
        properties.setProviderQuotaLimits(Map.of("confluence", 1));

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.QUOTA);
        @SuppressWarnings("unchecked")
        List<String> quotaLimited = (List<String>) turn.coverage().get("quota_limited");
        assertThat(quotaLimited).containsExactly(binding.bindingId());
        @SuppressWarnings("unchecked")
        List<String> timedOut = (List<String>) turn.coverage().get("timed_out");
        assertThat(timedOut).isEmpty();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void timeoutBackoffStaysTimedOutAndRejectedCallsDoNotProlongIt() throws Exception {
        Instant now = Instant.parse("2026-08-22T03:00:57Z");
        AtlasUserRecord user = owner("usr_ret_timeout_backoff", now);
        LogicalKnowledgeBaseRecord kb =
                chatReadyKb("lkb_ret_timeout_backoff", user.userId(), now);
        BindingRecord binding =
                binding(
                        "bnd_ret_timeout_backoff", kb.logicalKbId(), "dify", "{}", now);
        properties.setProviderBackoffs(Map.of("dify", Duration.ofMillis(200)));
        providerExecution.recordFailure(
                "dify", ProviderExecution.UnavailabilityCause.TIMEOUT, null);

        assertTimeoutCoverage(retrieve(user, "first", List.of(kb.logicalKbId())), binding);
        Thread.sleep(100);
        assertTimeoutCoverage(retrieve(user, "second", List.of(kb.logicalKbId())), binding);
        Thread.sleep(140);

        RetrievalTurn recovered = retrieve(user, "third", List.of(kb.logicalKbId()));
        @SuppressWarnings("unchecked")
        List<String> successful = (List<String>) recovered.coverage().get("successful");
        assertThat(successful).containsExactly(binding.bindingId());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void retrievalBackoffStaysFailedInsteadOfBecomingQuota() {
        Instant now = Instant.parse("2026-08-22T03:00:59Z");
        AtlasUserRecord user = owner("usr_ret_failure_backoff", now);
        LogicalKnowledgeBaseRecord kb =
                chatReadyKb("lkb_ret_failure_backoff", user.userId(), now);
        BindingRecord binding =
                binding(
                        "bnd_ret_failure_backoff", kb.logicalKbId(), "git_markdown", "{}", now);
        properties.setProviderBackoffs(Map.of("git_markdown", Duration.ofSeconds(1)));
        providerExecution.recordFailure(
                "git_markdown", ProviderExecution.UnavailabilityCause.RETRIEVAL, null);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed).containsExactly(binding.bindingId());
        @SuppressWarnings("unchecked")
        List<String> quotaLimited = (List<String>) turn.coverage().get("quota_limited");
        assertThat(quotaLimited).isEmpty();
        assertPositiveRetryAfter(turn, binding.bindingId());
    }

    @Test
    void missingBindingAccessExcludesTheWholeKnowledgeBase() {
        Instant now = Instant.parse("2026-08-22T03:01:00Z");
        AtlasUserRecord user = owner("usr_ret_fr47", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_fr47", user.userId(), now);
        binding("bnd_ret_fr47_ok", kb.logicalKbId(), "dify", "{}", now);
        binding(
                "bnd_ret_fr47_denied",
                kb.logicalKbId(),
                "git_markdown",
                "{\"repo\":\"org/runbooks\",\"retrieval_fixture\":\"binding_denied\"}",
                now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_ACCESS);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed).contains("bnd_ret_fr47_denied");
    }

    @Test
    void featureFlagOffStopsDispatchAndRemainsVisibleInCoverage() {
        Instant now = Instant.parse("2026-08-22T03:01:15Z");
        AtlasUserRecord user = owner("usr_ret_flag_off", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_flag_off", user.userId(), now);
        binding("bnd_ret_flag_on", kb.logicalKbId(), "dify", "{}", now);
        binding(
                "bnd_ret_flag_off",
                kb.logicalKbId(),
                "git_markdown",
                "{}",
                "healthy",
                true,
                false,
                false,
                now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_UNAVAILABLE);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed).contains("bnd_ret_flag_off");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void providerProfileFlagStopsOnlyThatProfileAndBlocksIncompleteKb() {
        Instant now = Instant.parse("2026-08-22T03:01:20Z");
        AtlasUserRecord user = owner("usr_ret_profile_off", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_profile_off", user.userId(), now);
        binding("bnd_ret_profile_dify", kb.logicalKbId(), "dify", "{}", now);
        binding("bnd_ret_profile_git", kb.logicalKbId(), "git_markdown", "{}", now);
        properties.setProviderEnabled(
                java.util.Map.of("dify", true, "git_markdown", false, "confluence", true));

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_UNAVAILABLE);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed).contains("bnd_ret_profile_dify", "bnd_ret_profile_git");
    }

    @Test
    void disabledKilledAndUnavailableBindingsCannotDisappearFromCoverage() {
        Instant now = Instant.parse("2026-08-22T03:01:30Z");
        AtlasUserRecord user = owner("usr_ret_controls", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_controls", user.userId(), now);
        binding("bnd_ret_controls_ok", kb.logicalKbId(), "dify", "{}", now);
        binding(
                "bnd_ret_disabled",
                kb.logicalKbId(),
                "git_markdown",
                "{}",
                "healthy",
                false,
                false,
                true,
                now);
        binding(
                "bnd_ret_killed",
                kb.logicalKbId(),
                "confluence",
                "{}",
                "healthy",
                true,
                true,
                true,
                now);
        binding(
                "bnd_ret_unavailable",
                kb.logicalKbId(),
                "git_markdown",
                "{}",
                "unavailable",
                true,
                false,
                true,
                now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_UNAVAILABLE);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed)
                .contains("bnd_ret_disabled", "bnd_ret_killed", "bnd_ret_unavailable");
    }

    @Test
    void requiredFreshnessFailsClosedWhenCurrentEvidenceCannotBeProven() {
        Instant now = Instant.parse("2026-08-22T03:01:45Z");
        AtlasUserRecord user = owner("usr_ret_freshness", now);
        LogicalKnowledgeBaseRecord kb =
                knowledgeBases.insert(
                        new LogicalKnowledgeBaseRecord(
                                "lkb_ret_freshness",
                                "Fresh KB",
                                "desc",
                                user.userId(),
                                "private",
                                "support",
                                "internal",
                                true,
                                "chat_ready",
                                "active",
                                "healthy",
                                1,
                                "PT1H",
                                true,
                                null,
                                now,
                                now,
                                now));
        binding("bnd_ret_freshness", kb.logicalKbId(), "dify", "{}", now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_UNAVAILABLE);
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed).containsExactly("bnd_ret_freshness");
    }

    @Test
    void securityFailureSuspendsTheKnowledgeBase() {
        Instant now = Instant.parse("2026-08-22T03:02:00Z");
        AtlasUserRecord user = owner("usr_ret_sec", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_sec", user.userId(), now);
        binding(
                "bnd_ret_sec",
                kb.logicalKbId(),
                "dify",
                "{\"retrieval_fixture\":\"security\"}",
                now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.SECURITY);
        assertThat(knowledgeBases.findById(kb.logicalKbId()).orElseThrow().lifecycle())
                .isEqualTo("suspended");
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed).contains("bnd_ret_sec");
    }

    @Test
    void thrownSecurityFailureStillFailsClosedAndSuspends() {
        Instant now = Instant.parse("2026-08-22T03:02:30Z");
        AtlasUserRecord user = owner("usr_ret_thrown_sec", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_thrown_sec", user.userId(), now);
        binding(
                "bnd_ret_thrown_sec",
                kb.logicalKbId(),
                "dify",
                "{\"retrieval_fixture\":\"throw_security\"}",
                now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.SECURITY);
        assertThat(knowledgeBases.findById(kb.logicalKbId()).orElseThrow().lifecycle())
                .isEqualTo("suspended");
    }

    @Test
    void thrownConnectorFailureIsFailedCoverageAlongsideSafeEvidence() {
        Instant now = Instant.parse("2026-08-22T03:02:45Z");
        AtlasUserRecord user = owner("usr_ret_thrown_failed", now);
        LogicalKnowledgeBaseRecord ok = chatReadyKb("lkb_ret_thrown_ok", user.userId(), now);
        LogicalKnowledgeBaseRecord failed = chatReadyKb("lkb_ret_thrown_failed", user.userId(), now);
        binding("bnd_ret_thrown_ok", ok.logicalKbId(), "dify", "{}", now);
        binding(
                "bnd_ret_thrown_failed",
                failed.logicalKbId(),
                "dify",
                "{\"retrieval_fixture\":\"throw_failed\"}",
                now);

        RetrievalTurn turn =
                retrieve(user, "question", List.of(ok.logicalKbId(), failed.logicalKbId()));

        assertThat(turn.blocked()).isFalse();
        @SuppressWarnings("unchecked")
        List<String> failedBindings = (List<String>) turn.coverage().get("failed");
        assertThat(failedBindings).contains("bnd_ret_thrown_failed");
        @SuppressWarnings("unchecked")
        List<String> timedOut = (List<String>) turn.coverage().get("timed_out");
        assertThat(timedOut).doesNotContain("bnd_ret_thrown_failed");
    }

    @Test
    void unknownAdapterExceptionBlocksGenerationWithoutSuspending() {
        Instant now = Instant.parse("2026-08-22T03:02:55Z");
        AtlasUserRecord user = owner("usr_ret_thrown_unknown", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_thrown_unknown", user.userId(), now);
        binding(
                "bnd_ret_thrown_unknown",
                kb.logicalKbId(),
                "dify",
                "{\"retrieval_fixture\":\"throw_unknown\"}",
                now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.UNKNOWN);
        assertThat(knowledgeBases.findById(kb.logicalKbId()).orElseThrow().lifecycle())
                .isEqualTo("active");
    }

    @Test
    void itemOmitKeepsTheKnowledgeBaseInScope() {
        Instant now = Instant.parse("2026-08-22T03:03:00Z");
        AtlasUserRecord user = owner("usr_ret_omit", now);
        LogicalKnowledgeBaseRecord kb = chatReadyKb("lkb_ret_omit", user.userId(), now);
        BindingRecord binding =
                binding(
                        "bnd_ret_omit",
                        kb.logicalKbId(),
                        "confluence",
                        "{\"retrieval_fixture\":\"item_omit\"}",
                        now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.blocked()).isFalse();
        @SuppressWarnings("unchecked")
        List<String> successful = (List<String>) turn.coverage().get("successful");
        assertThat(successful).contains(binding.bindingId());
        assertThat(turn.fused()).hasSize(1);
        assertThat(turn.fused().getFirst().hit().documentId()).endsWith(":kept");
    }

    @Test
    void unauthorizedKnowledgeBaseRemainsAnActionableBlockAlongsideSafeEvidence() {
        Instant now = Instant.parse("2026-08-22T03:03:15Z");
        AtlasUserRecord user = owner("usr_ret_scope_access", now);
        AtlasUserRecord otherOwner = owner("usr_ret_scope_access_other", now);
        LogicalKnowledgeBaseRecord safe =
                chatReadyKb("lkb_ret_scope_access_safe", user.userId(), now);
        LogicalKnowledgeBaseRecord denied =
                chatReadyKb("lkb_ret_scope_access_denied", otherOwner.userId(), now);
        BindingRecord safeBinding =
                binding("bnd_ret_scope_access_safe", safe.logicalKbId(), "dify", "{}", now);
        BindingRecord deniedBinding =
                binding(
                        "bnd_ret_scope_access_denied",
                        denied.logicalKbId(),
                        "git_markdown",
                        "{}",
                        now);
        BindingRecord deniedSibling =
                binding(
                        "bnd_ret_scope_access_denied_sibling",
                        denied.logicalKbId(),
                        "confluence",
                        "{}",
                        now);

        RetrievalTurn turn =
                retrieve(user, "question", List.of(denied.logicalKbId(), safe.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_ACCESS);
        assertThat(turn.blockLogicalKbId()).isEqualTo(denied.logicalKbId());
        assertThat(turn.blockBindingId()).isEqualTo(deniedBinding.bindingId());
        assertThat(strings(turn, "failed"))
                .contains(deniedBinding.bindingId(), deniedSibling.bindingId());
        assertThat(strings(turn, "successful")).contains(safeBinding.bindingId());
        assertThat(turn.fused()).isNotEmpty();
    }

    @Test
    void chatIneligibleKnowledgeBaseRemainsAnActionableBlockAlongsideSafeEvidence() {
        Instant now = Instant.parse("2026-08-22T03:03:30Z");
        AtlasUserRecord user = owner("usr_ret_scope_eligibility", now);
        LogicalKnowledgeBaseRecord safe =
                chatReadyKb("lkb_ret_scope_eligibility_safe", user.userId(), now);
        LogicalKnowledgeBaseRecord browseOnly =
                knowledgeBases.insert(
                        new LogicalKnowledgeBaseRecord(
                                "lkb_ret_scope_eligibility_invalid",
                                "Browse-only KB",
                                "desc",
                                user.userId(),
                                "private",
                                "support",
                                "internal",
                                true,
                                "browse_only",
                                "active",
                                "healthy",
                                1,
                                null,
                                false,
                                null,
                                now,
                                now,
                                now));
        BindingRecord safeBinding =
                binding(
                        "bnd_ret_scope_eligibility_safe",
                        safe.logicalKbId(),
                        "dify",
                        "{}",
                        now);
        BindingRecord invalidBinding =
                binding(
                        "bnd_ret_scope_eligibility_invalid",
                        browseOnly.logicalKbId(),
                        "git_markdown",
                        "{}",
                        now);
        BindingRecord invalidSibling =
                binding(
                        "bnd_ret_scope_eligibility_invalid_sibling",
                        browseOnly.logicalKbId(),
                        "confluence",
                        "{}",
                        now);

        RetrievalTurn turn =
                retrieve(
                        user,
                        "question",
                        List.of(browseOnly.logicalKbId(), safe.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_UNAVAILABLE);
        assertThat(turn.blockLogicalKbId()).isEqualTo(browseOnly.logicalKbId());
        assertThat(turn.blockBindingId()).isEqualTo(invalidBinding.bindingId());
        assertThat(strings(turn, "failed"))
                .contains(invalidBinding.bindingId(), invalidSibling.bindingId());
        assertThat(strings(turn, "successful")).contains(safeBinding.bindingId());
        assertThat(turn.fused()).isNotEmpty();
    }

    @Test
    void lifecycleDriftIsRevalidatedWithoutMutatingTheProvenanceSnapshot() {
        Instant now = Instant.parse("2026-08-22T03:03:45Z");
        AtlasUserRecord user = owner("usr_ret_scope_drift", now);
        LogicalKnowledgeBaseRecord drifting =
                chatReadyKb("lkb_ret_scope_drift_invalid", user.userId(), now);
        LogicalKnowledgeBaseRecord safe =
                chatReadyKb("lkb_ret_scope_drift_safe", user.userId(), now);
        BindingRecord driftingBinding =
                binding(
                        "bnd_ret_scope_drift_invalid",
                        drifting.logicalKbId(),
                        "git_markdown",
                        "{}",
                        now);
        BindingRecord safeBinding =
                binding("bnd_ret_scope_drift_safe", safe.logicalKbId(), "dify", "{}", now);
        RetrievalScope scope =
                new RetrievalScope(
                        List.of(
                                new RetrievalScope.KnowledgeBaseSnapshot(
                                        drifting,
                                        bindings.findByLogicalKbId(drifting.logicalKbId())),
                                new RetrievalScope.KnowledgeBaseSnapshot(
                                        safe, bindings.findByLogicalKbId(safe.logicalKbId()))));
        knowledgeBases.suspend(drifting.logicalKbId());

        RetrievalTurn turn = retrieval.retrieve(user, "question", scope);

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_UNAVAILABLE);
        assertThat(turn.blockLogicalKbId()).isEqualTo(drifting.logicalKbId());
        assertThat(turn.blockBindingId()).isEqualTo(driftingBinding.bindingId());
        assertThat(strings(turn, "failed")).contains(driftingBinding.bindingId());
        assertThat(strings(turn, "successful")).contains(safeBinding.bindingId());
        assertThat(turn.fused()).isNotEmpty();
        assertThat(turn.scope()).isSameAs(scope);
        assertThat(turn.scope().knowledgeBases().getFirst().knowledgeBase().lifecycle())
                .isEqualTo("active");
    }

    @Test
    void browseOnlyIsNotRetrieved() {
        Instant now = Instant.parse("2026-08-22T03:04:00Z");
        AtlasUserRecord user = owner("usr_ret_browse", now);
        LogicalKnowledgeBaseRecord kb =
                knowledgeBases.insert(
                        new LogicalKnowledgeBaseRecord(
                                "lkb_ret_browse",
                                "Browse",
                                "desc",
                                user.userId(),
                                "private",
                                "support",
                                "internal",
                                true,
                                "browse_only",
                                "active",
                                "healthy",
                                1,
                                null,
                                false,
                                null,
                                now,
                                now,
                                now));
        binding("bnd_ret_browse", kb.logicalKbId(), "git_markdown", "{\"repo\":\"org/runbooks\"}", now);

        RetrievalTurn turn = retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> successful = (List<String>) turn.coverage().get("successful");
        assertThat(successful).isEmpty();
    }

    private AtlasUserRecord owner(String userId, Instant now) {
        return users.insert(
                new AtlasUserRecord(
                        userId, "sso-" + userId, "Owner", null, "[\"end_user\",\"kb_owner\"]", true, now, now));
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(RetrievalTurn turn, String key) {
        return (List<String>) turn.coverage().get(key);
    }

    private void assertTimeoutCoverage(RetrievalTurn turn, BindingRecord binding) {
        @SuppressWarnings("unchecked")
        List<String> timedOut = (List<String>) turn.coverage().get("timed_out");
        assertThat(timedOut).containsExactly(binding.bindingId());
        @SuppressWarnings("unchecked")
        List<String> quotaLimited = (List<String>) turn.coverage().get("quota_limited");
        assertThat(quotaLimited).isEmpty();
        assertPositiveRetryAfter(turn, binding.bindingId());
    }

    private void assertPositiveRetryAfter(RetrievalTurn turn, String bindingId) {
        @SuppressWarnings("unchecked")
        Map<String, String> retryAfter =
                (Map<String, String>) turn.coverage().get("retry_after");
        assertThat(retryAfter).containsKey(bindingId);
        assertThat(Duration.parse(retryAfter.get(bindingId))).isPositive();
    }

    private RetrievalTurn retrieve(
            AtlasUserRecord user, String question, List<String> logicalKbIds) {
        List<RetrievalScope.KnowledgeBaseSnapshot> snapshots =
                logicalKbIds.stream()
                        .map(
                                logicalKbId ->
                                        new RetrievalScope.KnowledgeBaseSnapshot(
                                                knowledgeBases.findById(logicalKbId).orElseThrow(),
                                                bindings.findByLogicalKbId(logicalKbId)))
                        .toList();
        return retrieval.retrieve(user, question, new RetrievalScope(snapshots));
    }

    private LogicalKnowledgeBaseRecord chatReadyKb(String logicalKbId, String ownerUserId, Instant now) {
        return knowledgeBases.insert(
                new LogicalKnowledgeBaseRecord(
                        logicalKbId,
                        "Chat KB",
                        "desc",
                        ownerUserId,
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
                        now,
                        now,
                        now));
    }

    private BindingRecord binding(
            String bindingId, String logicalKbId, String provider, String identity, Instant now) {
        return binding(
                bindingId,
                logicalKbId,
                provider,
                identity,
                "healthy",
                true,
                false,
                true,
                now);
    }

    private BindingRecord binding(
            String bindingId,
            String logicalKbId,
            String provider,
            String identity,
            String health,
            boolean enabled,
            boolean killSwitch,
            boolean featureFlag,
            Instant now) {
        return bindings.insert(
                new BindingRecord(
                        bindingId,
                        logicalKbId,
                        provider,
                        identity,
                        "canonical",
                        "delegated_user",
                        health,
                        enabled,
                        killSwitch,
                        featureFlag,
                        null,
                        "{}",
                        "owner@example.com",
                        null,
                        1,
                        now,
                        now));
    }
}
