package com.atlas.knowledgebase.issues;

import java.time.Instant;

/** Immutable persistence row for an issue report and its separately governed reporter note. */
public record IssueReportRecord(
        String issueId,
        String userId,
        String messageId,
        String citationId,
        String category,
        String diagnosticsJson,
        String reportNote,
        String routeTarget,
        Instant createdAt) {}
