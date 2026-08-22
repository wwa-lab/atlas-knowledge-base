package com.atlas.knowledgebase.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Content-free operational telemetry for connector work.
 *
 * <p>Only connector and operation names, outcome counters, bounded timing values, and resilience
 * state are retained. No user identifiers, questions, source identities, retrieved bodies, or
 * provider responses are accepted by this component.
 */
@Component
public final class ConnectorTelemetry {

    private static final String SAFE_NAME = "[a-zA-Z0-9_.-]{1,64}";

    private final ConcurrentHashMap<String, ConnectorState> connectors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AnalyticsState> analytics = new ConcurrentHashMap<>();

    public Operation start(String connector, String operation) {
        String safeConnector = requireName(connector, "connector");
        String safeOperation = requireName(operation, "operation");
        ConnectorState state = connectors.computeIfAbsent(safeConnector, ignored -> new ConnectorState());
        state.requests.incrementAndGet();
        state.inFlight.incrementAndGet();
        state.lastOccurredAt.set(Instant.now());
        return new Operation(this, safeConnector, safeOperation, System.nanoTime());
    }

    /** Records a de-identified product feature event without accepting content or identity data. */
    public void recordFeatureUse(
            String feature, String outcome, int knowledgeBaseCount, Duration latency) {
        String safeFeature = requireName(feature, "feature");
        String safeOutcome = requireName(outcome, "outcome");
        if (knowledgeBaseCount < 0 || knowledgeBaseCount > 5) {
            throw new IllegalArgumentException("knowledgeBaseCount must be between 0 and 5");
        }
        AnalyticsState state =
                analytics.computeIfAbsent(
                        safeFeature + ":" + safeOutcome, ignored -> new AnalyticsState());
        state.count.incrementAndGet();
        state.knowledgeBaseCountTotal.addAndGet(knowledgeBaseCount);
        if (latency != null && !latency.isNegative()) {
            state.latencyMsTotal.addAndGet(toMillis(latency));
        }
    }

    public ConnectorSnapshot snapshot(String connector) {
        String safeConnector = requireName(connector, "connector");
        ConnectorState state = connectors.get(safeConnector);
        return state == null ? ConnectorSnapshot.empty(safeConnector) : state.snapshot(safeConnector);
    }

    public Map<String, ConnectorSnapshot> snapshots() {
        Map<String, ConnectorSnapshot> result = new LinkedHashMap<>();
        connectors.keySet().stream()
                .sorted()
                .forEach(connector -> result.put(connector, snapshot(connector)));
        return Map.copyOf(result);
    }

    public Map<String, AnalyticsSnapshot> analyticsSnapshots() {
        Map<String, AnalyticsSnapshot> result = new LinkedHashMap<>();
        analytics.keySet().stream()
                .sorted()
                .forEach(
                        key -> {
                            AnalyticsState state = analytics.get(key);
                            if (state != null) {
                                result.put(key, state.snapshot(key));
                            }
                        });
        return Map.copyOf(result);
    }

    public void recordResilience(
            String connector,
            ProviderResilienceCause cause,
            Duration retryAfter,
            Duration backoff,
            int consecutiveFailures,
            boolean circuitOpen) {
        String safeConnector = requireName(connector, "connector");
        if (cause == null) {
            throw new IllegalArgumentException("resilience cause is required");
        }
        ConnectorState state =
                connectors.computeIfAbsent(safeConnector, ignored -> new ConnectorState());
        switch (cause) {
            case QUOTA -> state.quotaLimited.incrementAndGet();
            case TIMEOUT -> state.timeouts.incrementAndGet();
            case RETRIEVAL, UNKNOWN -> state.failures.incrementAndGet();
        }
        state.consecutiveFailures.set(Math.max(0, consecutiveFailures));
        state.circuitOpen.set(circuitOpen);
        long retryAfterMs = retryAfter == null || retryAfter.isNegative() ? 0 : toMillis(retryAfter);
        state.lastRetryAfterMs.set(retryAfterMs);
        state.backoffUntilNanos.set(
                backoff == null || backoff.isNegative() || backoff.isZero()
                        ? 0
                        : saturatedAdd(System.nanoTime(), backoff.toNanos()));
        state.lastOccurredAt.set(Instant.now());
    }

    public void recordResilienceClosed(String connector) {
        String safeConnector = requireName(connector, "connector");
        ConnectorState state =
                connectors.computeIfAbsent(safeConnector, ignored -> new ConnectorState());
        state.consecutiveFailures.set(0);
        state.circuitOpen.set(false);
        state.backoffUntilNanos.set(0);
        state.lastOccurredAt.set(Instant.now());
    }

    private void finish(
            String connector,
            String operation,
            long startedAtNanos,
            Outcome outcome,
            Duration retryAfter) {
        ConnectorState state = connectors.computeIfAbsent(connector, ignored -> new ConnectorState());
        state.inFlight.updateAndGet(value -> Math.max(0, value - 1));
        long latencyMs = elapsedMillis(startedAtNanos);
        state.totalLatencyMs.addAndGet(latencyMs);
        state.maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
        state.lastOccurredAt.set(Instant.now());
        switch (outcome) {
            case SUCCESS -> state.successes.incrementAndGet();
            case FAILURE -> state.failures.incrementAndGet();
            case TIMEOUT -> state.timeouts.incrementAndGet();
            case QUOTA -> {
                state.quotaLimited.incrementAndGet();
                updateRetryAfter(state, retryAfter);
            }
            case CANCELLED -> state.cancelled.incrementAndGet();
        }
        AnalyticsState analyticsState =
                analytics.computeIfAbsent(
                        "connector." + operation + ":" + outcome.name().toLowerCase(),
                        ignored -> new AnalyticsState());
        analyticsState.count.incrementAndGet();
        analyticsState.latencyMsTotal.addAndGet(latencyMs);
    }

    private static void updateRetryAfter(ConnectorState state, Duration retryAfter) {
        if (retryAfter != null && !retryAfter.isNegative()) {
            state.lastRetryAfterMs.set(toMillis(retryAfter));
        }
    }

    private static String requireName(String value, String field) {
        if (value == null || !value.matches(SAFE_NAME)) {
            throw new IllegalArgumentException(field + " must be a bounded telemetry name");
        }
        return value;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static long toMillis(Duration duration) {
        try {
            return Math.max(0, duration.toMillis());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public enum Outcome {
        SUCCESS,
        FAILURE,
        TIMEOUT,
        QUOTA,
        CANCELLED
    }

    public enum ProviderResilienceCause {
        QUOTA,
        TIMEOUT,
        RETRIEVAL,
        UNKNOWN
    }

    public final class Operation {
        private final ConnectorTelemetry owner;
        private final String connector;
        private final String operation;
        private final long startedAtNanos;
        private final AtomicBoolean finished = new AtomicBoolean();

        private Operation(
                ConnectorTelemetry owner, String connector, String operation, long startedAtNanos) {
            this.owner = owner;
            this.connector = connector;
            this.operation = operation;
            this.startedAtNanos = startedAtNanos;
        }

        public void success() {
            finish(Outcome.SUCCESS, null);
        }

        public void failure() {
            finish(Outcome.FAILURE, null);
        }

        public void timeout() {
            finish(Outcome.TIMEOUT, null);
        }

        public void quota(Duration retryAfter) {
            finish(Outcome.QUOTA, retryAfter);
        }

        public void cancelled() {
            finish(Outcome.CANCELLED, null);
        }

        public long elapsedMillis() {
            return ConnectorTelemetry.elapsedMillis(startedAtNanos);
        }

        private void finish(Outcome outcome, Duration retryAfter) {
            if (finished.compareAndSet(false, true)) {
                owner.finish(connector, operation, startedAtNanos, outcome, retryAfter);
            }
        }
    }

    public record ConnectorSnapshot(
            String connector,
            long requests,
            long successes,
            long failures,
            long timeouts,
            long quotaLimited,
            long cancelled,
            long inFlight,
            long totalLatencyMs,
            long maxLatencyMs,
            long lastRetryAfterMs,
            int consecutiveFailures,
            boolean backoffActive,
            boolean circuitOpen,
            Instant lastOccurredAt) {

        public long averageLatencyMs() {
            return requests == 0 ? 0 : totalLatencyMs / requests;
        }

        private static ConnectorSnapshot empty(String connector) {
            return new ConnectorSnapshot(
                    connector, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, null);
        }
    }

    public record AnalyticsSnapshot(
            String key, long count, long knowledgeBaseCountTotal, long latencyMsTotal) {}

    private static final class ConnectorState {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong timeouts = new AtomicLong();
        private final AtomicLong quotaLimited = new AtomicLong();
        private final AtomicLong cancelled = new AtomicLong();
        private final AtomicLong inFlight = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();
        private final AtomicLong maxLatencyMs = new AtomicLong();
        private final AtomicLong lastRetryAfterMs = new AtomicLong();
        private final AtomicLong consecutiveFailures = new AtomicLong();
        private final AtomicLong backoffUntilNanos = new AtomicLong();
        private final AtomicBoolean circuitOpen = new AtomicBoolean();
        private final AtomicReference<Instant> lastOccurredAt = new AtomicReference<>();

        private ConnectorSnapshot snapshot(String connector) {
            long backoffUntil = backoffUntilNanos.get();
            return new ConnectorSnapshot(
                    connector,
                    requests.get(),
                    successes.get(),
                    failures.get(),
                    timeouts.get(),
                    quotaLimited.get(),
                    cancelled.get(),
                    inFlight.get(),
                    totalLatencyMs.get(),
                    maxLatencyMs.get(),
                    lastRetryAfterMs.get(),
                    (int) Math.min(Integer.MAX_VALUE, consecutiveFailures.get()),
                    backoffUntil > System.nanoTime(),
                    circuitOpen.get() && backoffUntil > System.nanoTime(),
                    lastOccurredAt.get());
        }
    }

    private static final class AnalyticsState {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong knowledgeBaseCountTotal = new AtomicLong();
        private final AtomicLong latencyMsTotal = new AtomicLong();

        private AnalyticsSnapshot snapshot(String key) {
            return new AnalyticsSnapshot(
                    key,
                    count.get(),
                    knowledgeBaseCountTotal.get(),
                    latencyMsTotal.get());
        }
    }
}
