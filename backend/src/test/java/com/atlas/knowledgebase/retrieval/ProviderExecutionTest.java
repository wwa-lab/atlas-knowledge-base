package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.knowledgebase.adapters.CancellationSource;
import com.atlas.knowledgebase.adapters.Retriever;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ProviderExecutionTest {

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
            execution.recordFailure("dify", Duration.ofSeconds(2));
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
                                                    unavailable ->
                                                            assertThat(unavailable.retryAfter())
                                                                    .isGreaterThan(
                                                                            Duration.ofSeconds(1))));
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
            execution.recordFailure("dify", null);
            execution.recordFailure("dify", null);
            ProviderExecution.TimedCall<String> call =
                    execution.submit("dify", timeout -> "should-not-run");

            assertThatThrownBy(() -> execution.await(call))
                    .isInstanceOfSatisfying(
                            java.util.concurrent.ExecutionException.class,
                            failure ->
                                    assertThat(failure.getCause())
                                            .isInstanceOfSatisfying(
                                                    ProviderExecution.ProviderUnavailableException.class,
                                                    unavailable ->
                                                            assertThat(unavailable.retryAfter())
                                                                    .isGreaterThan(
                                                                            Duration.ofSeconds(2))));
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
            execution.recordFailure("dify", null);
            ProviderExecution.TimedCall<String> rejected =
                    execution.submit("dify", timeout -> "should-not-run");
            ProviderExecution.ProviderUnavailableException unavailable =
                    (ProviderExecution.ProviderUnavailableException)
                            org.assertj.core.api.Assertions.catchThrowableOfType(
                                            () -> execution.await(rejected),
                                            java.util.concurrent.ExecutionException.class)
                                    .getCause();

            execution.recordFailure("dify", unavailable.retryAfter());
            Thread.sleep(60);

            ProviderExecution.TimedCall<String> recovered =
                    execution.submit("dify", timeout -> "recovered");
            assertThat(execution.await(recovered)).isEqualTo("recovered");
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
    }

    private static Retriever retriever() {
        return new Retriever() {
            @Override
            public Set<String> providerProfiles() {
                return Set.of("dify");
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
