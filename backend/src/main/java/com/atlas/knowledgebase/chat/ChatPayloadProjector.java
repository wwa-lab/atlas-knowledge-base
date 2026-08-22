package com.atlas.knowledgebase.chat;

import com.atlas.knowledgebase.evidence.CitationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds Chat API projections without owning orchestration or persistence decisions. */
@Component
final class ChatPayloadProjector {

    private final ObjectMapper objectMapper;
    private final CitationRepository citations;

    ChatPayloadProjector(ObjectMapper objectMapper, CitationRepository citations) {
        this.objectMapper = objectMapper;
        this.citations = citations;
    }

    Map<String, Object> thread(ChatThreadRecord thread) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("thread_id", thread.threadId());
        body.put("logical_kb_ids", readIds(thread.selectedLogicalKbIdsJson()));
        if (thread.branchedFromThreadId() != null) {
            body.put("branched_from_thread_id", thread.branchedFromThreadId());
        }
        return body;
    }

    Map<String, Object> message(ChatMessageRecord message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_id", message.messageId());
        body.put("role", message.role());
        body.put("status", message.status());
        body.put("question", message.questionText());
        body.put("answer", "completed".equals(message.status()) ? message.answerText() : null);
        body.put("request_id", message.requestId());
        return body;
    }

    Map<String, Object> replay(ChatMessageRecord message) {
        return completed(
                message,
                message.answerText(),
                citations.summariesByMessageId(message.messageId()),
                storedCoverage(message),
                null);
    }

    Map<String, Object> completed(
            ChatMessageRecord message,
            String answer,
            List<Map<String, Object>> citationSummaries,
            Map<String, Object> coverage,
            Object conflict) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_id", message.messageId());
        body.put("status", "completed");
        body.put("answer", answer);
        body.put("citations", citationSummaries == null ? List.of() : List.copyOf(citationSummaries));
        body.put("coverage", coverage == null ? Map.of() : Map.copyOf(coverage));
        body.put("conflict", conflict);
        body.put("classification", message.classification());
        body.put("request_id", message.requestId());
        return body;
    }

    private Map<String, Object> storedCoverage(ChatMessageRecord message) {
        if (message.coverageJson() == null || message.coverageJson().isBlank()) {
            return Map.of(
                    "successful", List.of(),
                    "failed", List.of(),
                    "timed_out", List.of());
        }
        try {
            Map<String, Object> coverage =
                    objectMapper.readValue(
                            message.coverageJson(), new TypeReference<Map<String, Object>>() {});
            return coverage == null ? Map.of() : Map.copyOf(coverage);
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
}
