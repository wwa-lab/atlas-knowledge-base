package com.atlas.knowledgebase.providers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProviderConnectionRepository {

    private static final RowMapper<ProviderConnectionRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new ProviderConnectionRecord(
                            rs.getString("connection_id"),
                            rs.getString("user_id"),
                            rs.getString("provider"),
                            rs.getString("status"),
                            rs.getString("granted_scopes"),
                            optionalInstant(rs.getTimestamp("expires_at")),
                            optionalInstant(rs.getTimestamp("last_verified_at")),
                            rs.getString("secret_ref"),
                            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ProviderConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProviderConnectionRecord> findByUserAndProvider(String userId, String provider) {
        return jdbcTemplate
                .query(
                        """
                        SELECT * FROM provider_connection
                        WHERE user_id = ? AND provider = ?
                        """,
                        ROW_MAPPER,
                        userId,
                        provider)
                .stream()
                .findFirst();
    }

    @Transactional
    public ProviderConnectionRecord insert(ProviderConnectionRecord row) {
        jdbcTemplate.update(
                """
                INSERT INTO provider_connection (
                  connection_id, user_id, provider, status, granted_scopes, expires_at,
                  last_verified_at, secret_ref, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.connectionId(),
                row.userId(),
                row.provider(),
                row.status(),
                row.grantedScopesJson(),
                timestamp(row.expiresAt()),
                timestamp(row.lastVerifiedAt()),
                row.secretRef(),
                Timestamp.from(row.updatedAt()));
        return findByUserAndProvider(row.userId(), row.provider()).orElseThrow();
    }

    @Transactional
    public ProviderConnectionRecord update(ProviderConnectionRecord row) {
        jdbcTemplate.update(
                """
                UPDATE provider_connection
                SET status = ?, granted_scopes = ?, expires_at = ?, last_verified_at = ?,
                    secret_ref = ?, updated_at = ?
                WHERE connection_id = ?
                """,
                row.status(),
                row.grantedScopesJson(),
                timestamp(row.expiresAt()),
                timestamp(row.lastVerifiedAt()),
                row.secretRef(),
                Timestamp.from(row.updatedAt()),
                row.connectionId());
        return findByUserAndProvider(row.userId(), row.provider()).orElseThrow();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant optionalInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
