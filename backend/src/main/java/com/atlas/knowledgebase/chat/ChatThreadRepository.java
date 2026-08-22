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
public class ChatThreadRepository {

    private static final RowMapper<ChatThreadRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new ChatThreadRecord(
                            rs.getString("thread_id"),
                            rs.getString("user_id"),
                            rs.getString("title"),
                            rs.getString("selected_logical_kb_ids"),
                            rs.getString("branched_from_thread_id"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            rs.getTimestamp("deleted_at") == null
                                    ? null
                                    : rs.getTimestamp("deleted_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ChatThreadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ChatThreadRecord insert(ChatThreadRecord thread) {
        Instant created = thread.createdAt() != null ? thread.createdAt() : Instant.now();
        Instant updated = thread.updatedAt() != null ? thread.updatedAt() : created;
        jdbcTemplate.update(
                """
                INSERT INTO chat_thread (
                  thread_id, user_id, title, selected_logical_kb_ids, branched_from_thread_id,
                  created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                thread.threadId(),
                thread.userId(),
                thread.title(),
                thread.selectedLogicalKbIdsJson(),
                thread.branchedFromThreadId(),
                Timestamp.from(created),
                Timestamp.from(updated),
                thread.deletedAt() == null ? null : Timestamp.from(thread.deletedAt()));
        return findById(thread.threadId()).orElseThrow();
    }

    public Optional<ChatThreadRecord> findById(String threadId) {
        return jdbcTemplate
                .query("SELECT * FROM chat_thread WHERE thread_id = ?", ROW_MAPPER, threadId)
                .stream()
                .findFirst();
    }

    public List<ChatThreadRecord> findActiveByUserId(String userId) {
        return jdbcTemplate.query(
                """
                SELECT * FROM chat_thread
                WHERE user_id = ? AND deleted_at IS NULL
                ORDER BY updated_at DESC, thread_id DESC
                """,
                ROW_MAPPER,
                userId);
    }

    @Transactional
    public ChatThreadRecord updateScope(String threadId, String selectedLogicalKbIdsJson, Instant updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE chat_thread
                SET selected_logical_kb_ids = ?, updated_at = ?
                WHERE thread_id = ? AND deleted_at IS NULL
                """,
                selectedLogicalKbIdsJson,
                Timestamp.from(updatedAt),
                threadId);
        return findById(threadId).orElseThrow();
    }

    @Transactional
    public void touch(String threadId, Instant updatedAt) {
        jdbcTemplate.update(
                "UPDATE chat_thread SET updated_at = ? WHERE thread_id = ?",
                Timestamp.from(updatedAt),
                threadId);
    }

    @Transactional
    public void softDelete(String threadId, Instant deletedAt) {
        jdbcTemplate.update(
                """
                UPDATE chat_thread
                SET deleted_at = ?, updated_at = ?
                WHERE thread_id = ? AND deleted_at IS NULL
                """,
                Timestamp.from(deletedAt),
                Timestamp.from(deletedAt),
                threadId);
    }
}
