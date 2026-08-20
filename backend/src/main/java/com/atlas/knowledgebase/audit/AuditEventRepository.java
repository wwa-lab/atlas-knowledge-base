package com.atlas.knowledgebase.audit;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void insert(AuditEventRecord event) {
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
}
