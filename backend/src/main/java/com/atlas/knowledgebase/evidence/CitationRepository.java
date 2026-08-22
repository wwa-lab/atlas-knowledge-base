package com.atlas.knowledgebase.evidence;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CitationRepository {

    private static final RowMapper<CitationRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new CitationRecord(
                            rs.getString("citation_id"),
                            rs.getString("message_id"),
                            rs.getString("logical_kb_id"),
                            rs.getString("binding_id"),
                            rs.getString("provider"),
                            rs.getString("locator"),
                            rs.getString("version_label"),
                            rs.getString("excerpt"),
                            rs.getString("document_title"),
                            rs.getString("owner"),
                            rs.getString("classification"),
                            rs.getTimestamp("source_updated_at") == null
                                    ? null
                                    : rs.getTimestamp("source_updated_at").toInstant(),
                            rs.getTimestamp("atlas_verified_at") == null
                                    ? null
                                    : rs.getTimestamp("atlas_verified_at").toInstant(),
                            rs.getString("resolve_status"));

    private final JdbcTemplate jdbcTemplate;

    public CitationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Replaces the complete citation set. The caller owns the surrounding completion transaction. */
    public void replaceForMessage(String messageId, List<CitationRecord> replacement) {
        List<CitationRecord> rows = replacement == null ? List.of() : List.copyOf(replacement);
        if (rows.stream().anyMatch(row -> !messageId.equals(row.messageId()))) {
            throw new IllegalArgumentException("all citations must belong to the replaced message");
        }
        jdbcTemplate.update("DELETE FROM citation WHERE message_id = ?", messageId);
        for (CitationRecord citation : rows) {
            jdbcTemplate.update(
                    """
                    INSERT INTO citation (
                      citation_id, message_id, logical_kb_id, binding_id, provider, locator,
                      version_label, excerpt, document_title, owner, classification,
                      source_updated_at, atlas_verified_at, resolve_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    citation.citationId(),
                    citation.messageId(),
                    citation.logicalKbId(),
                    citation.bindingId(),
                    citation.provider(),
                    citation.locatorJson(),
                    citation.versionLabel(),
                    citation.excerpt(),
                    citation.documentTitle(),
                    citation.owner(),
                    citation.classification(),
                    timestamp(citation.sourceUpdatedAt()),
                    timestamp(citation.atlasVerifiedAt()),
                    citation.resolveStatus());
        }
    }

    public List<CitationRecord> findByMessageId(String messageId) {
        return jdbcTemplate.query(
                "SELECT * FROM citation WHERE message_id = ? ORDER BY citation_id",
                ROW_MAPPER,
                messageId);
    }

    /**
     * Looks up a citation only through a completed assistant message in the user's active private
     * thread. Missing and cross-user rows therefore have the same result.
     */
    public Optional<CitationRecord> findOwnedByCitationId(String citationId, String userId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT c.*
                        FROM citation c
                        JOIN chat_message m ON m.message_id = c.message_id
                        JOIN chat_thread t ON t.thread_id = m.thread_id
                        WHERE c.citation_id = ?
                          AND t.user_id = ?
                          AND t.deleted_at IS NULL
                          AND m.message_role = 'assistant'
                          AND m.status = 'completed'
                        """,
                        ROW_MAPPER,
                        citationId,
                        userId)
                .stream()
                .findFirst();
    }

    /** Accepted compact citation projection used by final SSE and completed-message replay. */
    public List<Map<String, Object>> summariesByMessageId(String messageId) {
        List<CitationRecord> rows = findByMessageId(messageId);
        List<Map<String, Object>> summaries = new ArrayList<>(rows.size());
        rows.forEach(
                citation -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("citation_id", citation.citationId());
                    summary.put("logical_kb_id", citation.logicalKbId());
                    summary.put("binding_id", citation.bindingId());
                    summary.put("provider", citation.provider());
                    summary.put("title", citation.documentTitle());
                    summaries.add(Map.copyOf(summary));
                });
        return List.copyOf(summaries);
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
