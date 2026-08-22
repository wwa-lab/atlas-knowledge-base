package com.atlas.knowledgebase.issues;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.chat.ChatMessageRecord;
import com.atlas.knowledgebase.chat.ChatMessageRepository;
import com.atlas.knowledgebase.evidence.CitationRecord;
import com.atlas.knowledgebase.evidence.CitationRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Classifies and routes content-free issue reports without forwarding answer/source bodies. */
@Service
public class IssueService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final int MAX_NOTE_LENGTH = 1000;

    private final IssueReportRepository reports;
    private final ChatMessageRepository messages;
    private final CitationRepository citations;
    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final KbAccessService access;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IssueService(
            IssueReportRepository reports,
            ChatMessageRepository messages,
            CitationRepository citations,
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            KbAccessService access,
            AuditEventRepository auditEvents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.reports = reports;
        this.messages = messages;
        this.citations = citations;
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.access = access;
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(AtlasUserRecord user, CreateIssueCommand command) {
        if (command == null) {
            throw IssueException.validation("ISSUE_BODY_REQUIRED", "An issue report body is required.");
        }
        String messageId = optionalIdentifier(command.messageId(), "message_id");
        String citationId = optionalIdentifier(command.citationId(), "citation_id");
        if (messageId == null && citationId == null) {
            throw IssueException.validation(
                    "ISSUE_CONTEXT_REQUIRED", "Provide the assistant message_id or citation_id that has the issue.");
        }
        IssueCategory category = IssueCategory.parse(command.category());
        String note = normalizeNote(command.note());

        ChatMessageRecord message =
                messageId == null
                        ? null
                        : messages
                                .findOwnedAssistantById(messageId, user.userId())
                                .orElseThrow(
                                        () ->
                                                IssueException.notFound(
                                                        "ISSUE_MESSAGE_NOT_FOUND",
                                                        "The assistant message is not available to this user."));
        CitationRecord citation =
                citationId == null
                        ? null
                        : citations
                                .findOwnedByCitationId(citationId, user.userId())
                                .orElseThrow(
                                        () ->
                                                IssueException.notFound(
                                                        "ISSUE_CITATION_NOT_FOUND",
                                                        "The citation is not available to this user."));
        if (citation != null && message == null) {
            message =
                    messages
                            .findOwnedAssistantById(citation.messageId(), user.userId())
                            .orElseThrow(
                                    () ->
                                            IssueException.notFound(
                                                    "ISSUE_MESSAGE_NOT_FOUND",
                                                    "The citation's assistant message is not available."));
        }
        if (citation != null && !citation.messageId().equals(message.messageId())) {
            throw IssueException.validation(
                    "ISSUE_CONTEXT_MISMATCH", "message_id and citation_id must belong to the same answer.");
        }
        if ((category == IssueCategory.CONTENT || category == IssueCategory.CITATION)
                && citation == null) {
            throw IssueException.validation(
                    "CITATION_REQUIRED", "Content and citation issues require a citation_id.");
        }

        Context context = context(message, citation, user);
        String routeTarget = routeTarget(category, context.provider());
        String issueId = "iss_" + SessionService.randomToken().substring(0, 16);
        Map<String, Object> diagnostics = diagnostics(issueId, message, citation, context, note);
        reports.insert(
                new IssueReportRecord(
                        issueId,
                        user.userId(),
                        message.messageId(),
                        citation == null ? null : citation.citationId(),
                        category.wireName(),
                        writeJson(diagnostics),
                        routeTarget,
                        clock.instant()));
        audit(user, issueId, category, routeTarget, context);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("issue_id", issueId);
        response.put("route_target", routeTarget);
        response.put("diagnostics", diagnostics);
        return response;
    }

    private Context context(
            ChatMessageRecord message, CitationRecord citation, AtlasUserRecord user) {
        String logicalKbId = citation == null ? firstString(message.logicalKbScopeJson()) : citation.logicalKbId();
        String bindingId = citation == null ? firstBindingId(message.bindingSetJson()) : citation.bindingId();
        BindingRecord binding = bindingId == null ? null : bindings.findById(bindingId).orElse(null);
        if (logicalKbId == null && binding != null) {
            logicalKbId = binding.logicalKbId();
        }
        String provider = citation == null ? binding == null ? null : binding.providerProfile() : citation.provider();
        LogicalKnowledgeBaseRecord kb =
                logicalKbId == null ? null : knowledgeBases.findById(logicalKbId).orElse(null);
        String authorizationResult =
                kb == null ? "unknown" : access.authorized(user, kb) ? "allow" : "deny";
        String requestId = message.requestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = "req_" + SessionService.randomToken().substring(0, 16);
        }
        return new Context(
                requestId,
                logicalKbId,
                bindingId,
                provider,
                message.status() == null ? "unknown" : message.status(),
                authorizationResult);
    }

    private Map<String, Object> diagnostics(
            String issueId,
            ChatMessageRecord message,
            CitationRecord citation,
            Context context,
            String note) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("request_id", context.requestId());
        if (message != null) {
            diagnostics.put("message_id", message.messageId());
        }
        if (citation != null) {
            diagnostics.put("citation_id", citation.citationId());
        }
        if (context.logicalKbId() != null) {
            diagnostics.put("logical_kb_id", context.logicalKbId());
        }
        if (context.bindingId() != null) {
            diagnostics.put("binding_id", context.bindingId());
        }
        if (context.provider() != null) {
            diagnostics.put("provider", context.provider());
        }
        diagnostics.put("status", context.status());
        diagnostics.put("authorization_result", context.authorizationResult());
        if (note != null) {
            diagnostics.put("note", note);
        }
        diagnostics.put("issue_id", issueId);
        return Map.copyOf(diagnostics);
    }

    private void audit(
            AtlasUserRecord user,
            String issueId,
            IssueCategory category,
            String routeTarget,
            Context context) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("issue_id", issueId);
        details.put("category", category.wireName());
        details.put("route_target", routeTarget);
        auditEvents.insert(
                new AuditEventRecord(
                        "aud_" + SessionService.randomToken().substring(0, 16),
                        clock.instant(),
                        user.userId(),
                        context.logicalKbId(),
                        context.bindingId(),
                        context.provider(),
                        "issue_report",
                        context.authorizationResult(),
                        null,
                        null,
                        null,
                        "routed",
                        category.wireName(),
                        writeJson(details)));
    }

    private String routeTarget(IssueCategory category, String provider) {
        return switch (category) {
            case CONTENT, CITATION ->
                    switch (provider == null ? "" : provider.toLowerCase(Locale.ROOT)) {
                        case "git_markdown" -> "kb_correct_flow";
                        case "confluence" -> "confluence_original_flow";
                        case "dify" -> "kb_owner_remediation";
                        default -> "atlas_team";
                    };
            case PERMISSION_CONNECTION -> "connector_owner";
            case RETRIEVAL, MODEL -> "atlas_team";
            case SYSTEM_SECURITY -> "security_process";
        };
    }

    private String optionalIdentifier(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw IssueException.validation("IDENTIFIER_INVALID", field + " is not a valid identifier.");
        }
        return normalized;
    }

    private String normalizeNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_NOTE_LENGTH) {
            throw IssueException.validation(
                    "NOTE_TOO_LONG", "note must be at most " + MAX_NOTE_LENGTH + " characters.");
        }
        return normalized;
    }

    private String firstString(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "[]" : json);
            if (root != null && root.isArray() && !root.isEmpty() && root.get(0).isTextual()) {
                return root.get(0).asText();
            }
        } catch (JsonProcessingException ignored) {
            // The chat row is authoritative only when it remains parseable; diagnostics stay minimal.
        }
        return null;
    }

    private String firstBindingId(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "[]" : json);
            if (root != null && root.isArray() && !root.isEmpty()) {
                JsonNode bindingId = root.get(0).path("binding_id");
                if (bindingId.isTextual() && !bindingId.asText().isBlank()) {
                    return bindingId.asText();
                }
            }
        } catch (JsonProcessingException ignored) {
            // The chat row is authoritative only when it remains parseable; diagnostics stay minimal.
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize content-free issue details", exception);
        }
    }

    public record CreateIssueCommand(String messageId, String citationId, String category, String note) {}

    private record Context(
            String requestId,
            String logicalKbId,
            String bindingId,
            String provider,
            String status,
            String authorizationResult) {}
}
