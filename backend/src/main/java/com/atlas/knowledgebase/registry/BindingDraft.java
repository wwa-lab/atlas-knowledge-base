package com.atlas.knowledgebase.registry;

/** Mutable binding fields applied under an optimistic {@code config_version} check. */
public record BindingDraft(
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
        String regionConstraintsJson) {}
