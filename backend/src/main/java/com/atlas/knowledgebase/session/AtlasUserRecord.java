package com.atlas.knowledgebase.session;

import java.time.Instant;

/** Persistence row for {@code atlas_user}. */
public record AtlasUserRecord(
        String userId,
        String ssoSubject,
        String displayName,
        String email,
        String rolesJson,
        boolean modelEntitled,
        Instant createdAt,
        Instant updatedAt) {}
