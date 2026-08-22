package com.atlas.knowledgebase.chat;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.ModelChannel;
import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.retrieval.RetrievalOrchestrator;
import com.atlas.knowledgebase.retrieval.RetrievalScope;
import com.atlas.knowledgebase.retrieval.RetrievalTurn;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private static final int MIN_SCOPE = 1;
    private static final int MAX_SCOPE = 5;
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final ChatThreadRepository threads;
    private final ChatMessageRepository messages;
    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final KbAccessService access;
    private final RetrievalOrchestrator retrieval;
    private final ModelChannel modelChannel;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, InFlight> inFlight = new ConcurrentHashMap<>();

    public ChatService(
            ChatThreadRepository threads,
            ChatMessageRepository messages,
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            KbAccessService access,
            RetrievalOrchestrator retrieval,
            ModelChannel modelChannel,
            AuditEventRepository auditEvents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.threads = threads;
        this.messages = messages;
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.access = access;
        this.retrieval = retrieval;
        this.modelChannel = modelChannel;
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Map<String, Object> list(AtlasUserRecord user) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatThreadRecord thread : threads.findActiveByUserId(user.userId())) {
            items.add(threadProjection(thread));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("last_valid_logical_kb_ids", lastValidScope(user));
        return body;
    }

    public Map<String, Object> create(AtlasUserRecord user, List<String> requestedIds) {
        List<String> scope =
                requestedIds == null || requestedIds.isEmpty() ? lastValidScope(user) : requestedIds;
        ResolvedScope resolved = resolveScope(user, scope);
        Instant now = clock.instant();
        ChatThreadRecord thread =
                threads.insert(
                        new ChatThreadRecord(
                                "thr_" + SessionService.randomToken().substring(0, 16),
                                user.userId(),
                                null,
                                writeJson(resolved.logicalKbIds()),
                                null,
                                now,
                                now,
                                null));
        audit(user.userId(), resolved.logicalKbIds().getFirst(), "chat_create", "allow", "ok");
        return threadProjection(thread);
    }

    public Map<String, Object> get(AtlasUserRecord user, String threadId) {
        ChatThreadRecord thread = requireOwnThread(user, threadId);
        Map<String, Object> body = threadProjection(thread);
        List<Map<String, Object>> history = new ArrayList<>();
        for (ChatMessageRecord message : messages.findByThreadId(threadId)) {
            history.add(messageProjection(message));
        }
        body.put("messages", history);
        return body;
    }

    public Map<String, Object> changeScope(
            AtlasUserRecord user, String threadId, List<String> requestedIds, String mode) {
        ChatThreadRecord thread = requireOwnThread(user, threadId);
        ResolvedScope resolved = resolveScope(user, requestedIds);
        boolean hasMessages = messages.countByThreadId(threadId) > 0;
        Instant now = clock.instant();
        if (!hasMessages) {
            ChatThreadRecord updated = threads.updateScope(threadId, writeJson(resolved.logicalKbIds()), now);
            return threadProjection(updated);
        }
        if (mode == null || mode.isBlank()) {
            throw new ChatValidationException(
                    "MODE_REQUIRED",
                    "Changing scope after answers exist requires mode=branch or mode=new.");
        }
        if (!"branch".equals(mode) && !"new".equals(mode)) {
            throw new ChatValidationException("MODE_INVALID", "mode must be branch or new.");
        }
        String branchedFrom = "branch".equals(mode) ? thread.threadId() : null;
        ChatThreadRecord created =
                threads.insert(
                        new ChatThreadRecord(
                                "thr_" + SessionService.randomToken().substring(0, 16),
                                user.userId(),
                                null,
                                writeJson(resolved.logicalKbIds()),
                                branchedFrom,
                                now,
                                now,
                                null));
        Map<String, Object> body = threadProjection(created);
        if (branchedFrom != null) {
            body.put("branched_from_thread_id", branchedFrom);
        }
        return body;
    }

    public void delete(AtlasUserRecord user, String threadId) {
        requireOwnThread(user, threadId);
        threads.softDelete(threadId, clock.instant());
    }

    public SseEmitter ask(AtlasUserRecord user, String threadId, String question) {
        if (question == null || question.isBlank()) {
            throw new ChatValidationException("QUESTION_REQUIRED", "question is required.");
        }
        if (!user.modelEntitled()) {
            throw new ChatForbiddenException(
                    "MODEL_NOT_ENTITLED",
                    "Model-send is not entitled for this user.",
                    "request_model_entitlement");
        }
        ChatThreadRecord thread = requireOwnThread(user, threadId);
        ResolvedScope resolved = resolveScope(user, readIds(thread.selectedLogicalKbIdsJson()));
        RetrievalTurn turn = retrieveOrThrow(user, question, resolved);
        RetrievalScope actualScope = turn.scope();
        Instant now = clock.instant();
        String requestId = "req_" + SessionService.randomToken().substring(0, 16);
        ChatMessageRecord userMessage =
                messages.insert(
                        new ChatMessageRecord(
                                "msg_" + SessionService.randomToken().substring(0, 16),
                                threadId,
                                "user",
                                "completed",
                                question,
                                null,
                                writeJson(actualScope.logicalKbIds()),
                                writeJson(actualScope.bindingIds()),
                                writeJson(actualScope.configVersions()),
                                null,
                                null,
                                resolved.classification(),
                                requestId,
                                now,
                                now));
        ChatMessageRecord assistant =
                messages.insert(
                        new ChatMessageRecord(
                                "msg_" + SessionService.randomToken().substring(0, 16),
                                threadId,
                                "assistant",
                                "processing",
                                null,
                                null,
                                writeJson(actualScope.logicalKbIds()),
                                writeJson(actualScope.bindingIds()),
                                writeJson(actualScope.configVersions()),
                                null,
                                null,
                                resolved.classification(),
                                requestId,
                                now,
                                null));
        threads.touch(threadId, now);
        audit(user.userId(), actualScope.logicalKbIds().getFirst(), "chat_ask", "allow", "ok");
        return startGeneration(user, assistant, question, turn, false);
    }

    public Map<String, Object> cancel(AtlasUserRecord user, String threadId, String messageId) {
        requireOwnThread(user, threadId);
        ChatMessageRecord message =
                messages.findById(messageId).orElseThrow(() -> new ChatNotFoundException(threadId));
        if (!threadId.equals(message.threadId())) {
            throw new ChatNotFoundException(threadId);
        }
        if (!"assistant".equals(message.role())) {
            throw new ChatValidationException("NOT_ASSISTANT", "Only assistant messages can be cancelled.");
        }
        if ("completed".equals(message.status())) {
            throw new ChatConflictException("ALREADY_COMPLETED", "Completed answers cannot be cancelled.");
        }
        if ("incomplete_cancelled".equals(message.status())) {
            return Map.of("message_id", messageId, "status", "incomplete_cancelled");
        }
        InFlight flight = inFlight.get(messageId);
        if (flight != null) {
            flight.cancelled.set(true);
        }
        int cancelled = messages.cancelIfInFlight(messageId, clock.instant());
        if (cancelled == 0) {
            ChatMessageRecord current =
                    messages.findById(messageId).orElseThrow(() -> new ChatNotFoundException(threadId));
            if ("completed".equals(current.status())) {
                throw new ChatConflictException("ALREADY_COMPLETED", "Completed answers cannot be cancelled.");
            }
            if ("incomplete_cancelled".equals(current.status())) {
                return Map.of("message_id", messageId, "status", "incomplete_cancelled");
            }
            throw new ChatConflictException("NOT_CANCELLABLE", "This message is not in-flight.");
        }
        if (flight != null) {
            completeQuietly(flight.emitter);
        }
        return Map.of("message_id", messageId, "status", "incomplete_cancelled");
    }

    public SseEmitter retry(AtlasUserRecord user, String threadId, String messageId) {
        if (!user.modelEntitled()) {
            throw new ChatForbiddenException(
                    "MODEL_NOT_ENTITLED",
                    "Model-send is not entitled for this user.",
                    "request_model_entitlement");
        }
        ChatThreadRecord thread = requireOwnThread(user, threadId);
        ChatMessageRecord message =
                messages.findById(messageId).orElseThrow(() -> new ChatNotFoundException(threadId));
        if (!threadId.equals(message.threadId()) || !"assistant".equals(message.role())) {
            throw new ChatNotFoundException(threadId);
        }
        if ("completed".equals(message.status())) {
            return replayCompleted(message);
        }
        if ("processing".equals(message.status()) || "streaming".equals(message.status())) {
            throw new ChatConflictException("IN_FLIGHT", "Wait for the in-flight generation or cancel it.");
        }
        ResolvedScope resolved = resolveScope(user, readIds(thread.selectedLogicalKbIdsJson()));
        String question = latestUserQuestion(threadId, message);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        InFlight flight = new InFlight(emitter, new AtomicBoolean(false));
        int reserved =
                messages.markProcessingIfRetryable(
                        message.messageId(),
                        writeJson(resolved.retrievalScope().logicalKbIds()),
                        writeJson(resolved.retrievalScope().bindingIds()),
                        writeJson(resolved.retrievalScope().configVersions()),
                        resolved.classification());
        if (reserved == 0) {
            throw new ChatConflictException("NOT_RETRYABLE", "This message cannot be retried.");
        }
        InFlight existing = inFlight.putIfAbsent(message.messageId(), flight);
        if (existing != null) {
            messages.failIfInFlight(message.messageId(), clock.instant());
            throw new ChatConflictException(
                    "IN_FLIGHT", "Wait for the in-flight generation or cancel it.");
        }
        configureFlight(message.messageId(), flight);
        try {
            if (!retryStillReserved(message.messageId(), flight)) {
                completeQuietly(emitter);
                return emitter;
            }
            RetrievalTurn turn = retrieveOrThrow(user, question, resolved);
            if (!retryStillReserved(message.messageId(), flight)) {
                completeQuietly(emitter);
                return emitter;
            }
            launchGeneration(user, message, question, turn, true, flight);
            return emitter;
        } catch (RuntimeException failure) {
            messages.failIfInFlight(message.messageId(), clock.instant());
            inFlight.remove(message.messageId(), flight);
            completeQuietly(emitter);
            throw failure;
        }
    }

    private SseEmitter replayCompleted(ChatMessageRecord message) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("final")
                            .data(finalPayload(message, message.answerText(), null)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private SseEmitter startGeneration(
            AtlasUserRecord user,
            ChatMessageRecord assistant,
            String question,
            RetrievalTurn turn,
            boolean retry) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        InFlight flight = new InFlight(emitter, new AtomicBoolean(false));
        if (inFlight.putIfAbsent(assistant.messageId(), flight) != null) {
            throw new ChatConflictException(
                    "IN_FLIGHT", "Wait for the in-flight generation or cancel it.");
        }
        configureFlight(assistant.messageId(), flight);
        launchGeneration(user, assistant, question, turn, retry, flight);
        return emitter;
    }

    private void configureFlight(String messageId, InFlight flight) {
        flight.emitter.onCompletion(() -> inFlight.remove(messageId, flight));
        flight.emitter.onTimeout(
                () -> {
                    flight.cancelled.set(true);
                    inFlight.remove(messageId, flight);
                });
    }

    private void launchGeneration(
            AtlasUserRecord user,
            ChatMessageRecord assistant,
            String question,
            RetrievalTurn turn,
            boolean retry,
            InFlight flight) {
        Thread.ofVirtual()
                .name("chat-gen-" + assistant.messageId())
                .start(
                        () ->
                                runGeneration(
                                        user, assistant, question, turn, retry, flight));
    }

    private boolean retryStillReserved(String messageId, InFlight flight) {
        if (flight.cancelled.get()) {
            messages.cancelIfInFlight(messageId, clock.instant());
            inFlight.remove(messageId, flight);
            return false;
        }
        ChatMessageRecord current = messages.findById(messageId).orElse(null);
        if (current == null || !"processing".equals(current.status())) {
            flight.cancelled.set(true);
            inFlight.remove(messageId, flight);
            return false;
        }
        return true;
    }

    private void runGeneration(
            AtlasUserRecord user,
            ChatMessageRecord assistant,
            String question,
            RetrievalTurn turn,
            boolean retry,
            InFlight flight) {
        try {
            if (superseded(assistant.messageId(), flight)) {
                return;
            }
            messages.markStreamingIfProcessing(assistant.messageId());
            if (flight.cancelled.get()) {
                persistCancelled(assistant.messageId(), flight);
                completeQuietly(flight.emitter);
                return;
            }
            StringBuilder answer = new StringBuilder();
            modelChannel.generate(
                    new ModelChannel.Request(
                            assistant.requestId(),
                            question,
                            user.userId(),
                            turn.fused().stream().map(hit -> hit.hit().fingerprint()).toList()),
                    new ModelChannel.Listener() {
                        @Override
                        public void onToken(String delta) {
                            if (flight.cancelled.get() || superseded(assistant.messageId(), flight)) {
                                return;
                            }
                            answer.append(delta);
                            try {
                                flight.emitter.send(SseEmitter.event().name("token").data(Map.of("delta", delta)));
                            } catch (IOException e) {
                                flight.cancelled.set(true);
                            }
                        }

                        @Override
                        public void onComplete(String completeAnswer) {
                            if (superseded(assistant.messageId(), flight)) {
                                completeQuietly(flight.emitter);
                                return;
                            }
                            if (flight.cancelled.get()) {
                                persistCancelled(assistant.messageId(), flight);
                                completeQuietly(flight.emitter);
                                return;
                            }
                            Instant done = clock.instant();
                            String coverage = writeJson(turn.coverage());
                            int completed =
                                    messages.completeIfInFlight(
                                            assistant.messageId(), completeAnswer, coverage, done);
                            if (completed == 0) {
                                completeQuietly(flight.emitter);
                                return;
                            }
                            ChatMessageRecord stored =
                                    messages.findById(assistant.messageId()).orElseThrow();
                            try {
                                flight.emitter.send(
                                        SseEmitter.event()
                                                .name("final")
                                                .data(finalPayload(stored, completeAnswer, turn)));
                                flight.emitter.complete();
                            } catch (IOException e) {
                                completeQuietly(flight.emitter);
                            }
                            audit(
                                    user.userId(),
                                    turn.scope().logicalKbIds().getFirst(),
                                    retry ? "chat_retry" : "chat_complete",
                                    "allow",
                                    "ok");
                        }

                        @Override
                        public void onCancelled() {
                            persistCancelled(assistant.messageId(), flight);
                            completeQuietly(flight.emitter);
                        }

                        @Override
                        public boolean isCancelled() {
                            return flight.cancelled.get() || superseded(assistant.messageId(), flight);
                        }
                    });
        } catch (RuntimeException e) {
            if (!flight.cancelled.get() && !superseded(assistant.messageId(), flight)) {
                messages.failIfInFlight(assistant.messageId(), clock.instant());
                completeQuietly(flight.emitter);
            }
        } finally {
            inFlight.remove(assistant.messageId(), flight);
        }
    }

    private boolean superseded(String messageId, InFlight flight) {
        InFlight current = inFlight.get(messageId);
        return current != null && current != flight;
    }

    private void persistCancelled(String messageId, InFlight flight) {
        if (superseded(messageId, flight)) {
            return;
        }
        messages.cancelIfInFlight(messageId, clock.instant());
    }

    private RetrievalTurn retrieveOrThrow(
            AtlasUserRecord user, String question, ResolvedScope resolved) {
        RetrievalTurn turn = retrieval.retrieve(user, question, resolved.retrievalScope());
        if (turn.block() == RetrievalTurn.Block.BINDING_ACCESS) {
            throw new ChatForbiddenException(
                    "KB_BINDING_ACCESS_MISSING",
                    "One complete source of the selected knowledge base is unavailable for this turn.",
                    "reconnect_or_request_access",
                    Map.of(
                            "logical_kb_id", turn.blockLogicalKbId(),
                            "binding_id", turn.blockBindingId()));
        }
        if (turn.block() == RetrievalTurn.Block.BINDING_UNAVAILABLE) {
            throw new ChatRetrievalException(
                    "retrieval",
                    "KB_BINDING_UNAVAILABLE",
                    "One configured source is disabled, unavailable, or cannot prove required freshness for this turn.",
                    "contact_owner_or_change_scope",
                    Map.of("coverage", turn.coverage()));
        }
        if (turn.block() == RetrievalTurn.Block.SECURITY) {
            throw new ChatForbiddenException(
                    "KB_SECURITY_FAILURE",
                    "Permission or security-boundary failure closed this knowledge base.",
                    "contact_owner");
        }
        if (turn.block() == RetrievalTurn.Block.NO_EVIDENCE) {
            throw new ChatRetrievalException(
                    "retrieval",
                    "NO_GROUNDED_EVIDENCE",
                    "No selected source returned grounded evidence for this turn.",
                    "retry_or_change_scope",
                    Map.of("coverage", turn.coverage()));
        }
        if (turn.block() == RetrievalTurn.Block.UNKNOWN) {
            throw new ChatRetrievalException(
                    "unknown",
                    "RETRIEVAL_UNKNOWN_FAILURE",
                    "Retrieval could not be completed safely for this turn.",
                    "retry_or_contact_support",
                    Map.of("coverage", turn.coverage()));
        }
        return turn;
    }

    private String latestUserQuestion(String threadId, ChatMessageRecord assistant) {
        ChatMessageRecord latestUser = null;
        for (ChatMessageRecord row : messages.findByThreadId(threadId)) {
            if ("user".equals(row.role()) && !row.createdAt().isAfter(assistant.createdAt())) {
                latestUser = row;
            }
        }
        if (latestUser == null || latestUser.questionText() == null || latestUser.questionText().isBlank()) {
            throw new ChatValidationException("QUESTION_REQUIRED", "No question is available to retry.");
        }
        return latestUser.questionText();
    }

    private ChatThreadRecord requireOwnThread(AtlasUserRecord user, String threadId) {
        ChatThreadRecord thread =
                threads.findById(threadId).orElseThrow(() -> new ChatNotFoundException(threadId));
        if (thread.deletedAt() != null || !user.userId().equals(thread.userId())) {
            throw new ChatNotFoundException(threadId);
        }
        return thread;
    }

    private List<String> lastValidScope(AtlasUserRecord user) {
        for (ChatThreadRecord thread : threads.findActiveByUserId(user.userId())) {
            List<String> ids = readIds(thread.selectedLogicalKbIdsJson());
            if (ids.isEmpty() || ids.size() > MAX_SCOPE) {
                continue;
            }
            boolean valid = true;
            for (String id : ids) {
                LogicalKnowledgeBaseRecord kb = knowledgeBases.findById(id).orElse(null);
                if (kb == null || !access.chatEligible(user, kb)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return ids;
            }
        }
        return List.of();
    }

    private ResolvedScope resolveScope(AtlasUserRecord user, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ChatValidationException(
                    "SCOPE_REQUIRED", "Select at least one Chat-ready knowledge base.");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String id : requested) {
            if (id == null || id.isBlank()) {
                continue;
            }
            unique.add(id);
        }
        if (unique.size() < MIN_SCOPE) {
            throw new ChatValidationException(
                    "SCOPE_REQUIRED", "Select at least one Chat-ready knowledge base.");
        }
        if (unique.size() > MAX_SCOPE) {
            throw new ChatValidationException(
                    "SCOPE_LIMIT", "A chat may select at most five logical knowledge bases.");
        }
        if (unique.size() != requested.stream().filter(id -> id != null && !id.isBlank()).count()) {
            throw new ChatValidationException("SCOPE_DUPLICATE", "Duplicate knowledge bases are not allowed.");
        }
        List<String> logicalKbIds = List.copyOf(unique);
        List<RetrievalScope.KnowledgeBaseSnapshot> snapshots = new ArrayList<>();
        for (String logicalKbId : logicalKbIds) {
            LogicalKnowledgeBaseRecord kb =
                    knowledgeBases
                            .findById(logicalKbId)
                            .orElseThrow(
                                    () ->
                                            new ChatForbiddenException(
                                                    "KB_UNAUTHORIZED",
                                                    "Knowledge base is not authorized for Chat: " + logicalKbId,
                                                    "request_access"));
            if (!access.authorized(user, kb)) {
                throw new ChatForbiddenException(
                        "KB_UNAUTHORIZED",
                        "Knowledge base is not authorized for Chat: " + logicalKbId,
                        "request_access");
            }
            if (!access.chatEligible(user, kb)) {
                throw new ChatValidationException(
                        "NOT_CHAT_READY",
                        "Knowledge base is not Chat-ready: " + logicalKbId);
            }
            List<BindingRecord> currentBindings = bindings.findByLogicalKbId(logicalKbId);
            snapshots.add(new RetrievalScope.KnowledgeBaseSnapshot(kb, currentBindings));
        }
        return new ResolvedScope(
                logicalKbIds,
                ChatClassificationPolicy.resolve(snapshots),
                new RetrievalScope(snapshots));
    }

    private Map<String, Object> threadProjection(ChatThreadRecord thread) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("thread_id", thread.threadId());
        body.put("logical_kb_ids", readIds(thread.selectedLogicalKbIdsJson()));
        if (thread.branchedFromThreadId() != null) {
            body.put("branched_from_thread_id", thread.branchedFromThreadId());
        }
        return body;
    }

    private Map<String, Object> messageProjection(ChatMessageRecord message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_id", message.messageId());
        body.put("role", message.role());
        body.put("status", message.status());
        body.put("question", message.questionText());
        if ("completed".equals(message.status())) {
            body.put("answer", message.answerText());
        } else {
            body.put("answer", null);
        }
        body.put("request_id", message.requestId());
        return body;
    }

    private Map<String, Object> finalPayload(
            ChatMessageRecord message, String answer, RetrievalTurn turn) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_id", message.messageId());
        body.put("status", "completed");
        body.put("answer", answer);
        body.put("citations", turn == null ? List.of() : turn.citations());
        body.put("coverage", turn == null ? storedCoverage(message) : turn.coverage());
        body.put("conflict", turn == null ? null : turn.conflict());
        body.put("classification", message.classification());
        body.put("request_id", message.requestId());
        return body;
    }

    private Map<String, Object> storedCoverage(ChatMessageRecord message) {
        if (message.coverageJson() == null || message.coverageJson().isBlank()) {
            Map<String, Object> coverage = new LinkedHashMap<>();
            coverage.put("successful", List.of());
            coverage.put("failed", List.of());
            coverage.put("timed_out", List.of());
            return coverage;
        }
        try {
            Map<String, Object> coverage =
                    objectMapper.readValue(message.coverageJson(), new TypeReference<Map<String, Object>>() {});
            return coverage == null ? Map.of() : coverage;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private List<String> readIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return ids == null ? List.of() : List.copyOf(ids);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize chat JSON", e);
        }
    }

    private void audit(String userId, String logicalKbId, String action, String authorization, String status) {
        try {
            auditEvents.insert(
                    new AuditEventRecord(
                            "aud_" + SessionService.randomToken().substring(0, 16),
                            clock.instant(),
                            userId,
                            logicalKbId,
                            null,
                            null,
                            action,
                            authorization,
                            null,
                            null,
                            null,
                            status,
                            null,
                            objectMapper.writeValueAsString(Map.of("action", action))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize content-free audit details", e);
        }
    }

    private static void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // already completed
        }
    }

    private record ResolvedScope(
            List<String> logicalKbIds,
            String classification,
            RetrievalScope retrievalScope) {}

    private record InFlight(SseEmitter emitter, AtomicBoolean cancelled) {}
}
