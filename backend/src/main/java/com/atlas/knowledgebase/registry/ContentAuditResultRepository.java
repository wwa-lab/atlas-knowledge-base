package com.atlas.knowledgebase.registry;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ContentAuditResultRepository {

    private static final RowMapper<ContentAuditResultRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new ContentAuditResultRecord(
                            rs.getString("audit_id"),
                            rs.getString("logical_kb_id"),
                            rs.getString("binding_id"),
                            rs.getInt("total_count"),
                            rs.getInt("chat_eligible_count"),
                            rs.getInt("excluded_count"),
                            rs.getString("exclusion_reasons"),
                            rs.getString("remediation_blob_ref"),
                            rs.getTimestamp("audited_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ContentAuditResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ContentAuditResultRecord insert(ContentAuditResultRecord row) {
        Instant audited = row.auditedAt() != null ? row.auditedAt() : Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO content_audit_result (
                  audit_id, logical_kb_id, binding_id, total_count, chat_eligible_count,
                  excluded_count, exclusion_reasons, remediation_blob_ref, audited_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.auditId(),
                row.logicalKbId(),
                row.bindingId(),
                row.totalCount(),
                row.chatEligibleCount(),
                row.excludedCount(),
                row.exclusionReasonsJson(),
                row.remediationBlobRef(),
                Timestamp.from(audited));
        return findById(row.auditId()).orElseThrow();
    }

    public Optional<ContentAuditResultRecord> findById(String auditId) {
        return jdbcTemplate
                .query("SELECT * FROM content_audit_result WHERE audit_id = ?", ROW_MAPPER, auditId)
                .stream()
                .findFirst();
    }

    public List<ContentAuditResultRecord> findLatestByLogicalKbId(String logicalKbId) {
        return jdbcTemplate.query(
                """
                SELECT * FROM content_audit_result a
                WHERE a.logical_kb_id = ?
                  AND a.audited_at = (
                    SELECT MAX(b.audited_at) FROM content_audit_result b
                    WHERE b.logical_kb_id = a.logical_kb_id AND b.binding_id = a.binding_id)
                ORDER BY a.binding_id
                """,
                ROW_MAPPER,
                logicalKbId);
    }

    public Optional<ContentAuditResultRecord> findLatestForBinding(String logicalKbId, String bindingId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT * FROM content_audit_result
                        WHERE logical_kb_id = ? AND binding_id = ?
                        ORDER BY audited_at DESC
                        """,
                        ROW_MAPPER,
                        logicalKbId,
                        bindingId)
                .stream()
                .findFirst();
    }
}
