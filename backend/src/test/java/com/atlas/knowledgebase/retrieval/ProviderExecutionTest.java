package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.knowledgebase.adapters.CancellationSource;
import com.atlas.knowledgebase.adapters.Retriever;
import com.atlas.knowledgebase.audit.ConnectorTelemetry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProviderExecutionTest {

    @Test
    void telemetryTracksOperationOutcomeAndLatency() throws Exception {
        RetrievalProperties properties = properties(Set.of("dify"), Duration.ofSeconds(1), 1, 10);
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever(Set.of("dify"))));
        ConnectorTelemetry telemetry = new ConnectorTelemetry();

        try (ProviderExecution execution = new ProviderExecution(properties, registry, telemetry)) {
            String result =
                    execution.await(
                            execution.<String>submit(
                                    "dify", "authorize", timeout -> "authorized"));
            assertThat(result).isEqualTo("authorized");

            ConnectorTelemetry.ConnectorSnapshot snapshot = telemetry.snapshot("dify");
            assertThat(snapshot.requests()).isEqualTo(1);
            assertThat(snapshot.successes()).isEqualTo(1);
            assertThat(snapshot.inFlight()).isZero();
            assertThat(snapshot.totalLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(telemetry.analyticsSnapshots())
                    .containsKey("connector.authorize:success");
        }
    }

    @Test
    void timeoutInterruptsWorkAndRestoresProviderCapacity() throws Exception {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(Map.of("dify", Duration.ofMillis(100)));
        properties.setProviderConcurrency(Map.of("dify", 1));
        properties.setProviderEnabled(Map.of("dify", true));
        configureResilience(properties, Duration.ofMillis(1), 3, Duration.ofSeconds(1));
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever()));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean lateCall = new AtomicBoolean();

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            ProviderExecution.TimedCall<String> blocked =
                    execution.submit(
                            "dify",
                            timeout -> {
                                started.countDown();
                                try {
                                    new CountDownLatch(1).await();
                                    lateCall.set(true);
                                    return "late";
                                } catch (InterruptedException e) {
                                    interrupted.countDown();
                                    Thread.currentThread().interrupt();
                                    return "cancelled";
                                }
                            });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> execution.await(blocked))
                    .isInstanceOf(TimeoutException.class);
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();

            ProviderExecution.TimedCall<String> next =
                    execution.submit("dify", timeout -> "next");
            assertThat(execution.await(next)).isEqualTo("next");
            assertThat(lateCall).isFalse();
        }
    }

    @Test
    void completedSecondCallSurvivesLateObservationAfterFirstCallTimesOut() throws Exception {
        RetrievalProperties properties = properties(Set.of("dify"), Duration.ofMillis(100), 2, 10);
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever(Set.of("dify"))));
        CountDownLatch blockedStarted = new CountDownLatch(1);

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            ProviderExecution.TimedCall<String> first =
                    execution.submit(
                            "dify",
                            timeout -> {
                                blockedStarted.countDown();
                                try {
                                    new CountDownLatch(1).await();
                                    return "late";
                                } catch (InterruptedException error) {
                                    Thread.currentThread().interrupt();
                                    throw new CancellationException("deadline");
                                }
                            });
            assertThat(blockedStarted.await(1, TimeUnit.SECONDS)).isTrue();
            ProviderExecution.TimedCall<String> second =
                    execution.submit("dify", timeout -> "completed-before-deadline");

            assertThatThrownBy(() -> execution.await(first)).isInstanceOf(TimeoutException.class);
            assertThat(execution.await(second)).isEqualTo("completed-before-deadline");
        }
    }

    @Test
    void completedFirstCallSurvivesLateObservationAfterSecondCallTimesOut() throws Exception {
        RetrievalProperties properties = properties(Set.of("dify"), Duration.ofMillis(100), 2, 10);
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever(Set.of("dify"))));
        CountDownLatch completed = new CountDownLatch(1);

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            ProviderExecution.TimedCall<String> first =
                    execution.submit(
                            "dify",
                            timeout -> {
                                completed.countDown();
                                return "completed-before-deadline";
                            });
            assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();
            ProviderExecution.TimedCall<String> second =
                    execution.submit(
                            "dify",
                            timeout -> {
                                try {
                                    new CountDownLatch(1).await();
                                    return "late";
                                } catch (InterruptedException error) {
                                    Thread.currentThread().interrupt();
                                    throw new CancellationException("deadline");
                                }
                            });

            assertThatThrownBy(() -> execution.await(second)).isInstanceOf(TimeoutException.class);
            assertThat(execution.await(first)).isEqualTo("completed-before-deadline");
        }
    }

    @Test
    void unobservedDeadlineStillInterruptsProviderWork() throws Exception {
        RetrievalProperties properties = properties(Set.of("dify"), Duration.ofMillis(75), 1, 10);
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever(Set.of("dify"))));
        CountDownLatch interrupted = new CountDownLatch(1);

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            ProviderExecution.TimedCall<String> call =
                    execution.submit(
                            "dify",
                            timeout -> {
                                try {
                                    new CountDownLatch(1).await();
                                    return "late";
                                } catch (InterruptedException error) {
                                    interrupted.countDown();
                                    Thread.currentThread().interrupt();
                                    throw new CancellationException("deadline");
                                }
                            });

            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> execution.await(call)).isInstanceOf(TimeoutException.class);
        }
    }

    @Test
    void userCancellationInterruptsEverySubmittedProviderOperation() throws Exception {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(Map.of("dify", Duration.ofSeconds(5)));
        properties.setProviderConcurrency(Map.of("dify", 2));
        properties.setProviderEnabled(Map.of("dify", true));
        configureResilience(properties, Duration.ofMillis(1), 3, Duration.ofSeconds(1));
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever()));
        CancellationSource cancellation = new CancellationSource();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch interrupted = new CountDownLatch(2);

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            ProviderExecution.TimedCall<String> first =
                    execution.submit("dify", cancellation, timeout -> block(started, interrupted));
            ProviderExecution.TimedCall<String> second =
                    execution.submit("dify", cancellation, timeout -> block(started, interrupted));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            cancellation.cancel();

            assertThatThrownBy(() -> execution.await(first))
                    .isInstanceOf(CancellationException.class);
            assertThatThrownBy(() -> execution.await(second))
                    .isInstanceOf(CancellationException.class);
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void providerRetryAfterControlsBackoffWithoutDispatchingMoreWork() throws Exception {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(Map.of("dify", Duration.ofSeconds(5)));
        properties.setProviderConcurrency(Map.of("dify", 1));
        properties.setProviderEnabled(Map.of("dify", true));
        configureResilience(properties, Duration.ofMillis(10), 3, Duration.ofSeconds(1));
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever()));
        AtomicBoolean dispatched = new AtomicBoolean();

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            execution.recordFailure(
                    "dify", ProviderExecution.UnavailabilityCause.QUOTA, Duration.ofSeconds(2));
            ProviderExecution.TimedCall<String> call =
                    execution.submit(
                            "dify",
                            timeout -> {
                                dispatched.set(true);
                                return "should-not-run";
                            });

            assertThatThrownBy(() -> execution.await(call))
                    .isInstanceOfSatisfying(
                            java.util.concurrent.ExecutionException.class,
                            failure ->
                                    assertThat(failure.getCause())
                                            .isInstanceOfSatisfying(
                                                    ProviderExecution.ProviderUnavailableException.class,
                                                    unavailable -> {
                                                        assertThat(unavailable.cause())
                                                                .isEqualTo(
                                                                        ProviderExecution.UnavailabilityCause
                                                                                .QUOTA);
                                                        assertThat(unavailable.retryAfter())
                                                                .isGreaterThan(
                                                                            Duration.ofSeconds(1));
                                                    }));
            assertThat(dispatched).isFalse();
        }
    }

    @Test
    void circuitOpensAtConfiguredFailureThreshold() throws Exception {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(Map.of("dify", Duration.ofSeconds(5)));
        properties.setProviderConcurrency(Map.of("dify", 1));
        properties.setProviderEnabled(Map.of("dify", true));
        configureResilience(properties, Duration.ofNanos(1), 2, Duration.ofSeconds(3));
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever()));

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            execution.recordFailure(
                    "dify", ProviderExecution.UnavailabilityCause.RETRIEVAL, null);
            execution.recordFailure(
                    "dify", ProviderExecution.UnavailabilityCause.RETRIEVAL, null);
            ProviderExecution.TimedCall<String> call =
                    execution.submit("dify", timeout -> "should-not-run");

            assertThatThrownBy(() -> execution.await(call))
                    .isInstanceOfSatisfying(
                            java.util.concurrent.ExecutionException.class,
                            failure ->
                                    assertThat(failure.getCause())
                                            .isInstanceOfSatisfying(
                                                    ProviderExecution.ProviderUnavailableException.class,
                                                    unavailable -> {
                                                        assertThat(unavailable.cause())
                                                                .isEqualTo(
                                                                        ProviderExecution.UnavailabilityCause
                                                                                .RETRIEVAL);
                                                        assertThat(unavailable.retryAfter())
                                                                .isGreaterThan(
                                                                            Duration.ofSeconds(2));
                                                    }));
        }
    }

    @Test
    void rejectedCallsDoNotCountAsNewProviderFailuresOrProlongBackoff() throws Exception {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(Map.of("dify", Duration.ofSeconds(5)));
        properties.setProviderConcurrency(Map.of("dify", 1));
        properties.setProviderEnabled(Map.of("dify", true));
        configureResilience(properties, Duration.ofMillis(30), 2, Duration.ofSeconds(1));
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever()));

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            execution.recordFailure(
                    "dify", ProviderExecution.UnavailabilityCause.TIMEOUT, null);
            ProviderExecution.TimedCall<String> rejected =
                    execution.submit("dify", timeout -> "should-not-run");
            ProviderExecution.ProviderUnavailableException unavailable =
                    (ProviderExecution.ProviderUnavailableException)
                            org.assertj.core.api.Assertions.catchThrowableOfType(
                                            () -> execution.await(rejected),
                                            java.util.concurrent.ExecutionException.class)
                                    .getCause();

            Thread.sleep(60);

            ProviderExecution.TimedCall<String> recovered =
                    execution.submit("dify", timeout -> "recovered");
            assertThat(execution.await(recovered)).isEqualTo("recovered");
        }
    }

    @Test
    void providerNeutralQuotaRejectsOnlyTheExhaustedProvider() throws Exception {
        RetrievalProperties properties =
                properties(Set.of("dify", "confluence"), Duration.ofSeconds(2), 2, 1);
        RetrieverRegistry registry =
                new RetrieverRegistry(List.of(retriever(Set.of("dify", "confluence"))));

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            assertThat(execution.await(execution.<String>submit("dify", timeout -> "first")))
                    .isEqualTo("first");

            ExecutionException rejected =
                    org.assertj.core.api.Assertions.catchThrowableOfType(
                            () ->
                                    execution.await(
                                            execution.<String>submit(
                                                    "dify", timeout -> "second")),
                            ExecutionException.class);
            assertThat(rejected.getCause())
                    .isInstanceOfSatisfying(
                            ProviderExecution.ProviderUnavailableException.class,
                            unavailable -> {
                                assertThat(unavailable.cause())
                                        .isEqualTo(ProviderExecution.UnavailabilityCause.QUOTA);
                                assertThat(unavailable.retryAfter()).isPositive();
                            });
            assertThat(
                            execution.await(
                                    execution.<String>submit(
                                            "confluence", timeout -> "isolated")))
                    .isEqualTo("isolated");
        }
    }

    @Test
    void unknownBackoffDoesNotAffectAnotherProvider() throws Exception {
        RetrievalProperties properties =
                properties(Set.of("dify", "confluence"), Duration.ofSeconds(2), 2, 10);
        properties.setProviderBackoffs(
                Map.of(
                        "dify", Duration.ofMillis(100),
                        "confluence", Duration.ofMillis(100)));
        RetrieverRegistry registry =
                new RetrieverRegistry(List.of(retriever(Set.of("dify", "confluence"))));

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            execution.recordFailure(
                    "dify", ProviderExecution.UnavailabilityCause.UNKNOWN, null);

            assertUnavailableCause(
                    execution, "dify", ProviderExecution.UnavailabilityCause.UNKNOWN);
            assertThat(
                            execution.await(
                                    execution.<String>submit(
                                            "confluence", timeout -> "isolated")))
                    .isEqualTo("isolated");
        }
    }

    @Test
    void concurrentFailuresOpenCircuitWithoutLostUpdatesAndRecoveryResetsIt() throws Exception {
        RetrievalProperties properties = properties(Set.of("dify"), Duration.ofSeconds(2), 8, 20);
        properties.setProviderBackoffs(Map.of("dify", Duration.ofNanos(1)));
        properties.setProviderCircuitFailureThresholds(Map.of("dify", 8));
        properties.setProviderCircuitOpenDurations(Map.of("dify", Duration.ofMillis(120)));
        RetrieverRegistry registry = new RetrieverRegistry(List.of(retriever(Set.of("dify"))));

        try (ProviderExecution execution = new ProviderExecution(properties, registry)) {
            List<Thread> recorders =
                    IntStream.range(0, 8)
                            .mapToObj(
                                    ignored ->
                                            Thread.ofVirtual()
                                                    .start(
                                                            () ->
                                                                    execution.recordFailure(
                                                                            "dify",
                                                                            ProviderExecution
                                                                                    .UnavailabilityCause
                                                                                    .RETRIEVAL,
                                                                            null)))
                            .toList();
            for (Thread recorder : recorders) {
                recorder.join();
            }

            assertUnavailableCause(
                    execution, "dify", ProviderExecution.UnavailabilityCause.RETRIEVAL);
            Thread.sleep(180);
            assertThat(execution.await(execution.<String>submit("dify", timeout -> "recovered")))
                    .isEqualTo("recovered");
            execution.recordSuccess("dify");
            properties.setProviderBackoffs(Map.of("dify", Duration.ofMillis(100)));
            execution.recordFailure(
                    "dify", ProviderExecution.UnavailabilityCause.TIMEOUT, null);
            assertUnavailableCause(
                    execution, "dify", ProviderExecution.UnavailabilityCause.TIMEOUT);
        }
    }

    private static String block(CountDownLatch started, CountDownLatch interrupted) {
        started.countDown();
        try {
            new CountDownLatch(1).await();
            return "late";
        } catch (InterruptedException e) {
            interrupted.countDown();
            Thread.currentThread().interrupt();
            throw new CancellationException("cancelled");
        }
    }

    private static void configureResilience(
            RetrievalProperties properties,
            Duration backoff,
            int failureThreshold,
            Duration openDuration) {
        properties.setProviderBackoffs(Map.of("dify", backoff));
        properties.setProviderCircuitFailureThresholds(Map.of("dify", failureThreshold));
        properties.setProviderCircuitOpenDurations(Map.of("dify", openDuration));
        properties.setProviderQuotaLimits(Map.of("dify", 100));
        properties.setProviderQuotaWindows(Map.of("dify", Duration.ofMinutes(1)));
    }

    private static RetrievalProperties properties(
            Set<String> providers, Duration timeout, int concurrency, int quotaLimit) {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProviderTimeouts(
                providers.stream().collect(java.util.stream.Collectors.toMap(p -> p, p -> timeout)));
        properties.setProviderConcurrency(
                providers.stream()
                        .collect(java.util.stream.Collectors.toMap(p -> p, p -> concurrency)));
        properties.setProviderQuotaLimits(
                providers.stream()
                        .collect(java.util.stream.Collectors.toMap(p -> p, p -> quotaLimit)));
        properties.setProviderQuotaWindows(
                providers.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        p -> p, p -> Duration.ofSeconds(1))));
        properties.setProviderEnabled(
                providers.stream().collect(java.util.stream.Collectors.toMap(p -> p, p -> true)));
        properties.setProviderBackoffs(
                providers.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        p -> p, p -> Duration.ofMillis(10))));
        properties.setProviderCircuitFailureThresholds(
                providers.stream().collect(java.util.stream.Collectors.toMap(p -> p, p -> 3)));
        properties.setProviderCircuitOpenDurations(
                providers.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        p -> p, p -> Duration.ofSeconds(1))));
        return properties;
    }

    private static void assertUnavailableCause(
            ProviderExecution execution,
            String provider,
            ProviderExecution.UnavailabilityCause expected) {
        ExecutionException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> execution.await(execution.submit(provider, timeout -> "rejected")),
                        ExecutionException.class);
        assertThat(failure.getCause())
                .isInstanceOfSatisfying(
                        ProviderExecution.ProviderUnavailableException.class,
                        unavailable -> assertThat(unavailable.cause()).isEqualTo(expected));
    }

    private static Retriever retriever() {
        return retriever(Set.of("dify"));
    }

    private static Retriever retriever(Set<String> providers) {
        return new Retriever() {
            @Override
            public Set<String> providerProfiles() {
                return providers;
            }

            @Override
            public AuthorizationResult authorize(AuthorizationRequest request) {
                return AuthorizationResult.authorized();
            }

            @Override
            public Result retrieve(Request request) {
                return Result.success(List.of(), List.of());
            }
        };
    }
}
