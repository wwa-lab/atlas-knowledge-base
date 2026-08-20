package com.atlas.knowledgebase.registry;

import java.time.Instant;

/** Persistence row for {@code logical_knowledge_base}. */
public record LogicalKnowledgeBaseRecord(
        String logicalKbId,
        String name,
        String description,
        String ownerUserId,
        String discoverability,
        String purpose,
        String classification,
        boolean modelEligible,
        String capability,
        String lifecycle,
        String health,
        int configVersion,
        String maxStaleness,
        boolean freshnessRequired,
        String accessRequestUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt) {}
