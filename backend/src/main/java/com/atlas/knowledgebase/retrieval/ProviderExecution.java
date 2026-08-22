package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.adapters.CancellationSource;
import com.atlas.knowledgebase.adapters.CancellationToken;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Executes adapter work under provider-specific deadlines and concurrency limits. */
@Component
public final class ProviderExecution implements AutoCloseable {

    private final RetrievalProperties properties;
    private final Map<String, Semaphore> providerLimits;
    private final Map<String, AtomicReference<ProviderState>> providerStates;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public ProviderExecution(RetrievalProperties properties, RetrieverRegistry retrievers) {
        this.properties = properties;
        properties.validateProfiles(retrievers.providers());
        Map<String, Semaphore> limits = new LinkedHashMap<>();
        Map<String, AtomicReference<ProviderState>> states = new LinkedHashMap<>();
        retrievers.providers()
                .forEach(
                        provider -> {
                            limits.put(
                                    provider,
                                    new Semaphore(properties.concurrencyFor(provider)));
                            states.put(provider, new AtomicReference<>(ProviderState.available()));
                        });
        this.providerLimits = Map.copyOf(limits);
        this.providerStates = Map.copyOf(states);
    }

    public <T> TimedCall<T> submit(
            String providerProfile, Function<Duration, T> operation) {
        return submit(providerProfile, new CancellationSource(), operation);
    }

    public <T> TimedCall<T> submit(
            String providerProfile,
            CancellationToken cancellation,
            Function<Duration, T> operation) {
        Duration timeout = properties.timeoutFor(providerProfile);
        Semaphore limit = providerLimits.get(providerProfile);
        if (limit == null) {
            throw new IllegalStateException(
                    "No retrieval concurrency limit configured for provider " + providerProfile);
        }
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        FutureTask<T> future =
                new FutureTask<>(
                        () -> {
                            cancellation.throwIfCancelled();
                            requireAvailable(providerProfile);
                            boolean acquired = false;
                            try {
                                long remaining = deadlineNanos - System.nanoTime();
                                if (remaining <= 0
                                        || !limit.tryAcquire(remaining, TimeUnit.NANOSECONDS)) {
                                    throw new TimeoutException(
                                            "Provider concurrency budget expired before dispatch");
                                }
                                acquired = true;
                                cancellation.throwIfCancelled();
                                requireAvailable(providerProfile);
                                return operation.apply(timeout);
                            } finally {
                                if (acquired) {
                                    limit.release();
                                }
                            }
                        });
        CancellationToken.Registration registration =
                cancellation.onCancel(() -> future.cancel(true));
        workers.execute(future);
        return new TimedCall<>(future, deadlineNanos, registration);
    }

    /** Records a provider availability failure and applies its configured backoff/circuit policy. */
    public void recordFailure(String providerProfile, Duration retryAfter) {
        AtomicReference<ProviderState> reference = stateFor(providerProfile);
        long now = System.nanoTime();
        long providerBackoff =
                retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()
                        ? 0
                        : retryAfter.toNanos();
        long configuredBackoff = properties.backoffFor(providerProfile).toNanos();
        int threshold = properties.circuitFailureThresholdFor(providerProfile);
        long circuitOpen = properties.circuitOpenDurationFor(providerProfile).toNanos();
        reference.updateAndGet(
                current -> {
                    if (current.unavailableUntilNanos() > now) {
                        return current;
                    }
                    int failures = Math.min(threshold, current.consecutiveFailures() + 1);
                    long delay = Math.max(configuredBackoff, providerBackoff);
                    if (failures >= threshold) {
                        delay = Math.max(delay, circuitOpen);
                    }
                    long unavailableUntil =
                            Math.max(current.unavailableUntilNanos(), saturatedAdd(now, delay));
                    return new ProviderState(failures, unavailableUntil);
                });
    }

    /** A successful provider result closes the failure counter without shortening active backoff. */
    public void recordSuccess(String providerProfile) {
        AtomicReference<ProviderState> reference = stateFor(providerProfile);
        long now = System.nanoTime();
        reference.updateAndGet(
                current ->
                        current.unavailableUntilNanos() > now
                                ? current
                                : ProviderState.available());
    }

    public <T> T await(TimedCall<T> call)
            throws InterruptedException, ExecutionException, TimeoutException {
        long remaining = call.deadlineNanos() - System.nanoTime();
        if (remaining <= 0) {
            call.future().cancel(true);
            throw new TimeoutException("Provider operation exceeded its deadline");
        }
        try {
            return call.future().get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            call.future().cancel(true);
            throw e;
        } catch (InterruptedException e) {
            call.future().cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            call.registration().close();
        }
    }

    @Override
    @PreDestroy
    public void close() {
        workers.shutdownNow();
    }

    public record TimedCall<T>(
            Future<T> future,
            long deadlineNanos,
            CancellationToken.Registration registration) {}

    public static final class ProviderUnavailableException extends RuntimeException {
        private final Duration retryAfter;

        ProviderUnavailableException(String providerProfile, Duration retryAfter) {
            super("Provider " + providerProfile + " is temporarily unavailable");
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    private void requireAvailable(String providerProfile) {
        ProviderState state = stateFor(providerProfile).get();
        long remaining = state.unavailableUntilNanos() - System.nanoTime();
        if (remaining > 0) {
            throw new ProviderUnavailableException(
                    providerProfile, Duration.ofNanos(Math.max(1, remaining)));
        }
    }

    private AtomicReference<ProviderState> stateFor(String providerProfile) {
        AtomicReference<ProviderState> state = providerStates.get(providerProfile);
        if (state == null) {
            throw new IllegalStateException(
                    "No retrieval resilience policy configured for provider " + providerProfile);
        }
        return state;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private record ProviderState(int consecutiveFailures, long unavailableUntilNanos) {
        private static ProviderState available() {
            return new ProviderState(0, 0);
        }
    }
}
