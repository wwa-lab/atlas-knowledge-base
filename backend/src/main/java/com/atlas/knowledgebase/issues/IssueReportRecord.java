package com.atlas.knowledgebase.issues;

import java.time.Instant;

/** Immutable persistence row for the content-free issue report. */
public record IssueReportRecord(
        String issueId,
        String userId,
        String messageId,
        String citationId,
        String category,
        String diagnosticsJson,
        String routeTarget,
        Instant createdAt) {}
