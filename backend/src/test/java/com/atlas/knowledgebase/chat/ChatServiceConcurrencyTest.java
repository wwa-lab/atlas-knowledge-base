package com.atlas.knowledgebase.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.ModelChannel;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.retrieval.RetrievalOrchestrator;
import com.atlas.knowledgebase.retrieval.RetrievalScope;
import com.atlas.knowledgebase.retrieval.RetrievalTurn;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatServiceConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void concurrentRetryReservesBeforeRetrievalAndKeepsCancellationAttached() throws Exception {
        ChatThreadRepository threads = mock(ChatThreadRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        LogicalKnowledgeBaseRepository knowledgeBases = mock(LogicalKnowledgeBaseRepository.class);
        BindingRepository bindings = mock(BindingRepository.class);
        RetrievalOrchestrator retrieval = mock(RetrievalOrchestrator.class);
        ModelChannel modelChannel = mock(ModelChannel.class);
        AuditEventRepository auditEvents = mock(AuditEventRepository.class);
        ChatClassificationProperties classificationProperties = new ChatClassificationProperties();
        classificationProperties.setApprovedValues(java.util.Set.of("internal", "restricted"));
        ChatService service =
                new ChatService(
                        threads,
                        messages,
                        knowledgeBases,
                        bindings,
                        new KbAccessService(),
                        new ChatClassificationPolicy(classificationProperties),
                        retrieval,
                        modelChannel,
                        auditEvents,
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC));

        AtlasUserRecord user =
                new AtlasUserRecord(
                        "usr_1",
                        "sso_1",
                        "Owner",
                        "owner@example.com",
                        "[\"kb_owner\"]",
                        true,
                        NOW,
                        NOW);
        ChatThreadRecord thread =
                new ChatThreadRecord(
                        "thr_1", user.userId(), null, "[\"kb_1\"]", null, NOW, NOW, null);
        ChatMessageRecord userMessage =
                new ChatMessageRecord(
                        "msg_user",
                        thread.threadId(),
                        "user",
                        "completed",
                        "question",
                        null,
                        "[\"kb_1\"]",
                        "[\"bnd_1\"]",
                        "{}",
                        null,
                        null,
                        "internal",
                        "req_1",
                        NOW,
                        NOW);
        AtomicReference<String> status = new AtomicReference<>("failed");
        ChatMessageRecord failedAssistant = assistant(status.get());
        LogicalKnowledgeBaseRecord kb = knowledgeBase(user.userId());
        BindingRecord binding = binding();
        RetrievalScope scope =
                new RetrievalScope(
                        List.of(
                                new RetrievalScope.KnowledgeBaseSnapshot(
                                        kb, List.of(binding))));
        RetrievalTurn turn =
                new RetrievalTurn(
                        Map.of("successful", List.of("bnd_1")),
                        List.of(),
                        List.of(),
                        null,
                        scope,
                        RetrievalTurn.Block.NONE,
                        null,
                        null);

        when(threads.findById(thread.threadId())).thenReturn(Optional.of(thread));
        when(messages.findById(failedAssistant.messageId()))
                .thenAnswer(ignored -> Optional.of(assistant(status.get())));
        when(messages.findByThreadId(thread.threadId()))
                .thenReturn(List.of(userMessage, failedAssistant));
        when(knowledgeBases.findById(kb.logicalKbId())).thenReturn(Optional.of(kb));
        when(bindings.findByLogicalKbId(kb.logicalKbId())).thenReturn(List.of(binding));
        when(messages.markProcessingIfRetryable(
                        anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        ignored -> status.compareAndSet("failed", "processing") ? 1 : 0);
        when(messages.cancelIfInFlight(anyString(), any()))
                .thenAnswer(
                        ignored -> {
                            String current = status.get();
                            if (!"processing".equals(current) && !"streaming".equals(current)) {
                                return 0;
                            }
                            status.set("incomplete_cancelled");
                            return 1;
                        });

        CountDownLatch retrievalEntered = new CountDownLatch(1);
        CountDownLatch releaseRetrieval = new CountDownLatch(1);
        AtomicInteger retrievalCalls = new AtomicInteger();
        when(retrieval.retrieve(any(), anyString(), any(), any()))
                .thenAnswer(
                        ignored -> {
                            retrievalCalls.incrementAndGet();
                            retrievalEntered.countDown();
                            assertThat(releaseRetrieval.await(2, TimeUnit.SECONDS)).isTrue();
                            return turn;
                        });

        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        CountDownLatch secondFinished = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<SseEmitter> first =
                    executor.submit(
                            () ->
                                    service.retry(
                                            user,
                                            thread.threadId(),
                                            failedAssistant.messageId()));
            assertThat(retrievalEntered.await(1, TimeUnit.SECONDS)).isTrue();

            executor.submit(
                    () -> {
                        try {
                            service.retry(user, thread.threadId(), failedAssistant.messageId());
                        } catch (Throwable failure) {
                            secondFailure.set(failure);
                        } finally {
                            secondFinished.countDown();
                        }
                    });

            try {
                assertThat(secondFinished.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(secondFailure.get()).isInstanceOf(ChatConflictException.class);
                assertThat(retrievalCalls.get()).isEqualTo(1);

                Map<String, Object> cancelled =
                        service.cancel(user, thread.threadId(), failedAssistant.messageId());
                assertThat(cancelled.get("status")).isEqualTo("incomplete_cancelled");
            } finally {
                releaseRetrieval.countDown();
            }

            assertThat(first.get(2, TimeUnit.SECONDS)).isNotNull();
        }

        verify(retrieval, times(1)).retrieve(any(), anyString(), any(), any());
        verify(modelChannel, never()).generate(any(), any(ModelChannel.Listener.class));
    }

    private static ChatMessageRecord assistant(String status) {
        return new ChatMessageRecord(
                "msg_assistant",
                "thr_1",
                "assistant",
                status,
                null,
                null,
                "[\"kb_1\"]",
                "[\"bnd_1\"]",
                "{}",
                null,
                null,
                "internal",
                "req_1",
                NOW,
                null);
    }

    private static LogicalKnowledgeBaseRecord knowledgeBase(String ownerUserId) {
        return new LogicalKnowledgeBaseRecord(
                "kb_1",
                "KB",
                "description",
                ownerUserId,
                "private",
                "support",
                "internal",
                true,
                "chat_ready",
                "active",
                "healthy",
                1,
                null,
                false,
                null,
                NOW,
                NOW,
                NOW);
    }

    private static BindingRecord binding() {
        return new BindingRecord(
                "bnd_1",
                "kb_1",
                "dify",
                "{}",
                "canonical",
                "delegated",
                "healthy",
                true,
                false,
                true,
                "{}",
                "{}",
                "owner",
                "[]",
                1,
                NOW,
                NOW);
    }
}
