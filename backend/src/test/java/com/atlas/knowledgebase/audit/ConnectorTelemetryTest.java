package com.atlas.knowledgebase.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorTelemetryTest {

    @Test
    void recordsContentFreeConnectorOutcomesAndDeidentifiedAnalytics() {
        ConnectorTelemetry telemetry = new ConnectorTelemetry();

        ConnectorTelemetry.Operation successful = telemetry.start("dify", "retrieve");
        successful.success();
        successful.success();

        ConnectorTelemetry.Operation quota = telemetry.start("dify", "retrieve");
        quota.quota(Duration.ofSeconds(4));

        ConnectorTelemetry.Operation cancelled = telemetry.start("dify", "authorize");
        cancelled.cancelled();

        telemetry.recordFeatureUse("chat", "partial", 2, Duration.ofMillis(13));

        ConnectorTelemetry.ConnectorSnapshot snapshot = telemetry.snapshot("dify");
        assertThat(snapshot.requests()).isEqualTo(3);
        assertThat(snapshot.successes()).isEqualTo(1);
        assertThat(snapshot.quotaLimited()).isEqualTo(1);
        assertThat(snapshot.inFlight()).isZero();
        assertThat(snapshot.lastRetryAfterMs()).isEqualTo(4_000);
        assertThat(snapshot.totalLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(snapshot.maxLatencyMs()).isGreaterThanOrEqualTo(0);

        assertThat(telemetry.snapshot("missing").requests()).isZero();
        assertThat(telemetry.analyticsSnapshots())
                .containsKey("connector.retrieve:success")
                .containsKey("connector.retrieve:quota")
                .containsKey("connector.authorize:cancelled")
                .containsEntry(
                        "chat:partial",
                        new ConnectorTelemetry.AnalyticsSnapshot("chat:partial", 1, 2, 13));
    }

    @Test
    void rejectsUnboundedNamesAndAnalyticsDimensions() {
        ConnectorTelemetry telemetry = new ConnectorTelemetry();

        assertThatThrownBy(() -> telemetry.start("dify/query", "retrieve"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> telemetry.recordFeatureUse("chat", "ok", 6, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> telemetry.recordFeatureUse("question body", "ok", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operationOutcomesAreExactlyOnceEvenWhenCalledAfterCancellation() {
        ConnectorTelemetry telemetry = new ConnectorTelemetry();
        ConnectorTelemetry.Operation operation = telemetry.start("git", "retrieve");

        operation.cancelled();
        operation.failure();

        assertThat(telemetry.snapshot("git").cancelled()).isEqualTo(1);
        assertThat(telemetry.snapshot("git").failures()).isZero();
        assertThat(telemetry.snapshot("git").inFlight()).isZero();
    }

    @Test
    void returnedDomainOutcomeCanReplaceTransportSuccess() {
        ConnectorTelemetry telemetry = new ConnectorTelemetry();
        ConnectorTelemetry.Operation operation = telemetry.start("dify", "retrieve");

        operation.success();
        operation.reclassify(ConnectorTelemetry.Outcome.QUOTA, Duration.ofSeconds(2));

        assertThat(telemetry.snapshot("dify").successes()).isZero();
        assertThat(telemetry.snapshot("dify").quotaLimited()).isEqualTo(1);
        assertThat(telemetry.snapshot("dify").lastRetryAfterMs()).isEqualTo(2_000);
        assertThat(telemetry.analyticsSnapshots())
                .containsEntry(
                        "connector.retrieve:quota",
                        new ConnectorTelemetry.AnalyticsSnapshot(
                                "connector.retrieve:quota", 1, 0, 0));
    }

    @Test
    void exposesResilienceBackoffAndCircuitStateWithoutProviderPayloads() {
        ConnectorTelemetry telemetry = new ConnectorTelemetry();

        telemetry.recordResilience(
                "confluence",
                ConnectorTelemetry.ProviderResilienceCause.RETRIEVAL,
                null,
                Duration.ofSeconds(1),
                3,
                true);

        ConnectorTelemetry.ConnectorSnapshot snapshot = telemetry.snapshot("confluence");
        assertThat(snapshot.failures()).isEqualTo(1);
        assertThat(snapshot.consecutiveFailures()).isEqualTo(3);
        assertThat(snapshot.backoffActive()).isTrue();
        assertThat(snapshot.circuitOpen()).isTrue();
    }
}
