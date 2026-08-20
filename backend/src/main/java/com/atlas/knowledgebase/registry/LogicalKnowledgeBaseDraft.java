package com.atlas.knowledgebase.registry;

/** Mutable draft fields applied under an optimistic {@code config_version} check. */
public record LogicalKnowledgeBaseDraft(
        String name,
        String description,
        String ownerUserId,
        String discoverability,
        String purpose,
        String classification,
        boolean modelEligible,
        String capability,
        String health,
        String maxStaleness,
        boolean freshnessRequired,
        String accessRequestUrl) {}
