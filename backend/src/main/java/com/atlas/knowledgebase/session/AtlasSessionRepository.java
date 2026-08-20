package com.atlas.knowledgebase.session;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AtlasSessionRepository {

    private static final RowMapper<AtlasSessionRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new AtlasSessionRecord(
                            rs.getString("session_id"),
                            rs.getString("user_id"),
                            rs.getTimestamp("issued_at").toInstant(),
                            rs.getTimestamp("last_seen_at").toInstant(),
                            rs.getTimestamp("absolute_expires_at").toInstant(),
                            rs.getTimestamp("idle_expires_at").toInstant(),
                            optionalInstant(rs.getTimestamp("revoked_at")),
                            rs.getString("csrf_secret"));

    private final JdbcTemplate jdbcTemplate;

    public AtlasSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AtlasSessionRecord insert(AtlasSessionRecord session) {
        jdbcTemplate.update(
                """
                INSERT INTO atlas_session (
                  session_id, user_id, issued_at, last_seen_at, absolute_expires_at,
                  idle_expires_at, revoked_at, csrf_secret)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                session.sessionId(),
                session.userId(),
                Timestamp.from(session.issuedAt()),
                Timestamp.from(session.lastSeenAt()),
                Timestamp.from(session.absoluteExpiresAt()),
                Timestamp.from(session.idleExpiresAt()),
                session.revokedAt() == null ? null : Timestamp.from(session.revokedAt()),
                session.csrfSecret());
        return findById(session.sessionId()).orElseThrow();
    }

    public Optional<AtlasSessionRecord> findById(String sessionId) {
        return jdbcTemplate
                .query("SELECT * FROM atlas_session WHERE session_id = ?", ROW_MAPPER, sessionId)
                .stream()
                .findFirst();
    }

    @Transactional
    public void touchIdle(String sessionId, Instant lastSeenAt, Instant idleExpiresAt) {
        jdbcTemplate.update(
                """
                UPDATE atlas_session
                SET last_seen_at = ?, idle_expires_at = ?
                WHERE session_id = ? AND revoked_at IS NULL
                """,
                Timestamp.from(lastSeenAt),
                Timestamp.from(idleExpiresAt),
                sessionId);
    }

    @Transactional
    public void revoke(String sessionId, Instant revokedAt) {
        jdbcTemplate.update(
                """
                UPDATE atlas_session
                SET revoked_at = ?
                WHERE session_id = ? AND revoked_at IS NULL
                """,
                Timestamp.from(revokedAt),
                sessionId);
    }

    private static Instant optionalInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
