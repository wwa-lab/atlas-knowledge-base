package com.atlas.knowledgebase.evidence;

import java.time.Instant;

/** Immutable persistence row for {@code citation}. */
public record CitationRecord(
        String citationId,
        String messageId,
        String logicalKbId,
        String bindingId,
        String provider,
        String locatorJson,
        String versionLabel,
        String excerpt,
        String documentTitle,
        String owner,
        String classification,
        Instant sourceUpdatedAt,
        Instant atlasVerifiedAt,
        String resolveStatus) {}
