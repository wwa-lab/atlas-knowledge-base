package com.atlas.knowledgebase.retrieval;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Executes adapter work under provider-specific deadlines and concurrency limits. */
@Component
public final class ProviderExecution implements AutoCloseable {

    private final RetrievalProperties properties;
    private final Map<String, Semaphore> providerLimits;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public ProviderExecution(RetrievalProperties properties, RetrieverRegistry retrievers) {
        this.properties = properties;
        properties.validateProfiles(retrievers.providers());
        Map<String, Semaphore> limits = new LinkedHashMap<>();
        retrievers.providers()
                .forEach(
                        provider ->
                                limits.put(
                                        provider,
                                        new Semaphore(properties.concurrencyFor(provider))));
        this.providerLimits = Map.copyOf(limits);
    }

    public <T> TimedCall<T> submit(
            String providerProfile, Function<Duration, T> operation) {
        Duration timeout = properties.timeoutFor(providerProfile);
        Semaphore limit = providerLimits.get(providerProfile);
        if (limit == null) {
            throw new IllegalStateException(
                    "No retrieval concurrency limit configured for provider " + providerProfile);
        }
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        Future<T> future =
                workers.submit(
                        () -> {
                            boolean acquired = false;
                            try {
                                long remaining = deadlineNanos - System.nanoTime();
                                if (remaining <= 0
                                        || !limit.tryAcquire(remaining, TimeUnit.NANOSECONDS)) {
                                    throw new TimeoutException(
                                            "Provider concurrency budget expired before dispatch");
                                }
                                acquired = true;
                                return operation.apply(timeout);
                            } finally {
                                if (acquired) {
                                    limit.release();
                                }
                            }
                        });
        return new TimedCall<>(future, deadlineNanos);
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
        }
    }

    @Override
    @PreDestroy
    public void close() {
        workers.shutdownNow();
    }

    public record TimedCall<T>(Future<T> future, long deadlineNanos) {}
}
