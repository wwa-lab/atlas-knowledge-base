package com.atlas.knowledgebase.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.chat.AssistantCompletionService;
import com.atlas.knowledgebase.chat.ChatMessageRecord;
import com.atlas.knowledgebase.chat.ChatMessageRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.retrieval.ReciprocalRankFusion;
import com.atlas.knowledgebase.retrieval.RetrievalScope;
import com.atlas.knowledgebase.retrieval.RetrievalTurn;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class CitationCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T08:00:00Z");
    private static final String USER_ID = "usr_citation_test";
    private static final String OTHER_USER_ID = "usr_citation_other";
    private static final String KB_ID = "lkb_citation_test";
    private static final String BINDING_ID = "bnd_citation_test";
    private static final String THREAD_ID = "thr_citation_test";
    private static final String MESSAGE_ID = "msg_citation_test";

    @Autowired private AssistantCompletionService completions;
    @Autowired private CitationAssembler assembler;
    @Autowired private ChatMessageRepository messages;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private CitationRepository citations;

    @BeforeEach
    void seed() {
        jdbc.update(
                "INSERT INTO atlas_user (user_id,sso_subject,display_name,email,roles,model_entitled,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                USER_ID,
                "sso-citation-test",
                "Citation Owner",
                "owner@example.test",
                "[]",
                1,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        jdbc.update(
                "INSERT INTO atlas_user (user_id,sso_subject,display_name,email,roles,model_entitled,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                OTHER_USER_ID,
                "sso-citation-other",
                "Other User",
                "other@example.test",
                "[]",
                1,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        jdbc.update(
                "INSERT INTO logical_knowledge_base (logical_kb_id,name,owner_user_id,discoverability,purpose,classification,model_eligible,capability,lifecycle,health,config_version,freshness_required,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                KB_ID,
                "Citation KB",
                USER_ID,
                "private",
                "citation fixture",
                "internal",
                1,
                "chat_ready",
                "active",
                "healthy",
                1,
                0,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        jdbc.update(
                "INSERT INTO binding (binding_id,logical_kb_id,provider_profile,source_identity,binding_role,auth_method,health,enabled,kill_switch,feature_flag,locator_rules,credential_owner,config_version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                BINDING_ID,
                KB_ID,
                "git_markdown",
                "{\"repo\":\"atlas/docs\",\"atlas_fixture\":true}",
                "canonical",
                "delegated_user",
                "healthy",
                1,
                0,
                1,
                "{}",
                USER_ID,
                1,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        jdbc.update(
                "INSERT INTO chat_thread (thread_id,user_id,selected_logical_kb_ids,created_at,updated_at) VALUES (?,?,?,?,?)",
                THREAD_ID,
                USER_ID,
                "[\"" + KB_ID + "\"]",
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        insertAssistant(MESSAGE_ID, "streaming");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM citation WHERE message_id LIKE 'msg_citation_%'");
        jdbc.update("DELETE FROM chat_message WHERE thread_id = ?", THREAD_ID);
        jdbc.update("DELETE FROM chat_thread WHERE thread_id = ?", THREAD_ID);
        jdbc.update("DELETE FROM binding WHERE binding_id = ?", BINDING_ID);
        jdbc.update("DELETE FROM logical_knowledge_base WHERE logical_kb_id = ?", KB_ID);
        jdbc.update("DELETE FROM atlas_user WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
    }

    @Test
    void assemblesEveryProvenancePathWithDeterministicIdsAndAnswerTimeMetadata() {
        RetrievalTurn turn = turn(List.of(path("fp-a", "A title", "A excerpt", 1), path("fp-b", "B title", "B excerpt", 2)));

        CitationAssembler.Assembly first = assembler.assemble(MESSAGE_ID, turn, NOW);
        CitationAssembler.Assembly second = assembler.assemble(MESSAGE_ID, turn, NOW);

        assertThat(first.citations()).hasSize(2);
        assertThat(first.citations().stream().map(CitationRecord::citationId).toList())
                .containsExactlyElementsOf(second.citations().stream().map(CitationRecord::citationId).toList())
                .allSatisfy(id -> assertThat(id).startsWith("cit_").hasSizeLessThanOrEqualTo(64));
        assertThat(first.citations().stream().map(CitationRecord::documentTitle).toList())
                .containsExactly("A title", "B title");
        assertThat(first.citations())
                .allSatisfy(
                        citation -> {
                            assertThat(citation.owner()).isEqualTo("Citation Owner");
                            assertThat(citation.classification()).isEqualTo("internal");
                            assertThat(citation.atlasVerifiedAt()).isEqualTo(NOW);
                            assertThat(citation.sourceUpdatedAt()).isNull();
                            assertThat(citation.resolveStatus()).isEqualTo("ok");
                        });
        assertThat(first.summaries())
                .allSatisfy(
                        summary ->
                                assertThat(summary)
                                        .containsOnlyKeys(
                                                "citation_id", "logical_kb_id", "binding_id", "provider", "title"));
        assertThat(assembler.bindingRoleSnapshot(turn.scope()))
                .containsExactly(Map.of("binding_id", BINDING_ID, "binding_role", "canonical"));
    }

    @Test
    void fallsBackToStableOwnerIdWhenDisplayNameIsBlank() {
        jdbc.update("UPDATE atlas_user SET display_name = '   ' WHERE user_id = ?", USER_ID);

        CitationAssembler.Assembly assembly = assembler.assemble(MESSAGE_ID, turn(List.of(path("fp-a", "Title", "Excerpt", 1))), NOW);

        assertThat(assembly.citations().getFirst().owner()).isEqualTo(USER_ID);
    }

    @Test
    void fallsBackToStableOwnerIdWhenDisplayNameIsNull() {
        jdbc.update("UPDATE atlas_user SET display_name = NULL WHERE user_id = ?", USER_ID);

        CitationAssembler.Assembly assembly =
                assembler.assemble(
                        MESSAGE_ID,
                        turn(List.of(path("fp-a", "Title", "Excerpt", 1))),
                        NOW);

        assertThat(assembly.citations().getFirst().owner()).isEqualTo(USER_ID);
    }

    @Test
    void filtersInvalidProvenanceBeforeModelAndDisclosesItemOmissionInCoverage() {
        RetrievalTurn mixed =
                turn(
                        List.of(
                                path("fp-invalid", " ", "Invalid excerpt", 1),
                                path("fp-valid", "Valid title", "Valid excerpt", 2)));

        RetrievalTurn filtered = assembler.filterValidCandidates(mixed);

        assertThat(filtered.fused()).hasSize(1);
        assertThat(filtered.fused().getFirst().provenance()).hasSize(1);
        assertThat(filtered.fused().getFirst().hit().fingerprint()).isEqualTo("fp-valid");
        assertThat(filtered.coverage()).containsEntry("partial_coverage", true);
        assertThat(filtered.coverage().get("item_omitted")).asList().hasSize(1);

        RetrievalTurn allInvalid =
                assembler.filterValidCandidates(
                        turn(List.of(path("fp-invalid", " ", "Invalid excerpt", 1))));
        assertThat(allInvalid.fused()).isEmpty();
    }

    @Test
    void winnerCompletesAndPersistsWhilePrivateLookupAndReplayStayScoped() {
        RetrievalTurn turn = turn(List.of(path("fp-a", "A title", "A excerpt", 1), path("fp-b", "B title", "B excerpt", 2)));

        AssistantCompletionService.CompletionResult result =
                completions.complete(MESSAGE_ID, "answer", "{\"successful\":[\"bnd_citation_test\"]}", turn, NOW);

        assertThat(result.won()).isTrue();
        assertThat(messages.findById(MESSAGE_ID).orElseThrow().status()).isEqualTo("completed");
        assertThat(citations.findByMessageId(MESSAGE_ID)).hasSize(2);
        CitationRecord stored = citations.findByMessageId(MESSAGE_ID).getFirst();
        assertThat(citations.findOwnedByCitationId(stored.citationId(), USER_ID)).contains(stored);
        assertThat(citations.findOwnedByCitationId(stored.citationId(), OTHER_USER_ID)).isEmpty();
        assertThat(citations.summariesByMessageId(MESSAGE_ID)).containsExactlyElementsOf(result.summaries());

        AssistantCompletionService.CompletionResult loser =
                completions.complete(MESSAGE_ID, "losing answer", "{}", turn, NOW.plusSeconds(1));
        assertThat(loser.won()).isFalse();
        assertThat(citations.findByMessageId(MESSAGE_ID)).hasSize(2);
        assertThat(messages.findById(MESSAGE_ID).orElseThrow().answerText()).isEqualTo("answer");

        jdbc.update(
                "UPDATE chat_message SET status = 'failed', answer_text = NULL, completed_at = NULL WHERE message_id = ?",
                MESSAGE_ID);
        assertThat(
                        messages.markProcessingIfRetryable(
                                MESSAGE_ID,
                                "[\"" + KB_ID + "\"]",
                                "[{\"binding_id\":\"" + BINDING_ID + "\",\"binding_role\":\"canonical\"}]",
                                "{}",
                                "internal"))
                .isEqualTo(1);
        RetrievalTurn retryTurn = turn(List.of(path("fp-retry", "Retry title", "Retry excerpt", 1)));
        AssistantCompletionService.CompletionResult retry =
                completions.complete(MESSAGE_ID, "retry answer", "{}", retryTurn, NOW.plusSeconds(2));
        assertThat(retry.won()).isTrue();
        assertThat(citations.findByMessageId(MESSAGE_ID))
                .singleElement()
                .extracting(CitationRecord::documentTitle)
                .isEqualTo("Retry title");

        jdbc.update(
                "UPDATE chat_thread SET deleted_at = ? WHERE thread_id = ?",
                Timestamp.from(NOW.plusSeconds(3)),
                THREAD_ID);
        assertThat(
                        citations.findOwnedByCitationId(
                                retry.citations().getFirst().citationId(), USER_ID))
                .isEmpty();
    }

    @Test
    void invalidCompletionMetadataRollsBackAssistantCompletionAndCitationReplacement() {
        RetrievalTurn invalid = turn(List.of(path("fp-invalid", " ", "Excerpt", 1)));

        assertThatThrownBy(() -> completions.complete(MESSAGE_ID, "answer", "{}", invalid, NOW))
                .isInstanceOf(CitationAssembler.CitationMetadataIncompleteException.class)
                .hasMessageContaining("document_title");

        assertThat(messages.findById(MESSAGE_ID).orElseThrow().status()).isEqualTo("streaming");
        assertThat(citations.findByMessageId(MESSAGE_ID)).isEmpty();
    }

    @Test
    void repositoryInsertFailureRollsBackAssistantCompletion() {
        RetrievalTurn valid = turn(List.of(path("fp-a", "A title", "A excerpt", 1)));
        doThrow(new IllegalStateException("simulated insert failure"))
                .when(citations)
                .replaceForMessage(eq(MESSAGE_ID), anyList());

        assertThatThrownBy(() -> completions.complete(MESSAGE_ID, "answer", "{}", valid, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated insert failure");

        assertThat(messages.findById(MESSAGE_ID).orElseThrow().status()).isEqualTo("streaming");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM citation WHERE message_id = ?", Integer.class, MESSAGE_ID))
                .isZero();
    }

    private void insertAssistant(String messageId, String status) {
        jdbc.update(
                "INSERT INTO chat_message (message_id,thread_id,message_role,status,logical_kb_scope,binding_set,config_versions,classification,request_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                messageId,
                THREAD_ID,
                "assistant",
                status,
                "[\"" + KB_ID + "\"]",
                "[{\"binding_id\":\"" + BINDING_ID + "\",\"binding_role\":\"canonical\"}]",
                "{}",
                "internal",
                "req_citation_test",
                Timestamp.from(NOW));
    }

    private RetrievalTurn turn(List<ReciprocalRankFusion.Provenance> provenance) {
        ReciprocalRankFusion.FusedHit fused =
                new ReciprocalRankFusion.FusedHit(
                        provenance.getFirst().hit(), provenance, KB_ID, BINDING_ID, "git_markdown");
        return new RetrievalTurn(
                Map.of("successful", List.of(BINDING_ID)),
                List.of(fused),
                List.of(),
                null,
                scope(),
                RetrievalTurn.Block.NONE,
                null,
                null);
    }

    private ReciprocalRankFusion.Provenance path(
            String fingerprint, String title, String excerpt, int rank) {
        Retriever.Hit hit =
                new Retriever.Hit(
                        "source:" + fingerprint,
                        "https://fixture.invalid/" + fingerprint,
                        "doc-" + fingerprint,
                        title,
                        excerpt,
                        "v1",
                        "{\"repository\":\"atlas/docs\",\"commit_sha\":\"40ac7e9\",\"path\":\"docs/runbook.md\",\"line_range\":[10,40],\"atlas_fixture\":true}",
                        rank,
                        fingerprint);
        return new ReciprocalRankFusion.Provenance(KB_ID, BINDING_ID, "git_markdown", rank, hit);
    }

    private RetrievalScope scope() {
        return new RetrievalScope(
                List.of(
                        new RetrievalScope.KnowledgeBaseSnapshot(
                                new LogicalKnowledgeBaseRecord(
                                        KB_ID,
                                        "Citation KB",
                                        null,
                                        USER_ID,
                                        "private",
                                        "citation fixture",
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
                                        NOW),
                                List.of(
                                        new BindingRecord(
                                                BINDING_ID,
                                                KB_ID,
                                                "git_markdown",
                                                "{\"repo\":\"atlas/docs\",\"atlas_fixture\":true}",
                                                "canonical",
                                                "delegated_user",
                                                "healthy",
                                                true,
                                                false,
                                                true,
                                                null,
                                                "{}",
                                                USER_ID,
                                                null,
                                                1,
                                                NOW,
                                                NOW)))));
    }
}
