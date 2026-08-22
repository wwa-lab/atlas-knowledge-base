package com.atlas.knowledgebase.evidence;

import com.atlas.knowledgebase.access.KbAccessService;
import com.atlas.knowledgebase.adapters.EvidenceResolver;
import com.atlas.knowledgebase.chat.ChatMessageRecord;
import com.atlas.knowledgebase.chat.ChatMessageRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Private, fail-closed Evidence Drawer and exact-original orchestration. */
@Service
public final class EvidenceService {

    private static final Pattern CITATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final String VIEW = "evidence_view";
    private static final String OPEN = "evidence_open";

    private final CitationRepository citations;
    private final ChatMessageRepository messages;
    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final KbAccessService access;
    private final EvidenceLocatorValidator locatorValidator;
    private final EvidenceSourceContinuity continuity;
    private final EvidenceResolverRegistry resolvers;
    private final EvidenceNavigationPolicy navigationPolicy;
    private final EvidenceAuditService audit;
    private final ObjectMapper objectMapper;

    public EvidenceService(
            CitationRepository citations,
            ChatMessageRepository messages,
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            KbAccessService access,
            EvidenceLocatorValidator locatorValidator,
            EvidenceSourceContinuity continuity,
            EvidenceResolverRegistry resolvers,
            EvidenceNavigationPolicy navigationPolicy,
            EvidenceAuditService audit,
            ObjectMapper objectMapper) {
        this.citations = citations;
        this.messages = messages;
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.access = access;
        this.locatorValidator = locatorValidator;
        this.continuity = continuity;
        this.resolvers = resolvers;
        this.navigationPolicy = navigationPolicy;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> drawer(AtlasUserRecord user, String citationId) {
        Prepared prepared = prepare(user, citationId, VIEW, EvidenceResolver.Operation.INSPECT);
        EvidenceResolver.Result result = resolve(user, prepared, VIEW);
        if (result.status() == EvidenceResolver.Status.UNKNOWN) {
            throw unknownAfterAudit(user, prepared.citation(), VIEW, result.verificationMode());
        }
        audit.owned(
                user.userId(),
                prepared.citation(),
                VIEW,
                "allow",
                result.status().wireValue(),
                null);
        return drawerProjection(prepared, result);
    }

    public Map<String, Object> openOriginal(
            AtlasUserRecord user, String citationId, JsonNode requestBody) {
        if (requestBody != null && (!requestBody.isObject() || !requestBody.isEmpty())) {
            audit.generic(user.userId(), OPEN, "not_evaluated", "invalid", "validation");
            throw error(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation",
                    "EVIDENCE_OPEN_BODY_INVALID",
                    "Open original does not accept locator, version, URL, or other request fields.",
                    "remove_request_fields",
                    Map.of());
        }
        Prepared prepared = prepare(user, citationId, OPEN, EvidenceResolver.Operation.OPEN);
        EvidenceResolver.Result result = resolve(user, prepared, OPEN);
        return switch (result.status()) {
            case OK -> openOk(user, prepared, result);
            case MOVED -> throw movedAfterAudit(user, prepared, result);
            case UNAVAILABLE -> throw unavailableAfterAudit(user, prepared, result);
            case UNKNOWN -> throw unknownAfterAudit(
                    user, prepared.citation(), OPEN, result.verificationMode());
        };
    }

    private Prepared prepare(
            AtlasUserRecord user,
            String citationId,
            String action,
            EvidenceResolver.Operation operation) {
        if (user == null || citationId == null || !CITATION_ID.matcher(citationId).matches()) {
            if (user != null) {
                audit.generic(user.userId(), action, "not_found", "not_found", "unavailable");
            }
            throw notFound();
        }
        CitationRecord citation =
                citations
                        .findOwnedByCitationId(citationId, user.userId())
                        .orElseThrow(
                                () -> {
                                    audit.generic(
                                            user.userId(),
                                            action,
                                            "not_found",
                                            "not_found",
                                            "unavailable");
                                    return notFound();
                                });
        try {
            requireCitationMetadata(citation);
            LogicalKnowledgeBaseRecord kb =
                    knowledgeBases.findById(citation.logicalKbId()).orElseThrow(UnknownState::new);
            BindingRecord binding =
                    bindings.findById(citation.bindingId()).orElseThrow(UnknownState::new);
            if (!binding.logicalKbId().equals(citation.logicalKbId())
                    || !binding.providerProfile().equals(citation.provider())
                    || kb.name() == null
                    || kb.name().isBlank()) {
                throw new UnknownState();
            }
            if (!access.authorized(user, kb)
                    || !binding.enabled()
                    || binding.killSwitch()
                    || !binding.featureFlag()) {
                throw denied(user, citation, action);
            }
            EvidenceLocatorValidator.ValidatedLocator locator =
                    locatorValidator.validate(citation.provider(), citation.locatorJson());
            EvidenceSourceContinuity.Check continuityCheck =
                    continuity.check(locator, binding.sourceIdentityJson());
            if (!continuityCheck.continuous()) {
                throw new UnknownState();
            }
            JsonNode sourceIdentity = objectMapper.readTree(binding.sourceIdentityJson());
            if (sourceIdentity == null || !sourceIdentity.isObject()) {
                throw new UnknownState();
            }
            String bindingRole = bindingRole(citation);
            requireText(binding.authMethod());
            EvidenceResolver.AuthorizationContext authorizationContext =
                    new EvidenceResolver.AuthorizationContext(
                            user.userId(), binding.bindingId(), binding.authMethod());
            EvidenceResolver resolver =
                    resolvers.find(citation.provider()).orElseThrow(UnknownState::new);
            EvidenceResolver.AuthorizationResult authorization =
                    resolver.authorize(
                            new EvidenceResolver.AuthorizationRequest(
                                    citation.provider(),
                                    locator,
                                    sourceIdentity,
                                    authorizationContext));
            if (authorization.outcome() == EvidenceResolver.AuthorizationOutcome.ACCESS_DENIED) {
                throw denied(user, citation, action);
            }
            if (authorization.outcome() != EvidenceResolver.AuthorizationOutcome.AUTHORIZED) {
                throw new UnknownState();
            }
            return new Prepared(
                    citation,
                    kb,
                    binding,
                    bindingRole,
                    locator,
                    sourceIdentity,
                    authorizationContext,
                    resolver,
                    operation);
        } catch (EvidenceException exception) {
            throw exception;
        } catch (UnknownState
                | EvidenceLocatorValidator.InvalidLocatorException
                | JsonProcessingException exception) {
            audit.owned(user.userId(), citation, action, "unknown", "unknown", "unknown");
            throw unknown(EvidenceResolver.VerificationMode.NONE);
        }
    }

    private EvidenceResolver.Result resolve(
            AtlasUserRecord user, Prepared prepared, String action) {
        EvidenceResolver.Result result;
        try {
            result =
                    prepared.resolver()
                            .resolve(
                                    new EvidenceResolver.Request(
                                            prepared.citation().provider(),
                                            prepared.locator(),
                                            prepared.sourceIdentity(),
                                            prepared.authorizationContext(),
                                            prepared.operation()));
            requireResultBoundary(prepared.locator(), result);
        } catch (RuntimeException exception) {
            EvidenceResolver.VerificationMode mode =
                    prepared.locator().fixtureMarked()
                            ? EvidenceResolver.VerificationMode.FIXTURE
                            : EvidenceResolver.VerificationMode.PROVIDER;
            audit.owned(
                    user.userId(),
                    prepared.citation(),
                    action,
                    "unknown",
                    "unknown",
                    "unknown");
            throw unknown(mode);
        }
        return result;
    }

    private Map<String, Object> drawerProjection(
            Prepared prepared, EvidenceResolver.Result result) {
        CitationRecord citation = prepared.citation();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("citation_id", citation.citationId());
        body.put("excerpt", citation.excerpt());
        body.put("logical_kb_id", citation.logicalKbId());
        body.put("logical_kb_name", prepared.kb().name());
        body.put("provider", citation.provider());
        body.put("binding_id", citation.bindingId());
        body.put("binding_role", prepared.bindingRole());
        body.put("version", citation.versionLabel());
        body.put("locator", prepared.locator().locator());
        body.put("document_title", citation.documentTitle());
        body.put("owner", citation.owner());
        body.put("classification", citation.classification());
        body.put("source_updated_at", instant(citation.sourceUpdatedAt()));
        body.put("atlas_verified_at", instant(citation.atlasVerifiedAt()));
        body.put("resolve_status", result.status().wireValue());
        body.put("verification_mode", result.verificationMode().wireValue());
        body.put("provider_verified", result.providerVerified());
        body.put(
                "open_original_action",
                Map.of(
                        "method", "POST",
                        "path", "/api/v1/citations/" + citation.citationId() + "/open-original",
                        "requires_csrf", true));
        return body;
    }

    private Map<String, Object> openOk(
            AtlasUserRecord user, Prepared prepared, EvidenceResolver.Result result) {
        if (result.navigationUrl() == null) {
            throw unknownAfterAudit(
                    user, prepared.citation(), OPEN, result.verificationMode());
        }
        try {
            if (result.verificationMode() == EvidenceResolver.VerificationMode.FIXTURE) {
                navigationPolicy.requireFixtureNavigation(result.navigationUrl());
            } else {
                navigationPolicy.requireTrustedProviderNavigation(
                        result.navigationUrl(), result.trustedOrigin());
            }
        } catch (IllegalArgumentException exception) {
            throw unknownAfterAudit(
                    user, prepared.citation(), OPEN, result.verificationMode());
        }
        audit.owned(
                user.userId(), prepared.citation(), OPEN, "allow", "ok", null);
        return Map.of(
                "navigation_url", result.navigationUrl(),
                "resolve_status", "ok",
                "verification_mode", result.verificationMode().wireValue(),
                "provider_verified", result.providerVerified());
    }

    private EvidenceException movedAfterAudit(
            AtlasUserRecord user, Prepared prepared, EvidenceResolver.Result result) {
        Map<String, Object> details = verificationDetails(result);
        details.put("provider", prepared.citation().provider());
        JsonNode stableSourceId = prepared.locator().locator().get("stable_source_id");
        if (stableSourceId != null && stableSourceId.isTextual()) {
            details.put("stable_source_id", stableSourceId.textValue());
        }
        if (result.providerVerified() && result.movedToLocator().isPresent()) {
            details.put("moved_to_locator_id", movedLocatorId(result.movedToLocator().orElseThrow()));
        }
        audit.owned(user.userId(), prepared.citation(), OPEN, "allow", "moved", "moved");
        return error(
                HttpStatus.CONFLICT,
                "moved",
                "EVIDENCE_MOVED",
                "The cited immutable locator is no longer canonical; no newer content was opened.",
                "inspect_move_mapping_or_ask_owner",
                details);
    }

    private EvidenceException unavailableAfterAudit(
            AtlasUserRecord user, Prepared prepared, EvidenceResolver.Result result) {
        audit.owned(
                user.userId(),
                prepared.citation(),
                OPEN,
                "allow",
                "unavailable",
                "unavailable");
        return error(
                HttpStatus.GONE,
                "unavailable",
                "EVIDENCE_UNAVAILABLE",
                "The cited immutable version is deleted, not retained, or cannot be resolved with a verifiable move mapping.",
                "ask_owner_or_retry_later",
                verificationDetails(result));
    }

    private EvidenceException unknownAfterAudit(
            AtlasUserRecord user,
            CitationRecord citation,
            String action,
            EvidenceResolver.VerificationMode mode) {
        audit.owned(user.userId(), citation, action, "unknown", "unknown", "unknown");
        return unknown(mode);
    }

    private EvidenceException denied(
            AtlasUserRecord user, CitationRecord citation, String action) {
        audit.owned(user.userId(), citation, action, "deny", "denied", "authorization");
        audit.owned(
                user.userId(),
                citation,
                "authorization_denied",
                "deny",
                "denied",
                "authorization");
        return error(
                HttpStatus.FORBIDDEN,
                "authorization",
                "EVIDENCE_ACCESS_DENIED",
                "Current knowledge-base or source authorization denies this evidence operation.",
                "request_access_or_reconnect",
                Map.of());
    }

    private void requireCitationMetadata(CitationRecord citation) {
        requireText(citation.versionLabel());
        requireText(citation.excerpt());
        requireText(citation.documentTitle());
        requireText(citation.owner());
        requireText(citation.classification());
        requireText(citation.resolveStatus());
        requireText(citation.locatorJson());
        if (citation.atlasVerifiedAt() == null) {
            throw new UnknownState();
        }
    }

    private String bindingRole(CitationRecord citation) throws JsonProcessingException {
        ChatMessageRecord message =
                messages.findById(citation.messageId()).orElseThrow(UnknownState::new);
        JsonNode snapshots = objectMapper.readTree(message.bindingSetJson());
        if (snapshots == null || !snapshots.isArray()) {
            throw new UnknownState();
        }
        for (JsonNode snapshot : snapshots) {
            if (snapshot.isObject()
                    && citation.bindingId().equals(snapshot.path("binding_id").asText(null))) {
                String role = snapshot.path("binding_role").asText(null);
                requireText(role);
                return role;
            }
        }
        throw new UnknownState();
    }

    private void requireResultBoundary(
            EvidenceLocatorValidator.ValidatedLocator locator, EvidenceResolver.Result result) {
        if (result == null) {
            throw new IllegalArgumentException("resolver result is required");
        }
        boolean fixtureResult =
                result.verificationMode() == EvidenceResolver.VerificationMode.FIXTURE;
        if (locator.fixtureMarked() != fixtureResult) {
            throw new IllegalArgumentException("fixture and provider result boundaries do not match");
        }
        if (result.status() == EvidenceResolver.Status.MOVED) {
            locatorValidator.validateMoveTarget(
                    locator, result.movedToLocator().orElseThrow());
        }
    }

    private static Map<String, Object> verificationDetails(EvidenceResolver.Result result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("verification_mode", result.verificationMode().wireValue());
        details.put("provider_verified", result.providerVerified());
        return details;
    }

    private String movedLocatorId(JsonNode locator) {
        String canonical = canonical(locator);
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "loc_sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String canonical(JsonNode node) {
        if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            node.fields().forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            StringBuilder value = new StringBuilder("{");
            for (int index = 0; index < fields.size(); index++) {
                if (index > 0) {
                    value.append(',');
                }
                value.append(jsonString(fields.get(index).getKey()))
                        .append(':')
                        .append(canonical(fields.get(index).getValue()));
            }
            return value.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder value = new StringBuilder("[");
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) {
                    value.append(',');
                }
                value.append(canonical(node.get(index)));
            }
            return value.append(']').toString();
        }
        if (node.isTextual()) {
            return jsonString(node.textValue());
        }
        return node.toString();
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to canonicalize evidence locator", exception);
        }
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new UnknownState();
        }
    }

    private static EvidenceException notFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "unavailable",
                "EVIDENCE_NOT_FOUND",
                "The requested evidence is unavailable.",
                "return_to_answer",
                Map.of());
    }

    private static EvidenceException unknown(EvidenceResolver.VerificationMode mode) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "unknown",
                "EVIDENCE_RESOLUTION_UNKNOWN",
                "Atlas cannot safely verify the cited immutable version.",
                "retry_later",
                Map.of(
                        "verification_mode", mode.wireValue(),
                        "provider_verified", false));
    }

    private static EvidenceException error(
            HttpStatus status,
            String category,
            String code,
            String message,
            String nextStep,
            Map<String, Object> details) {
        return new EvidenceException(status, category, code, message, nextStep, details);
    }

    private record Prepared(
            CitationRecord citation,
            LogicalKnowledgeBaseRecord kb,
            BindingRecord binding,
            String bindingRole,
            EvidenceLocatorValidator.ValidatedLocator locator,
            JsonNode sourceIdentity,
            EvidenceResolver.AuthorizationContext authorizationContext,
            EvidenceResolver resolver,
            EvidenceResolver.Operation operation) {}

    private static final class UnknownState extends RuntimeException {}
}
