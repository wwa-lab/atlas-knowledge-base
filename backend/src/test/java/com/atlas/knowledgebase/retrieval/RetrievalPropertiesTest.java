package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrievalPropertiesTest {

    @Test
    void resolvesIndependentProviderBudgets() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(
                Map.of("dify", Duration.ofSeconds(1), "confluence", Duration.ofSeconds(3)));
        properties.setProviderConcurrency(Map.of("dify", 2, "confluence", 5));
        properties.setProviderQuotaLimits(Map.of("dify", 10, "confluence", 25));
        properties.setProviderQuotaWindows(
                Map.of("dify", Duration.ofMinutes(1), "confluence", Duration.ofMinutes(5)));
        properties.setProviderEnabled(Map.of("dify", true, "confluence", false));
        properties.setProviderBackoffs(
                Map.of("dify", Duration.ofMillis(250), "confluence", Duration.ofSeconds(2)));
        properties.setProviderCircuitFailureThresholds(Map.of("dify", 3, "confluence", 5));
        properties.setProviderCircuitOpenDurations(
                Map.of("dify", Duration.ofSeconds(5), "confluence", Duration.ofSeconds(30)));

        properties.validateProfiles(Set.of("dify", "confluence"));

        assertThat(properties.timeoutFor("dify")).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.timeoutFor("confluence")).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.concurrencyFor("dify")).isEqualTo(2);
        assertThat(properties.concurrencyFor("confluence")).isEqualTo(5);
        assertThat(properties.quotaLimitFor("dify")).isEqualTo(10);
        assertThat(properties.quotaWindowFor("confluence")).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.enabled("dify")).isTrue();
        assertThat(properties.enabled("confluence")).isFalse();
        assertThat(properties.backoffFor("dify")).isEqualTo(Duration.ofMillis(250));
        assertThat(properties.circuitFailureThresholdFor("confluence")).isEqualTo(5);
        assertThat(properties.circuitOpenDurationFor("confluence"))
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsMissingOrUnsafeBudgetsForAnActiveProvider() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(Map.of("dify", Duration.ZERO));
        properties.setProviderConcurrency(Map.of("dify", 0));
        properties.setProviderQuotaLimits(Map.of("dify", 0));
        properties.setProviderQuotaWindows(Map.of("dify", Duration.ZERO));
        properties.setProviderEnabled(Map.of("dify", true));
        properties.setProviderBackoffs(Map.of("dify", Duration.ZERO));
        properties.setProviderCircuitFailureThresholds(Map.of("dify", 0));
        properties.setProviderCircuitOpenDurations(Map.of("dify", Duration.ZERO));

        assertThatThrownBy(() -> properties.validateProfiles(Set.of("dify")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dify");
    }
}
