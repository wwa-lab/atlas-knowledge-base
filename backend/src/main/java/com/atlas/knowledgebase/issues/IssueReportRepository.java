package com.atlas.knowledgebase.issues;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IssueReportRepository {

    private static final RowMapper<IssueReportRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new IssueReportRecord(
                            rs.getString("issue_id"),
                            rs.getString("user_id"),
                            rs.getString("message_id"),
                            rs.getString("citation_id"),
                            rs.getString("category"),
                            rs.getString("diagnostics"),
                            rs.getString("route_target"),
                            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public IssueReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IssueReportRecord insert(IssueReportRecord report) {
        Instant created = report.createdAt() == null ? Instant.now() : report.createdAt();
        jdbcTemplate.update(
                """
                INSERT INTO issue_report (
                  issue_id, user_id, message_id, citation_id, category, diagnostics,
                  route_target, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                report.issueId(),
                report.userId(),
                report.messageId(),
                report.citationId(),
                report.category(),
                report.diagnosticsJson(),
                report.routeTarget(),
                Timestamp.from(created));
        return findById(report.issueId()).orElseThrow();
    }

    public Optional<IssueReportRecord> findById(String issueId) {
        return jdbcTemplate
                .query("SELECT * FROM issue_report WHERE issue_id = ?", ROW_MAPPER, issueId)
                .stream()
                .findFirst();
    }
}
