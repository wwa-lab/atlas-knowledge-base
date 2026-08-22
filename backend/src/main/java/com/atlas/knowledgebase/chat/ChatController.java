package com.atlas.knowledgebase.chat;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chats")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return chat.list(user);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            HttpServletRequest request, @RequestBody(required = false) CreateChatRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        List<String> ids = body == null || body.logicalKbIds() == null ? List.of() : body.logicalKbIds();
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.create(user, ids));
    }

    @GetMapping("/{threadId}")
    public Map<String, Object> get(HttpServletRequest request, @PathVariable String threadId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return chat.get(user, threadId);
    }

    @PostMapping("/{threadId}/scope")
    public Map<String, Object> scope(
            HttpServletRequest request,
            @PathVariable String threadId,
            @RequestBody ScopeRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        List<String> ids = body == null || body.logicalKbIds() == null ? List.of() : body.logicalKbIds();
        String mode = body == null ? null : body.mode();
        return chat.changeScope(user, threadId, ids, mode);
    }

    @DeleteMapping("/{threadId}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String threadId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        chat.delete(user, threadId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{threadId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(
            HttpServletRequest request,
            @PathVariable String threadId,
            @RequestBody AskRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return chat.ask(user, threadId, body == null ? null : body.question());
    }

    @PostMapping("/{threadId}/messages/{messageId}/cancel")
    public Map<String, Object> cancel(
            HttpServletRequest request,
            @PathVariable String threadId,
            @PathVariable String messageId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return chat.cancel(user, threadId, messageId);
    }

    @PostMapping(
            path = "/{threadId}/messages/{messageId}/retry",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter retry(
            HttpServletRequest request,
            @PathVariable String threadId,
            @PathVariable String messageId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return chat.retry(user, threadId, messageId);
    }

    public record CreateChatRequest(@JsonProperty("logical_kb_ids") List<String> logicalKbIds) {}

    public record ScopeRequest(
            @JsonProperty("logical_kb_ids") List<String> logicalKbIds, String mode) {}

    public record AskRequest(String question) {}
}
