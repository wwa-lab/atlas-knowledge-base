package com.atlas.knowledgebase.registry;

import java.time.Instant;

/** Persistence row for {@code content_audit_result}. */
public record ContentAuditResultRecord(
        String auditId,
        String logicalKbId,
        String bindingId,
        int totalCount,
        int chatEligibleCount,
        int excludedCount,
        String exclusionReasonsJson,
        String remediationBlobRef,
        Instant auditedAt) {}
