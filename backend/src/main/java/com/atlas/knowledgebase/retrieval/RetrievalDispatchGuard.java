package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.registry.BindingRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compares persisted ask-time bindings with authoritative dispatch-time registry state. */
final class RetrievalDispatchGuard {

    private RetrievalDispatchGuard() {}

    static List<BindingRecord> mergeBindings(
            List<BindingRecord> snapshotBindings, List<BindingRecord> authoritativeBindings) {
        LinkedHashMap<String, BindingRecord> merged = new LinkedHashMap<>();
        snapshotBindings.forEach(binding -> merged.put(binding.bindingId(), binding));
        authoritativeBindings.forEach(binding -> merged.put(binding.bindingId(), binding));
        return List.copyOf(merged.values());
    }

    static boolean sameBindings(
            List<BindingRecord> snapshotBindings, List<BindingRecord> authoritativeBindings) {
        if (snapshotBindings.size() != authoritativeBindings.size()) {
            return false;
        }
        Map<String, BindingRecord> authoritativeById = new LinkedHashMap<>();
        authoritativeBindings.forEach(
                binding -> authoritativeById.put(binding.bindingId(), binding));
        return snapshotBindings.stream()
                .allMatch(
                        snapshot ->
                                sameBinding(
                                        snapshot,
                                        authoritativeById.get(snapshot.bindingId())));
    }

    private static boolean sameBinding(BindingRecord snapshot, BindingRecord authoritative) {
        return authoritative != null
                && Objects.equals(snapshot.bindingId(), authoritative.bindingId())
                && Objects.equals(snapshot.logicalKbId(), authoritative.logicalKbId())
                && Objects.equals(snapshot.providerProfile(), authoritative.providerProfile())
                && Objects.equals(snapshot.sourceIdentityJson(), authoritative.sourceIdentityJson())
                && Objects.equals(snapshot.bindingRole(), authoritative.bindingRole())
                && Objects.equals(snapshot.authMethod(), authoritative.authMethod())
                && Objects.equals(snapshot.health(), authoritative.health())
                && snapshot.enabled() == authoritative.enabled()
                && snapshot.killSwitch() == authoritative.killSwitch()
                && snapshot.featureFlag() == authoritative.featureFlag()
                && Objects.equals(
                        snapshot.freshnessPolicyJson(), authoritative.freshnessPolicyJson())
                && Objects.equals(snapshot.locatorRulesJson(), authoritative.locatorRulesJson())
                && Objects.equals(snapshot.credentialOwner(), authoritative.credentialOwner())
                && Objects.equals(
                        snapshot.regionConstraintsJson(), authoritative.regionConstraintsJson())
                && snapshot.configVersion() == authoritative.configVersion();
    }
}
