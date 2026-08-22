package com.atlas.knowledgebase.audit;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Repository
public class AuditEventRepository {

    private static final org.springframework.jdbc.core.RowMapper<AuditEventRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new AuditEventRecord(
                            rs.getString("event_id"),
                            rs.getTimestamp("occurred_at").toInstant(),
                            rs.getString("user_id"),
                            rs.getString("logical_kb_id"),
                            rs.getString("binding_id"),
                            rs.getString("connector"),
                            rs.getString("action"),
                            rs.getString("authorization_result"),
                            rs.getString("evidence_locator_ids"),
                            rs.getString("model_id"),
                            rs.getObject("latency_ms") == null
                                    ? null
                                    : ((Number) rs.getObject("latency_ms")).intValue(),
                            rs.getString("status"),
                            rs.getString("error_category"),
                            rs.getString("details"));

    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void insert(AuditEventRecord event) {
        insertRow(event);
    }

    /** Writes a denial/security event independently of a business transaction that will roll back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertIndependent(AuditEventRecord event) {
        insertRow(event);
    }

    private void insertRow(AuditEventRecord event) {
        jdbcTemplate.update(
                """
                INSERT INTO audit_event (
                  event_id, occurred_at, user_id, logical_kb_id, binding_id, connector,
                  action, authorization_result, evidence_locator_ids, model_id, latency_ms,
                  status, error_category, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.eventId(),
                Timestamp.from(event.occurredAt()),
                event.userId(),
                event.logicalKbId(),
                event.bindingId(),
                event.connector(),
                event.action(),
                event.authorizationResult(),
                event.evidenceLocatorIdsJson(),
                event.modelId(),
                event.latencyMs(),
                event.status(),
                event.errorCategory(),
                event.detailsJson());
    }

    public int countByUserAction(String userId, String action) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM audit_event
                        WHERE user_id = ? AND action = ?
                        """,
                        Integer.class,
                        userId,
                        action);
        return count == null ? 0 : count;
    }

    public String latestDetailsByUserAction(String userId, String action) {
        return jdbcTemplate.query(
                """
                SELECT details FROM audit_event
                WHERE user_id = ? AND action = ?
                ORDER BY occurred_at DESC
                """,
                rs -> rs.next() ? rs.getString("details") : null,
                userId,
                action);
    }

    public Optional<AuditEventRecord> findById(String eventId) {
        return jdbcTemplate
                .query("SELECT * FROM audit_event WHERE event_id = ?", ROW_MAPPER, eventId)
                .stream()
                .findFirst();
    }

}
