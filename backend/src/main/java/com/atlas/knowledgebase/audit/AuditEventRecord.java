package com.atlas.knowledgebase.audit;

import java.time.Instant;

/** Persistence row for {@code audit_event}. Must not contain prompt/body/token material. */
public record AuditEventRecord(
        String eventId,
        Instant occurredAt,
        String userId,
        String logicalKbId,
        String bindingId,
        String connector,
        String action,
        String authorizationResult,
        String evidenceLocatorIdsJson,
        String modelId,
        Integer latencyMs,
        String status,
        String errorCategory,
        String detailsJson) {}
