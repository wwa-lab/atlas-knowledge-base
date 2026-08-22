package com.atlas.knowledgebase.chat;

import java.time.Instant;

/** Persistence row for {@code chat_message}. */
public record ChatMessageRecord(
        String messageId,
        String threadId,
        String role,
        String status,
        String questionText,
        String answerText,
        String logicalKbScopeJson,
        String bindingSetJson,
        String configVersionsJson,
        String coverageJson,
        String conflictSectionJson,
        String classification,
        String requestId,
        Instant createdAt,
        Instant completedAt) {}
