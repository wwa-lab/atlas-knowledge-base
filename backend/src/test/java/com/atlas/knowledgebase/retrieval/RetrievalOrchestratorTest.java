package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class RetrievalOrchestratorTest {

    @Autowired private RetrievalOrchestrator retrieval;
    @Autowired private AtlasUserRepository users;
    @Autowired private LogicalKnowledgeBaseRepository knowledgeBases;
    @Autowired private BindingRepository bindings;

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
                retrieval.retrieve(
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

        RetrievalTurn turn = retrieval.retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.NO_EVIDENCE);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> timedOut = (List<String>) turn.coverage().get("timed_out");
        assertThat(timedOut).containsExactly(binding.bindingId());
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

        RetrievalTurn turn = retrieval.retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.block()).isEqualTo(RetrievalTurn.Block.BINDING_ACCESS);
        assertThat(turn.fused()).isEmpty();
        @SuppressWarnings("unchecked")
        List<String> failed = (List<String>) turn.coverage().get("failed");
        assertThat(failed).contains("bnd_ret_fr47_denied");
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

        RetrievalTurn turn = retrieval.retrieve(user, "question", List.of(kb.logicalKbId()));

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

        RetrievalTurn turn = retrieval.retrieve(user, "question", List.of(kb.logicalKbId()));

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
                retrieval.retrieve(user, "question", List.of(ok.logicalKbId(), failed.logicalKbId()));

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

        RetrievalTurn turn = retrieval.retrieve(user, "question", List.of(kb.logicalKbId()));

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

        RetrievalTurn turn = retrieval.retrieve(user, "question", List.of(kb.logicalKbId()));

        assertThat(turn.blocked()).isFalse();
        @SuppressWarnings("unchecked")
        List<String> successful = (List<String>) turn.coverage().get("successful");
        assertThat(successful).contains(binding.bindingId());
        assertThat(turn.fused()).hasSize(1);
        assertThat(turn.fused().getFirst().hit().documentId()).endsWith(":kept");
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

        RetrievalTurn turn = retrieval.retrieve(user, "question", List.of(kb.logicalKbId()));

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
        return bindings.insert(
                new BindingRecord(
                        bindingId,
                        logicalKbId,
                        provider,
                        identity,
                        "canonical",
                        "delegated_user",
                        "healthy",
                        true,
                        false,
                        false,
                        null,
                        "{}",
                        "owner@example.com",
                        null,
                        1,
                        now,
                        now));
    }
}
