package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.adapters.Retriever;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deterministic provider-to-retriever registry; duplicate active handlers fail at startup. */
@Component
public final class RetrieverRegistry {

    private final Map<String, Retriever> byProvider;

    public RetrieverRegistry(List<Retriever> retrievers) {
        Map<String, Retriever> handlers = new LinkedHashMap<>();
        for (Retriever retriever : retrievers) {
            for (String provider : retriever.providerProfiles()) {
                if (provider == null || provider.isBlank()) {
                    throw new IllegalStateException("Retriever provider profile must not be blank");
                }
                Retriever existing = handlers.putIfAbsent(provider, retriever);
                if (existing != null && existing != retriever) {
                    throw new IllegalStateException(
                            "Multiple active retrievers registered for provider: " + provider);
                }
            }
        }
        this.byProvider = Map.copyOf(handlers);
    }

    public Optional<Retriever> find(String providerProfile) {
        return Optional.ofNullable(byProvider.get(providerProfile));
    }

    public Set<String> providers() {
        return byProvider.keySet();
    }
}
