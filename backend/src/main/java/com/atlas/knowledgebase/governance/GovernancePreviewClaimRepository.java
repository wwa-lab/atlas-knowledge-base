package com.atlas.knowledgebase.governance;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Atomically claims an impact preview so a confirmation can be applied only once. */
@Repository
public class GovernancePreviewClaimRepository {

    private final JdbcTemplate jdbcTemplate;

    public GovernancePreviewClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void claim(
            String impactPreviewId,
            String operation,
            String bindingId,
            String userId,
            Instant claimedAt) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO governance_preview_claim (
                      impact_preview_id, operation, binding_id, user_id, claimed_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    impactPreviewId,
                    operation,
                    bindingId,
                    userId,
                    Timestamp.from(claimedAt));
        } catch (DuplicateKeyException ex) {
            throw new GovernanceConflictException(
                    "IMPACT_PREVIEW_REPLAYED",
                    "The impact preview has already been consumed; run a new preview.");
        }
    }
}
