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
        properties.setProviderEnabled(Map.of("dify", true, "confluence", false));

        properties.validateProfiles(Set.of("dify", "confluence"));

        assertThat(properties.timeoutFor("dify")).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.timeoutFor("confluence")).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.concurrencyFor("dify")).isEqualTo(2);
        assertThat(properties.concurrencyFor("confluence")).isEqualTo(5);
        assertThat(properties.enabled("dify")).isTrue();
        assertThat(properties.enabled("confluence")).isFalse();
    }

    @Test
    void rejectsMissingOrUnsafeBudgetsForAnActiveProvider() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(Map.of("dify", Duration.ZERO));
        properties.setProviderConcurrency(Map.of("dify", 0));
        properties.setProviderEnabled(Map.of("dify", true));

        assertThatThrownBy(() -> properties.validateProfiles(Set.of("dify")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dify");
    }
}
