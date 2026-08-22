package com.atlas.knowledgebase.chat;

import java.time.Instant;

/** Persistence row for {@code chat_thread}. */
public record ChatThreadRecord(
        String threadId,
        String userId,
        String title,
        String selectedLogicalKbIdsJson,
        String branchedFromThreadId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {}
