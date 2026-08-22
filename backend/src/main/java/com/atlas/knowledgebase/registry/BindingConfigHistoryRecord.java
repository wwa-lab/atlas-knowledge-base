package com.atlas.knowledgebase.registry;

import java.time.Instant;

/** Immutable binding configuration captured before a governance or registry update. */
public record BindingConfigHistoryRecord(
        String historyId,
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
        Instant capturedAt) {

    public static BindingConfigHistoryRecord from(BindingRecord binding, String historyId, Instant capturedAt) {
        return new BindingConfigHistoryRecord(
                historyId,
                binding.bindingId(),
                binding.logicalKbId(),
                binding.providerProfile(),
                binding.sourceIdentityJson(),
                binding.bindingRole(),
                binding.authMethod(),
                binding.health(),
                binding.enabled(),
                binding.killSwitch(),
                binding.featureFlag(),
                binding.freshnessPolicyJson(),
                binding.locatorRulesJson(),
                binding.credentialOwner(),
                binding.regionConstraintsJson(),
                binding.configVersion(),
                capturedAt);
    }
}
