package com.atlas.knowledgebase.registry;

import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.session.AtlasRoles;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner wizard create/update for Draft logical KBs. Connection test, content audit, submit, and
 * activation stay on TASK-012.
 */
@Service
public class RegistryService {

    private static final Set<String> DISCOVERABILITY = Set.of("catalog", "private");
    private static final Set<String> PROVIDERS = Set.of("dify", "git_markdown", "confluence");
    private static final Set<String> BINDING_ROLES = Set.of("canonical", "mirror", "supplemental");
    private static final Set<String> AUTH_METHODS = Set.of("delegated_user", "sso_group_mapping");

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RegistryService(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            AuditEventRepository auditEvents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public LogicalKnowledgeBaseRecord createDraft(AtlasUserRecord user, CreateDraftCommand command) {
        requireKbOwner(user);
        validateBasics(
                command.name(),
                command.description(),
                command.discoverability(),
                command.purpose(),
                command.classification());
        Instant now = clock.instant();
        String logicalKbId = "lkb_" + SessionService.randomToken().substring(0, 16);
        LogicalKnowledgeBaseRecord created =
                knowledgeBases.insert(
                        new LogicalKnowledgeBaseRecord(
                                logicalKbId,
                                command.name().trim(),
                                trimToNull(command.description()),
                                user.userId(),
                                command.discoverability(),
                                command.purpose().trim(),
                                command.classification().trim(),
                                command.modelEligible(),
                                command.modelEligible() ? "chat_ready" : "browse_only",
                                "draft",
                                "healthy",
                                1,
                                null,
                                false,
                                null,
                                now,
                                now,
                                null));
        audit(user.userId(), created.logicalKbId(), null, "register_draft", "allowed", "success");
        return created;
    }

    @Transactional
    public LogicalKnowledgeBaseRecord updateDraft(AtlasUserRecord user, UpdateDraftCommand command) {
        requireKbOwner(user);
        LogicalKnowledgeBaseRecord current =
                knowledgeBases
                        .findById(command.logicalKbId())
                        .orElseThrow(() -> new DraftNotFoundException(command.logicalKbId()));
        if (!user.userId().equals(current.ownerUserId())) {
            throw new RegistryForbiddenException(
                    "NOT_DRAFT_OWNER",
                    "Only the knowledge-base Owner can update this draft.",
                    "open_own_draft");
        }
        if (!"draft".equals(current.lifecycle())) {
            throw new DraftValidationException(
                    "NOT_A_DRAFT", "Only Draft knowledge bases can be updated through the wizard.");
        }
        String name = command.name() != null ? command.name() : current.name();
        String description =
                command.description() != null ? command.description() : current.description();
        String discoverability =
                command.discoverability() != null
                        ? command.discoverability()
                        : current.discoverability();
        String purpose = command.purpose() != null ? command.purpose() : current.purpose();
        String classification =
                command.classification() != null
                        ? command.classification()
                        : current.classification();
        boolean modelEligible =
                command.modelEligible() != null ? command.modelEligible() : current.modelEligible();
        validateBasics(name, description, discoverability, purpose, classification);

        String capability = modelEligible ? "chat_ready" : "browse_only";
        List<ResolvedBinding> resolved = null;
        if (command.bindings() != null) {
            resolved = resolveBindings(user, current.logicalKbId(), command.bindings());
            rejectIncompatibleBindings(resolved);
            if (mixedModelEligibility(resolved, modelEligible)) {
                capability = "browse_only";
            }
        }

        LogicalKnowledgeBaseRecord updated =
                knowledgeBases.updateDraft(
                        current.logicalKbId(),
                        command.configVersion(),
                        new LogicalKnowledgeBaseDraft(
                                name.trim(),
                                trimToNull(description),
                                current.ownerUserId(),
                                discoverability,
                                purpose.trim(),
                                classification.trim(),
                                modelEligible,
                                capability,
                                current.health(),
                                current.maxStaleness(),
                                current.freshnessRequired(),
                                current.accessRequestUrl()));
        if (resolved != null) {
            replaceBindings(updated.logicalKbId(), resolved);
        }
        audit(user.userId(), updated.logicalKbId(), null, "update_draft", "allowed", "success");
        return updated;
    }

    private void requireKbOwner(AtlasUserRecord user) {
        if (!AtlasRoles.has(user, AtlasRoles.KB_OWNER)) {
            throw new RegistryForbiddenException(
                    "KB_OWNER_REQUIRED",
                    "Only a verified knowledge-base Owner can register Drafts.",
                    "request_kb_owner_role");
        }
    }

    private void validateBasics(
            String name, String description, String discoverability, String purpose, String classification) {
        if (name == null || name.isBlank()) {
            throw new DraftValidationException("NAME_REQUIRED", "A knowledge-base name is required.");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new DraftValidationException("PURPOSE_REQUIRED", "A knowledge-base purpose is required.");
        }
        if (classification == null || classification.isBlank()) {
            throw new DraftValidationException(
                    "CLASSIFICATION_REQUIRED", "A security classification is required.");
        }
        if (discoverability == null || !DISCOVERABILITY.contains(discoverability)) {
            throw new DraftValidationException(
                    "DISCOVERABILITY_INVALID", "discoverability must be catalog or private.");
        }
        if (description != null && description.length() > 4000) {
            throw new DraftValidationException("DESCRIPTION_TOO_LONG", "Description is too long.");
        }
    }

    private List<ResolvedBinding> resolveBindings(
            AtlasUserRecord user, String logicalKbId, List<BindingInput> inputs) {
        List<ResolvedBinding> resolved = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (BindingInput input : inputs) {
            if (input == null) {
                throw new DraftValidationException("BINDING_REQUIRED", "Binding entries cannot be empty.");
            }
            String provider = normalize(input.providerProfile());
            if (!PROVIDERS.contains(provider)) {
                throw new DraftValidationException(
                        "PROVIDER_INVALID", "provider_profile must be dify, git_markdown, or confluence.");
            }
            String role = normalize(input.role());
            if (!BINDING_ROLES.contains(role)) {
                throw new DraftValidationException(
                        "BINDING_ROLE_INVALID", "role must be canonical, mirror, or supplemental.");
            }
            String auth = normalize(input.authMethod() == null ? "delegated_user" : input.authMethod());
            if (!AUTH_METHODS.contains(auth)) {
                throw new DraftValidationException(
                        "AUTH_METHOD_INVALID",
                        "auth_method must be delegated_user or sso_group_mapping.");
            }
            String bindingId = input.bindingId();
            if (bindingId == null || bindingId.isBlank()) {
                bindingId = "bnd_" + SessionService.randomToken().substring(0, 16);
            } else {
                BindingRecord existing = bindings.findById(bindingId).orElse(null);
                if (existing != null && !logicalKbId.equals(existing.logicalKbId())) {
                    throw new DraftValidationException(
                            "BINDING_ID_IN_USE", "binding_id belongs to another knowledge base.");
                }
            }
            if (input.sourceIdentity() == null || input.sourceIdentity().isEmpty()) {
                throw new DraftValidationException(
                        "SOURCE_IDENTITY_REQUIRED", "Each binding must declare a source_identity.");
            }
            if (!seenIds.add(bindingId)) {
                throw new DraftValidationException("BINDING_ID_DUPLICATE", "Duplicate binding_id in request.");
            }
            String credentialOwner =
                    input.credentialOwner() == null || input.credentialOwner().isBlank()
                            ? user.userId()
                            : input.credentialOwner().trim();
            resolved.add(
                    new ResolvedBinding(
                            bindingId,
                            provider,
                            writeJson(input.sourceIdentity(), "{}"),
                            role,
                            auth,
                            writeJson(input.freshnessPolicy(), null),
                            writeJson(input.locatorRules(), "{}"),
                            credentialOwner,
                            writeJson(input.regionConstraints(), null),
                            input.modelEligible()));
        }
        return resolved;
    }

    private void rejectIncompatibleBindings(List<ResolvedBinding> resolved) {
        if (resolved.isEmpty()) {
            return;
        }
        long canonical = resolved.stream().filter(b -> "canonical".equals(b.role())).count();
        if (canonical != 1) {
            throw new DraftValidationException(
                    "CANONICAL_REQUIRED", "A draft with sources must have exactly one canonical binding.");
        }
        String owner = resolved.getFirst().credentialOwner();
        JsonNode region = parseJson(resolved.getFirst().regionConstraintsJson());
        for (ResolvedBinding binding : resolved) {
            if (!owner.equals(binding.credentialOwner())) {
                throw new DraftValidationException(
                        "INCOMPATIBLE_BINDINGS",
                        "Bindings on one knowledge base must share one credential Owner.");
            }
            JsonNode other = parseJson(binding.regionConstraintsJson());
            if (!regionEquals(region, other)) {
                throw new DraftValidationException(
                        "INCOMPATIBLE_BINDINGS",
                        "Bindings on one knowledge base must share one region, retention, and egress boundary.");
            }
        }
    }

    private static boolean mixedModelEligibility(List<ResolvedBinding> resolved, boolean kbEligible) {
        boolean sawTrue = kbEligible;
        boolean sawFalse = !kbEligible;
        for (ResolvedBinding binding : resolved) {
            if (binding.modelEligible() == null) {
                continue;
            }
            if (binding.modelEligible()) {
                sawTrue = true;
            } else {
                sawFalse = true;
            }
        }
        return sawTrue && sawFalse;
    }

    private String writeJson(Object value, String emptyDefault) {
        if (value == null) {
            return emptyDefault;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new DraftValidationException("JSON_INVALID", "Could not encode binding JSON fields.");
        }
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static boolean regionEquals(JsonNode left, JsonNode right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private void replaceBindings(String logicalKbId, List<ResolvedBinding> resolved) {
        bindings.deleteByLogicalKbId(logicalKbId);
        Instant now = clock.instant();
        for (ResolvedBinding binding : resolved) {
            bindings.insert(
                    new BindingRecord(
                            binding.bindingId(),
                            logicalKbId,
                            binding.providerProfile(),
                            binding.sourceIdentityJson(),
                            binding.role(),
                            binding.authMethod(),
                            "healthy",
                            true,
                            false,
                            true,
                            binding.freshnessPolicyJson(),
                            binding.locatorRulesJson(),
                            binding.credentialOwner(),
                            binding.regionConstraintsJson(),
                            1,
                            now,
                            now));
        }
    }

    private void audit(
            String userId,
            String logicalKbId,
            String bindingId,
            String action,
            String authorization,
            String status) {
        auditEvents.insert(
                new AuditEventRecord(
                        "aud_" + SessionService.randomToken().substring(0, 16),
                        clock.instant(),
                        userId,
                        logicalKbId,
                        bindingId,
                        null,
                        action,
                        authorization,
                        null,
                        null,
                        null,
                        status,
                        null,
                        contentFreeDetails(logicalKbId)));
    }

    private String contentFreeDetails(String logicalKbId) {
        try {
            return objectMapper.writeValueAsString(Map.of("logical_kb_id", logicalKbId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize content-free audit details", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record CreateDraftCommand(
            String name,
            String description,
            String discoverability,
            String purpose,
            String classification,
            boolean modelEligible) {}

    public record UpdateDraftCommand(
            String logicalKbId,
            int configVersion,
            String name,
            String description,
            String discoverability,
            String purpose,
            String classification,
            Boolean modelEligible,
            List<BindingInput> bindings) {}

    public record BindingInput(
            @com.fasterxml.jackson.annotation.JsonProperty("binding_id") String bindingId,
            @com.fasterxml.jackson.annotation.JsonProperty("provider_profile") String providerProfile,
            @com.fasterxml.jackson.annotation.JsonProperty("source_identity")
                    Map<String, Object> sourceIdentity,
            String role,
            @com.fasterxml.jackson.annotation.JsonProperty("auth_method") String authMethod,
            @com.fasterxml.jackson.annotation.JsonProperty("credential_owner") String credentialOwner,
            @com.fasterxml.jackson.annotation.JsonProperty("freshness_policy")
                    Map<String, Object> freshnessPolicy,
            @com.fasterxml.jackson.annotation.JsonProperty("locator_rules")
                    Map<String, Object> locatorRules,
            @com.fasterxml.jackson.annotation.JsonProperty("region_constraints")
                    Map<String, Object> regionConstraints,
            @com.fasterxml.jackson.annotation.JsonProperty("model_eligible") Boolean modelEligible) {}

    private record ResolvedBinding(
            String bindingId,
            String providerProfile,
            String sourceIdentityJson,
            String role,
            String authMethod,
            String freshnessPolicyJson,
            String locatorRulesJson,
            String credentialOwner,
            String regionConstraintsJson,
            Boolean modelEligible) {}
}
