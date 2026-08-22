package com.atlas.knowledgebase.chat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ChatMessageRepository {

    private static final RowMapper<ChatMessageRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new ChatMessageRecord(
                            rs.getString("message_id"),
                            rs.getString("thread_id"),
                            rs.getString("message_role"),
                            rs.getString("status"),
                            rs.getString("question_text"),
                            rs.getString("answer_text"),
                            rs.getString("logical_kb_scope"),
                            rs.getString("binding_set"),
                            rs.getString("config_versions"),
                            rs.getString("coverage"),
                            rs.getString("conflict_section"),
                            rs.getString("classification"),
                            rs.getString("request_id"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("completed_at") == null
                                    ? null
                                    : rs.getTimestamp("completed_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ChatMessageRecord insert(ChatMessageRecord message) {
        Instant created = message.createdAt() != null ? message.createdAt() : Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO chat_message (
                  message_id, thread_id, message_role, status, question_text, answer_text,
                  logical_kb_scope, binding_set, config_versions, coverage, conflict_section,
                  classification, request_id, created_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                message.messageId(),
                message.threadId(),
                message.role(),
                message.status(),
                message.questionText(),
                message.answerText(),
                message.logicalKbScopeJson(),
                message.bindingSetJson(),
                message.configVersionsJson(),
                message.coverageJson(),
                message.conflictSectionJson(),
                message.classification(),
                message.requestId(),
                Timestamp.from(created),
                message.completedAt() == null ? null : Timestamp.from(message.completedAt()));
        return findById(message.messageId()).orElseThrow();
    }

    /** Persists the user question and its processing assistant placeholder atomically. */
    @Transactional
    public void insertAskPair(ChatMessageRecord userMessage, ChatMessageRecord assistantMessage) {
        insert(userMessage);
        insert(assistantMessage);
    }

    public Optional<ChatMessageRecord> findById(String messageId) {
        return jdbcTemplate
                .query("SELECT * FROM chat_message WHERE message_id = ?", ROW_MAPPER, messageId)
                .stream()
                .findFirst();
    }

    public List<ChatMessageRecord> findByThreadId(String threadId) {
        return jdbcTemplate.query(
                """
                SELECT * FROM chat_message
                WHERE thread_id = ?
                ORDER BY created_at, message_id
                """,
                ROW_MAPPER,
                threadId);
    }

    public int countByThreadId(String threadId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM chat_message WHERE thread_id = ?", Integer.class, threadId);
        return count == null ? 0 : count;
    }

    /** @return rows updated; 0 means a terminal write already won. */
    @Transactional
    public int completeIfInFlight(
            String messageId, String answerText, String coverageJson, Instant completedAt) {
        return updateAnswerIfInFlight(messageId, "completed", answerText, coverageJson, completedAt);
    }

    /** @return rows updated; 0 means completed/failed already won. */
    @Transactional
    public int cancelIfInFlight(String messageId, Instant completedAt) {
        return updateAnswerIfInFlight(messageId, "incomplete_cancelled", null, null, completedAt);
    }

    @Transactional
    public int failIfInFlight(String messageId, Instant completedAt) {
        return updateAnswerIfInFlight(messageId, "failed", null, null, completedAt);
    }

    @Transactional
    public int markStreamingIfProcessing(String messageId) {
        return jdbcTemplate.update(
                """
                UPDATE chat_message
                SET status = ?
                WHERE message_id = ? AND status = ?
                """,
                "streaming",
                messageId,
                "processing");
    }

    @Transactional
    public int markProcessingIfRetryable(
            String messageId,
            String logicalKbScopeJson,
            String bindingSetJson,
            String configVersionsJson,
            String classification) {
        return jdbcTemplate.update(
                """
                UPDATE chat_message
                SET status = ?, answer_text = NULL, logical_kb_scope = ?, binding_set = ?,
                    config_versions = ?, classification = ?, coverage = NULL, completed_at = NULL
                WHERE message_id = ? AND status IN ('incomplete_cancelled', 'failed')
                """,
                "processing",
                logicalKbScopeJson,
                bindingSetJson,
                configVersionsJson,
                classification,
                messageId);
    }

    private int updateAnswerIfInFlight(
            String messageId,
            String status,
            String answerText,
            String coverageJson,
            Instant completedAt) {
        return jdbcTemplate.update(
                """
                UPDATE chat_message
                SET status = ?, answer_text = ?, coverage = ?, completed_at = ?
                WHERE message_id = ? AND status IN ('processing', 'streaming')
                """,
                status,
                answerText,
                coverageJson,
                completedAt == null ? null : Timestamp.from(completedAt),
                messageId);
    }
}
