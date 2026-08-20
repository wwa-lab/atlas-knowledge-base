package com.atlas.knowledgebase.registry;

import java.time.Instant;

/** Persistence row for {@code binding}. */
public record BindingRecord(
        String bindingId,
        String logicalKbId,
        String providerProfile,
        String sourceIdentityJson,
        String bindingRole,
        String authMethod,
        String health,
        boolean enabled,
        boolean killSwitch,
        boolean featureFlag,
        String freshnessPolicyJson,
        String locatorRulesJson,
        String credentialOwner,
        String regionConstraintsJson,
        int configVersion,
        Instant createdAt,
        Instant updatedAt) {}
