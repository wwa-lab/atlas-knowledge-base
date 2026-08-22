package com.atlas.knowledgebase.evidence;

import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/** Writes the content-free Evidence audit allow-list from ADR-0008. */
@Service
public final class EvidenceAuditService {

    private final AuditEventRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EvidenceAuditService(
            AuditEventRepository events, ObjectMapper objectMapper, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void generic(
            String userId,
            String action,
            String authorizationResult,
            String status,
            String errorCategory) {
        insert(userId, null, action, authorizationResult, status, errorCategory);
    }

    public void owned(
            String userId,
            CitationRecord citation,
            String action,
            String authorizationResult,
            String status,
            String errorCategory) {
        insert(userId, citation, action, authorizationResult, status, errorCategory);
    }

    private void insert(
            String userId,
            CitationRecord citation,
            String action,
            String authorizationResult,
            String status,
            String errorCategory) {
        events.insert(
                new AuditEventRecord(
                        "aud_" + SessionService.randomToken().substring(0, 16),
                        clock.instant(),
                        userId,
                        citation == null ? null : citation.logicalKbId(),
                        citation == null ? null : citation.bindingId(),
                        citation == null ? null : citation.provider(),
                        action,
                        authorizationResult,
                        citation == null ? null : citationIds(citation.citationId()),
                        null,
                        null,
                        status,
                        errorCategory,
                        null));
    }

    private String citationIds(String citationId) {
        try {
            return objectMapper.writeValueAsString(List.of(citationId));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize evidence audit identifiers", exception);
        }
    }
}
