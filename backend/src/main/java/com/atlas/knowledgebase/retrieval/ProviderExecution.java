package com.atlas.knowledgebase.retrieval;

import com.atlas.knowledgebase.adapters.CancellationSource;
import com.atlas.knowledgebase.adapters.CancellationToken;
import com.atlas.knowledgebase.audit.ConnectorTelemetry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Executes adapter work under independent provider deadlines and resilience budgets. */
@Component
public final class ProviderExecution implements AutoCloseable {

    private final RetrievalProperties properties;
    private final Map<String, Semaphore> providerLimits;
    private final Map<String, AtomicReference<QuotaWindow>> providerQuotas;
    private final Map<String, AtomicReference<ProviderState>> providerStates;
    private final ConnectorTelemetry telemetry;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService deadlines =
            Executors.newSingleThreadScheduledExecutor(
                    runnable ->
                            Thread.ofPlatform()
                                    .daemon()
                                    .name("retrieval-deadline")
                                    .unstarted(runnable));

    public ProviderExecution(RetrievalProperties properties, RetrieverRegistry retrievers) {
        this(properties, retrievers, new ConnectorTelemetry());
    }

    @Autowired
    public ProviderExecution(
            RetrievalProperties properties,
            RetrieverRegistry retrievers,
            ConnectorTelemetry telemetry) {
        this.properties = properties;
        this.telemetry = telemetry;
        properties.validateProfiles(retrievers.providers());
        Map<String, Semaphore> limits = new LinkedHashMap<>();
        Map<String, AtomicReference<QuotaWindow>> quotas = new LinkedHashMap<>();
        Map<String, AtomicReference<ProviderState>> states = new LinkedHashMap<>();
        retrievers.providers()
                .forEach(
                        provider -> {
                            limits.put(
                                    provider,
                                    new Semaphore(properties.concurrencyFor(provider)));
                            quotas.put(
                                    provider,
                                    new AtomicReference<>(QuotaWindow.initial(System.nanoTime())));
                            states.put(provider, new AtomicReference<>(ProviderState.available()));
                        });
        this.providerLimits = Map.copyOf(limits);
        this.providerQuotas = Map.copyOf(quotas);
        this.providerStates = Map.copyOf(states);
    }

    public <T> TimedCall<T> submit(String providerProfile, Function<Duration, T> operation) {
        return submit(providerProfile, "retrieve", new CancellationSource(), operation);
    }

    public <T> TimedCall<T> submit(
            String providerProfile,
            CancellationToken cancellation,
            Function<Duration, T> operation) {
        return submit(providerProfile, "retrieve", cancellation, operation);
    }

    public <T> TimedCall<T> submit(
            String providerProfile, String operation, Function<Duration, T> work) {
        return submit(providerProfile, operation, new CancellationSource(), work);
    }

    public <T> TimedCall<T> submit(
            String providerProfile,
            String operation,
            CancellationToken cancellation,
            Function<Duration, T> work) {
        Duration timeout = properties.timeoutFor(providerProfile);
        Semaphore limit = providerLimits.get(providerProfile);
        if (limit == null) {
            throw new IllegalStateException(
                    "No retrieval concurrency limit configured for provider " + providerProfile);
        }
        long deadlineNanos = saturatedAdd(System.nanoTime(), timeout.toNanos());
        ConnectorTelemetry.Operation telemetryOperation = telemetry.start(providerProfile, operation);
        CallState<T> state = new CallState<>(deadlineNanos, telemetryOperation);
        FutureTask<Void> worker =
                new FutureTask<>(
                        () -> {
                            try {
                                cancellation.throwIfCancelled();
                                requireAvailable(providerProfile);
                                boolean acquired = false;
                                try {
                                    long remaining = deadlineNanos - System.nanoTime();
                                    if (remaining <= 0
                                            || !limit.tryAcquire(
                                                    remaining, TimeUnit.NANOSECONDS)) {
                                        throw new TimeoutException(
                                                "Provider concurrency budget expired before dispatch");
                                    }
                                    acquired = true;
                                    cancellation.throwIfCancelled();
                                    requireAvailable(providerProfile);
                                    acquireQuota(providerProfile);
                                    state.complete(work.apply(timeout));
                                } finally {
                                    if (acquired) {
                                        limit.release();
                                    }
                                }
                            } catch (Throwable error) {
                                state.fail(error);
                            }
                            return null;
                        });
        state.attachWorker(worker);
        state.attachRegistration(cancellation.onCancel(state::cancelByUser));
        long delay = Math.max(0, deadlineNanos - System.nanoTime());
        state.attachDeadline(deadlines.schedule(state::expireDeadline, delay, TimeUnit.NANOSECONDS));
        workers.execute(worker);
        return new TimedCall<>(state);
    }

    /** Records a real provider failure and applies its configured backoff/circuit policy. */
    public void recordFailure(
            String providerProfile, UnavailabilityCause cause, Duration retryAfter) {
        if (cause == null) {
            throw new IllegalArgumentException("provider unavailability cause is required");
        }
        AtomicReference<ProviderState> reference = stateFor(providerProfile);
        long now = System.nanoTime();
        long providerBackoff =
                retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()
                        ? 0
                        : retryAfter.toNanos();
        long configuredBackoff = properties.backoffFor(providerProfile).toNanos();
        int threshold = properties.circuitFailureThresholdFor(providerProfile);
        long circuitOpen = properties.circuitOpenDurationFor(providerProfile).toNanos();
        ProviderState updated =
                reference.updateAndGet(
                current -> {
                    int failures = Math.min(threshold, current.consecutiveFailures() + 1);
                    long delay = Math.max(configuredBackoff, providerBackoff);
                    if (failures >= threshold) {
                        delay = Math.max(delay, circuitOpen);
                    }
                    long candidateUntil = saturatedAdd(now, delay);
                    if (candidateUntil >= current.unavailableUntilNanos()) {
                        return new ProviderState(failures, candidateUntil, cause);
                    }
                    return new ProviderState(
                            failures, current.unavailableUntilNanos(), current.cause());
                });
        telemetry.recordResilience(
                providerProfile,
                switch (cause) {
                    case QUOTA -> ConnectorTelemetry.ProviderResilienceCause.QUOTA;
                    case TIMEOUT -> ConnectorTelemetry.ProviderResilienceCause.TIMEOUT;
                    case RETRIEVAL -> ConnectorTelemetry.ProviderResilienceCause.RETRIEVAL;
                    case UNKNOWN -> ConnectorTelemetry.ProviderResilienceCause.UNKNOWN;
                },
                retryAfter,
                Duration.ofNanos(
                        Math.max(0, updated.unavailableUntilNanos() - System.nanoTime())),
                updated.consecutiveFailures(),
                updated.unavailableUntilNanos() > System.nanoTime()
                        && updated.consecutiveFailures() >= threshold);
    }

    /** A successful provider result closes the failure counter after any active window expires. */
    public void recordSuccess(String providerProfile) {
        AtomicReference<ProviderState> reference = stateFor(providerProfile);
        long now = System.nanoTime();
        reference.updateAndGet(
                current ->
                        current.unavailableUntilNanos() > now
                                ? current
                                : ProviderState.available());
        telemetry.recordResilienceClosed(providerProfile);
    }

    public ConnectorTelemetry telemetry() {
        return telemetry;
    }

    public <T> T await(TimedCall<T> call)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return call.state.completion().get();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof TimeoutException timeout) {
                throw timeout;
            }
            throw failure;
        } catch (InterruptedException interrupted) {
            call.state.cancelByInterruption();
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            call.state.cleanup();
        }
    }

    @Override
    @PreDestroy
    public void close() {
        deadlines.shutdownNow();
        workers.shutdownNow();
    }

    public static final class TimedCall<T> {
        private final CallState<T> state;

        private TimedCall(CallState<T> state) {
            this.state = state;
        }

        public long latencyMs() {
            return state.latencyMs();
        }
    }

    public enum UnavailabilityCause {
        QUOTA,
        TIMEOUT,
        RETRIEVAL,
        UNKNOWN
    }

    public static final class ProviderUnavailableException extends RuntimeException {
        private final UnavailabilityCause cause;
        private final Duration retryAfter;

        ProviderUnavailableException(
                String providerProfile, UnavailabilityCause cause, Duration retryAfter) {
            super(
                    "Provider "
                            + providerProfile
                            + " is temporarily unavailable: "
                            + cause.name().toLowerCase());
            this.cause = cause;
            this.retryAfter = retryAfter;
        }

        public UnavailabilityCause cause() {
            return cause;
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
                    providerProfile,
                    state.cause(),
                    Duration.ofNanos(Math.max(1, remaining)));
        }
    }

    private void acquireQuota(String providerProfile) {
        AtomicReference<QuotaWindow> reference = quotaFor(providerProfile);
        int limit = properties.quotaLimitFor(providerProfile);
        long windowNanos = properties.quotaWindowFor(providerProfile).toNanos();
        while (true) {
            long now = System.nanoTime();
            QuotaWindow current = reference.get();
            boolean expired = now - current.startedAtNanos() >= windowNanos;
            QuotaWindow next;
            if (expired) {
                next = new QuotaWindow(now, 1);
            } else if (current.used() >= limit) {
                long retryNanos =
                        Math.max(
                                1,
                                saturatedAdd(current.startedAtNanos(), windowNanos) - now);
                throw new ProviderUnavailableException(
                        providerProfile,
                        UnavailabilityCause.QUOTA,
                        Duration.ofNanos(retryNanos));
            } else {
                next = new QuotaWindow(current.startedAtNanos(), current.used() + 1);
            }
            if (reference.compareAndSet(current, next)) {
                return;
            }
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

    private AtomicReference<QuotaWindow> quotaFor(String providerProfile) {
        AtomicReference<QuotaWindow> quota = providerQuotas.get(providerProfile);
        if (quota == null) {
            throw new IllegalStateException(
                    "No retrieval quota configured for provider " + providerProfile);
        }
        return quota;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private enum TerminalState {
        PENDING,
        COMPLETED,
        FAILED,
        DEADLINE,
        USER_CANCELLED,
        INTERRUPTED
    }

    static final class CallState<T> {
        private final long deadlineNanos;
        private final ConnectorTelemetry.Operation telemetryOperation;
        private final CompletableFuture<T> completion = new CompletableFuture<>();
        private final AtomicReference<TerminalState> terminal =
                new AtomicReference<>(TerminalState.PENDING);
        private final AtomicReference<Future<?>> worker = new AtomicReference<>();
        private final AtomicReference<ScheduledFuture<?>> deadline = new AtomicReference<>();
        private final AtomicReference<CancellationToken.Registration> registration =
                new AtomicReference<>();
        private final AtomicBoolean cleaned = new AtomicBoolean(false);

        private CallState(long deadlineNanos, ConnectorTelemetry.Operation telemetryOperation) {
            this.deadlineNanos = deadlineNanos;
            this.telemetryOperation = telemetryOperation;
        }

        private CompletableFuture<T> completion() {
            return completion;
        }

        private void attachWorker(Future<?> submittedWorker) {
            worker.set(submittedWorker);
            if (terminal.get() != TerminalState.PENDING) {
                submittedWorker.cancel(true);
            }
        }

        private void attachDeadline(ScheduledFuture<?> scheduledDeadline) {
            deadline.set(scheduledDeadline);
            if (cleaned.get()) {
                cancelDeadline(scheduledDeadline);
            }
        }

        private void attachRegistration(CancellationToken.Registration attachedRegistration) {
            registration.set(attachedRegistration);
            if (cleaned.get() && registration.compareAndSet(attachedRegistration, null)) {
                attachedRegistration.close();
            }
        }

        private void complete(T value) {
            if (System.nanoTime() >= deadlineNanos) {
                expireDeadline();
                return;
            }
            if (terminal.compareAndSet(TerminalState.PENDING, TerminalState.COMPLETED)) {
                telemetryOperation.success();
                completion.complete(value);
                cleanup();
            }
        }

        private void fail(Throwable error) {
            if (System.nanoTime() >= deadlineNanos) {
                expireDeadline();
                return;
            }
            if (terminal.compareAndSet(TerminalState.PENDING, TerminalState.FAILED)) {
                recordFailure(error);
                completion.completeExceptionally(error);
                cleanup();
            }
        }

        private void expireDeadline() {
            terminate(
                    TerminalState.DEADLINE,
                    new TimeoutException("Provider operation exceeded its deadline"));
        }

        private void cancelByUser() {
            terminate(TerminalState.USER_CANCELLED, new CancellationException("operation cancelled"));
        }

        private void cancelByInterruption() {
            terminate(
                    TerminalState.INTERRUPTED,
                    new CancellationException("provider await interrupted"));
        }

        private void terminate(TerminalState terminalState, Throwable failure) {
            if (!terminal.compareAndSet(TerminalState.PENDING, terminalState)) {
                return;
            }
            if (terminalState == TerminalState.DEADLINE) {
                telemetryOperation.timeout();
            } else if (terminalState == TerminalState.USER_CANCELLED
                    || terminalState == TerminalState.INTERRUPTED) {
                telemetryOperation.cancelled();
            } else {
                recordFailure(failure);
            }
            completion.completeExceptionally(failure);
            Future<?> runningWorker = worker.get();
            if (runningWorker != null) {
                runningWorker.cancel(true);
            }
            cleanup();
        }

        private long latencyMs() {
            return telemetryOperation.elapsedMillis();
        }

        private void recordFailure(Throwable error) {
            if (error instanceof ProviderUnavailableException unavailable) {
                if (unavailable.cause() == UnavailabilityCause.QUOTA) {
                    telemetryOperation.quota(unavailable.retryAfter());
                } else if (unavailable.cause() == UnavailabilityCause.TIMEOUT) {
                    telemetryOperation.timeout();
                } else {
                    telemetryOperation.failure();
                }
            } else if (error instanceof TimeoutException) {
                telemetryOperation.timeout();
            } else if (error instanceof CancellationException) {
                telemetryOperation.cancelled();
            } else {
                telemetryOperation.failure();
            }
        }

        private void cleanup() {
            if (!cleaned.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> scheduledDeadline = deadline.getAndSet(null);
            if (scheduledDeadline != null) {
                cancelDeadline(scheduledDeadline);
            }
            CancellationToken.Registration attachedRegistration = registration.getAndSet(null);
            if (attachedRegistration != null) {
                attachedRegistration.close();
            }
        }

        private static void cancelDeadline(ScheduledFuture<?> scheduledDeadline) {
            scheduledDeadline.cancel(false);
        }
    }

    private record QuotaWindow(long startedAtNanos, int used) {
        private static QuotaWindow initial(long now) {
            return new QuotaWindow(now, 0);
        }
    }

    private record ProviderState(
            int consecutiveFailures,
            long unavailableUntilNanos,
            UnavailabilityCause cause) {
        private static ProviderState available() {
            return new ProviderState(0, 0, UnavailabilityCause.UNKNOWN);
        }
    }
}
