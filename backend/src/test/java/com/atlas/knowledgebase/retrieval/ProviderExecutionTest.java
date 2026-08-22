package com.atlas.knowledgebase.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.knowledgebase.adapters.Retriever;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
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
