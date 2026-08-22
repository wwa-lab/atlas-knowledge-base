package com.atlas.knowledgebase.evidence;

import com.atlas.knowledgebase.adapters.EvidenceResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deterministic provider registry; duplicate active evidence adapters fail at startup. */
@Component
public final class EvidenceResolverRegistry {

    private final Map<String, EvidenceResolver> byProvider;

    public EvidenceResolverRegistry(List<EvidenceResolver> resolvers) {
        Map<String, EvidenceResolver> handlers = new LinkedHashMap<>();
        for (EvidenceResolver resolver : resolvers) {
            for (String provider : resolver.providerProfiles()) {
                if (provider == null || provider.isBlank()) {
                    throw new IllegalStateException("Evidence resolver provider profile must not be blank");
                }
                EvidenceResolver existing = handlers.putIfAbsent(provider, resolver);
                if (existing != null && existing != resolver) {
                    throw new IllegalStateException(
                            "Multiple active evidence resolvers registered for provider: " + provider);
                }
            }
        }
        this.byProvider = Map.copyOf(handlers);
    }

    public Optional<EvidenceResolver> find(String providerProfile) {
        return Optional.ofNullable(byProvider.get(providerProfile));
    }

    public Set<String> providers() {
        return byProvider.keySet();
    }
}
