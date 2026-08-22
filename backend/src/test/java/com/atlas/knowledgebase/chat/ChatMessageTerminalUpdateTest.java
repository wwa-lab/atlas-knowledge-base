package com.atlas.knowledgebase.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class ChatMessageTerminalUpdateTest {

    @Autowired private ChatMessageRepository messages;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void completedWriteDoesNotOverwriteCancel() {
        String messageId = insertInFlight("msg_term_cancel_wins");
        Instant done = Instant.parse("2026-08-22T02:00:00Z");
        assertThat(messages.cancelIfInFlight(messageId, done)).isEqualTo(1);
        assertThat(messages.completeIfInFlight(messageId, "should not persist", "{}", done)).isZero();
        assertThat(status(messageId)).isEqualTo("incomplete_cancelled");
        assertThat(answer(messageId)).isNull();
    }

    @Test
    void cancelDoesNotOverwriteCompleted() {
        String messageId = insertInFlight("msg_term_complete_wins");
        Instant done = Instant.parse("2026-08-22T02:01:00Z");
        assertThat(messages.completeIfInFlight(messageId, "grounded stub", "{}", done)).isEqualTo(1);
        assertThat(messages.cancelIfInFlight(messageId, done)).isZero();
        assertThat(status(messageId)).isEqualTo("completed");
        assertThat(answer(messageId)).isEqualTo("grounded stub");
    }

    private String insertInFlight(String messageId) {
        Timestamp now = Timestamp.from(Instant.parse("2026-08-22T01:00:00Z"));
        jdbcTemplate.update(
                """
                INSERT INTO atlas_user
                  (user_id, sso_subject, roles, model_entitled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "usr_" + messageId,
                "sso-" + messageId,
                "[\"end_user\"]",
                0,
                now,
                now);
        jdbcTemplate.update(
                """
                INSERT INTO chat_thread
                  (thread_id, user_id, selected_logical_kb_ids, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                "thr_" + messageId,
                "usr_" + messageId,
                "[]",
                now,
                now);
        jdbcTemplate.update(
                """
                INSERT INTO chat_message
                  (message_id, thread_id, message_role, status, question_text, answer_text,
                   logical_kb_scope, binding_set, config_versions, request_id, created_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                messageId,
                "thr_" + messageId,
                "assistant",
                "streaming",
                null,
                null,
                "[]",
                "[]",
                "{}",
                "req_" + messageId,
                now,
                null);
        return messageId;
    }

    private String status(String messageId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM chat_message WHERE message_id = ?", String.class, messageId);
    }

    private String answer(String messageId) {
        return jdbcTemplate.queryForObject(
                "SELECT answer_text FROM chat_message WHERE message_id = ?", String.class, messageId);
    }
}
