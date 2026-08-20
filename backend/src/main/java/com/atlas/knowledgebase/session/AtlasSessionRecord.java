package com.atlas.knowledgebase.session;

import java.time.Instant;

/** Persistence row for {@code atlas_session}. */
public record AtlasSessionRecord(
        String sessionId,
        String userId,
        Instant issuedAt,
        Instant lastSeenAt,
        Instant absoluteExpiresAt,
        Instant idleExpiresAt,
        Instant revokedAt,
        String csrfSecret) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(idleExpiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
