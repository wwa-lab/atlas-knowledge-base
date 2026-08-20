package com.atlas.knowledgebase.providers;

import java.time.Instant;

/** Persistence row for {@code provider_connection}. Tokens live only in {@code secret_ref}. */
public record ProviderConnectionRecord(
        String connectionId,
        String userId,
        String provider,
        String status,
        String grantedScopesJson,
        Instant expiresAt,
        Instant lastVerifiedAt,
        String secretRef,
        Instant updatedAt) {}
