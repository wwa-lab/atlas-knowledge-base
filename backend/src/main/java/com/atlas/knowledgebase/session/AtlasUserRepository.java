package com.atlas.knowledgebase.session;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AtlasUserRepository {

    private static final RowMapper<AtlasUserRecord> ROW_MAPPER =
            (rs, rowNum) ->
                    new AtlasUserRecord(
                            rs.getString("user_id"),
                            rs.getString("sso_subject"),
                            rs.getString("display_name"),
                            rs.getString("email"),
                            rs.getString("roles"),
                            rs.getInt("model_entitled") == 1,
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public AtlasUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AtlasUserRecord insert(AtlasUserRecord user) {
        Instant now = user.createdAt() != null ? user.createdAt() : Instant.now();
        Instant updated = user.updatedAt() != null ? user.updatedAt() : now;
        jdbcTemplate.update(
                """
                INSERT INTO atlas_user
                  (user_id, sso_subject, display_name, email, roles, model_entitled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                user.userId(),
                user.ssoSubject(),
                user.displayName(),
                user.email(),
                user.rolesJson() != null ? user.rolesJson() : "[]",
                user.modelEntitled() ? 1 : 0,
                Timestamp.from(now),
                Timestamp.from(updated));
        return findById(user.userId()).orElseThrow();
    }

    public Optional<AtlasUserRecord> findById(String userId) {
        return jdbcTemplate
                .query("SELECT * FROM atlas_user WHERE user_id = ?", ROW_MAPPER, userId)
                .stream()
                .findFirst();
    }

    public Optional<AtlasUserRecord> findBySsoSubject(String ssoSubject) {
        return jdbcTemplate
                .query("SELECT * FROM atlas_user WHERE sso_subject = ?", ROW_MAPPER, ssoSubject)
                .stream()
                .findFirst();
    }

    @Transactional
    public AtlasUserRecord refreshIdentity(
            String userId, String displayName, String email, Instant updatedAt) {
        jdbcTemplate.update(
                """
                UPDATE atlas_user
                SET display_name = ?, email = ?, updated_at = ?
                WHERE user_id = ?
                """,
                displayName,
                email,
                Timestamp.from(updatedAt),
                userId);
        return findById(userId).orElseThrow();
    }
}
