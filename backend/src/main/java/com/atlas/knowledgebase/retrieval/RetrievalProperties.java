package com.atlas.knowledgebase.retrieval;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime connector budgets. Production values remain environment-owned and provider-specific. */
@ConfigurationProperties(prefix = "atlas.retrieval")
public class RetrievalProperties {

    private Map<String, Duration> providerTimeouts = new LinkedHashMap<>();
    private Map<String, Integer> providerConcurrency = new LinkedHashMap<>();
    private Map<String, Boolean> providerEnabled = new LinkedHashMap<>();

    public Map<String, Duration> getProviderTimeouts() {
        return Map.copyOf(providerTimeouts);
    }

    public void setProviderTimeouts(Map<String, Duration> providerTimeouts) {
        this.providerTimeouts =
                providerTimeouts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providerTimeouts);
    }

    public Map<String, Integer> getProviderConcurrency() {
        return Map.copyOf(providerConcurrency);
    }

    public void setProviderConcurrency(Map<String, Integer> providerConcurrency) {
        this.providerConcurrency =
                providerConcurrency == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerConcurrency);
    }

    public Map<String, Boolean> getProviderEnabled() {
        return Map.copyOf(providerEnabled);
    }

    public void setProviderEnabled(Map<String, Boolean> providerEnabled) {
        this.providerEnabled =
                providerEnabled == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerEnabled);
    }

    public boolean enabled(String providerProfile) {
        Boolean enabled = providerEnabled.get(providerProfile);
        if (enabled == null) {
            throw new IllegalStateException(
                    "An explicit retrieval feature flag is required for provider "
                            + providerProfile);
        }
        return enabled;
    }

    public Duration timeoutFor(String providerProfile) {
        Duration timeout = providerTimeouts.get(providerProfile);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(
                    "A positive retrieval timeout is required for provider " + providerProfile);
        }
        return timeout;
    }

    public int concurrencyFor(String providerProfile) {
        Integer concurrency = providerConcurrency.get(providerProfile);
        if (concurrency == null || concurrency < 1) {
            throw new IllegalStateException(
                    "A positive retrieval concurrency limit is required for provider "
                            + providerProfile);
        }
        return concurrency;
    }

    public void validateProfiles(Set<String> providerProfiles) {
        providerProfiles.forEach(
                provider -> {
                    timeoutFor(provider);
                    concurrencyFor(provider);
                    enabled(provider);
                });
    }
}
