package com.atlas.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class CoreSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void coreTablesExist() {
        List<String> expected =
                List.of(
                        "ATLAS_USER",
                        "ATLAS_SESSION",
                        "PROVIDER_CONNECTION",
                        "LOGICAL_KNOWLEDGE_BASE",
                        "BINDING",
                        "CONTENT_AUDIT_RESULT",
                        "CHAT_THREAD",
                        "CHAT_MESSAGE",
                        "CITATION",
                        "ISSUE_REPORT",
                        "AUDIT_EVENT");
        List<String> tables =
                jdbcTemplate.queryForList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE'
                        """,
                        String.class);
        assertThat(tables).containsAll(expected);
    }

    @Test
    void lifecycleAndHealthAreSeparateColumns() {
        List<String> columns =
                jdbcTemplate.queryForList(
                        """
                        SELECT column_name FROM information_schema.columns
                        WHERE table_name = 'LOGICAL_KNOWLEDGE_BASE'
                        """,
                        String.class);
        assertThat(columns).contains("LIFECYCLE", "HEALTH");
        assertThat(columns).doesNotContain("STATUS");
    }

    @Test
    void incompleteChatMessageIsRepresentable() {
        Timestamp now = Timestamp.from(Instant.parse("2026-08-20T05:00:00Z"));
        jdbcTemplate.update(
                """
                INSERT INTO atlas_user
                  (user_id, sso_subject, roles, model_entitled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "usr_schema_1",
                "sso-schema-1",
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
                "thr_schema_1",
                "usr_schema_1",
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
                "msg_schema_1",
                "thr_schema_1",
                "assistant",
                "incomplete_cancelled",
                null,
                null,
                "[]",
                "[]",
                "{}",
                "req_schema_1",
                now,
                null);

        String status =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM chat_message WHERE message_id = ?",
                        String.class,
                        "msg_schema_1");
        assertThat(status).isEqualTo("incomplete_cancelled");
        Integer completedAnswers =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM chat_message
                        WHERE message_id = ? AND status = 'completed' AND answer_text IS NOT NULL
                        """,
                        Integer.class,
                        "msg_schema_1");
        assertThat(completedAnswers).isZero();
    }
}
