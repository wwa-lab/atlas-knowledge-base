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
    private Map<String, Integer> providerQuotaLimits = new LinkedHashMap<>();
    private Map<String, Duration> providerQuotaWindows = new LinkedHashMap<>();
    private Map<String, Boolean> providerEnabled = new LinkedHashMap<>();
    private Map<String, Duration> providerBackoffs = new LinkedHashMap<>();
    private Map<String, Integer> providerCircuitFailureThresholds = new LinkedHashMap<>();
    private Map<String, Duration> providerCircuitOpenDurations = new LinkedHashMap<>();

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

    public Map<String, Integer> getProviderQuotaLimits() {
        return Map.copyOf(providerQuotaLimits);
    }

    public void setProviderQuotaLimits(Map<String, Integer> providerQuotaLimits) {
        this.providerQuotaLimits =
                providerQuotaLimits == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerQuotaLimits);
    }

    public Map<String, Duration> getProviderQuotaWindows() {
        return Map.copyOf(providerQuotaWindows);
    }

    public void setProviderQuotaWindows(Map<String, Duration> providerQuotaWindows) {
        this.providerQuotaWindows =
                providerQuotaWindows == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerQuotaWindows);
    }

    public void setProviderEnabled(Map<String, Boolean> providerEnabled) {
        this.providerEnabled =
                providerEnabled == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerEnabled);
    }

    public Map<String, Duration> getProviderBackoffs() {
        return Map.copyOf(providerBackoffs);
    }

    public void setProviderBackoffs(Map<String, Duration> providerBackoffs) {
        this.providerBackoffs =
                providerBackoffs == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerBackoffs);
    }

    public Map<String, Integer> getProviderCircuitFailureThresholds() {
        return Map.copyOf(providerCircuitFailureThresholds);
    }

    public void setProviderCircuitFailureThresholds(
            Map<String, Integer> providerCircuitFailureThresholds) {
        this.providerCircuitFailureThresholds =
                providerCircuitFailureThresholds == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerCircuitFailureThresholds);
    }

    public Map<String, Duration> getProviderCircuitOpenDurations() {
        return Map.copyOf(providerCircuitOpenDurations);
    }

    public void setProviderCircuitOpenDurations(
            Map<String, Duration> providerCircuitOpenDurations) {
        this.providerCircuitOpenDurations =
                providerCircuitOpenDurations == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(providerCircuitOpenDurations);
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

    public int quotaLimitFor(String providerProfile) {
        Integer limit = providerQuotaLimits.get(providerProfile);
        if (limit == null || limit < 1) {
            throw new IllegalStateException(
                    "A positive retrieval quota limit is required for provider "
                            + providerProfile);
        }
        return limit;
    }

    public Duration quotaWindowFor(String providerProfile) {
        return positiveDuration(
                providerQuotaWindows.get(providerProfile),
                "retrieval quota window",
                providerProfile);
    }

    public Duration backoffFor(String providerProfile) {
        return positiveDuration(
                providerBackoffs.get(providerProfile), "retrieval backoff", providerProfile);
    }

    public int circuitFailureThresholdFor(String providerProfile) {
        Integer threshold = providerCircuitFailureThresholds.get(providerProfile);
        if (threshold == null || threshold < 1) {
            throw new IllegalStateException(
                    "A positive retrieval circuit failure threshold is required for provider "
                            + providerProfile);
        }
        return threshold;
    }

    public Duration circuitOpenDurationFor(String providerProfile) {
        return positiveDuration(
                providerCircuitOpenDurations.get(providerProfile),
                "retrieval circuit open duration",
                providerProfile);
    }

    public void validateProfiles(Set<String> providerProfiles) {
        providerProfiles.forEach(
                provider -> {
                    timeoutFor(provider);
                    concurrencyFor(provider);
                    quotaLimitFor(provider);
                    quotaWindowFor(provider);
                    enabled(provider);
                    backoffFor(provider);
                    circuitFailureThresholdFor(provider);
                    circuitOpenDurationFor(provider);
                });
    }

    private Duration positiveDuration(Duration duration, String name, String providerProfile) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(
                    "A positive " + name + " is required for provider " + providerProfile);
        }
        return duration;
    }
}
