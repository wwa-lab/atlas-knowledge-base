package com.atlas.knowledgebase.governance;

import com.atlas.knowledgebase.adapters.SourceProbe;
import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.registry.BindingConfigHistoryRecord;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.registry.RegistryForbiddenException;
import com.atlas.knowledgebase.retrieval.RetrievalEligibility;
import com.atlas.knowledgebase.retrieval.RetrievalProperties;
import com.atlas.knowledgebase.session.AtlasRoles;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-only, content-free runtime governance for bindings and logical KB lifecycle. */
@Service
public class GovernanceService {

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final AuditEventRepository auditEvents;
    private final GovernancePreviewClaimRepository previewClaims;
    private final AtlasUserRepository users;
    private final SourceProbe probe;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RetrievalProperties retrievalProperties;

    public GovernanceService(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            AuditEventRepository auditEvents,
            GovernancePreviewClaimRepository previewClaims,
            AtlasUserRepository users,
            SourceProbe probe,
            ObjectMapper objectMapper,
            Clock clock,
            RetrievalProperties retrievalProperties) {
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.auditEvents = auditEvents;
        this.previewClaims = previewClaims;
        this.users = users;
        this.probe = probe;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.retrievalProperties = retrievalProperties;
    }

    @Transactional
    public Map<String, Object> impactPreview(
            AtlasUserRecord user, String bindingId, String requestedOperation) {
        requireAdmin(user);
        BindingRecord binding = requireBinding(bindingId);
        LogicalKnowledgeBaseRecord kb = requireKnowledgeBase(binding.logicalKbId());
        Operation operation = Operation.parse(requestedOperation);
        BindingConfigHistoryRecord rollbackTarget = null;
        if (operation == Operation.ROLLBACK) {
            rollbackTarget =
                    bindings
                            .findLatestHistory(bindingId)
                            .orElseThrow(
                                    () ->
                                            new GovernanceConflictException(
                                                    "ROLLBACK_UNAVAILABLE",
                                                    "No prior binding configuration is available for rollback."));
        }

        String previewId = "imp_" + SessionService.randomToken().substring(0, 16);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("impact_preview_id", previewId);
        details.put("operation", operation.wireName);
        details.put("binding_id", binding.bindingId());
        details.put("logical_kb_id", binding.logicalKbId());
        details.put("config_version", binding.configVersion());
        details.put("enabled", binding.enabled());
        details.put("kill_switch", binding.killSwitch());
        details.put("feature_flag", binding.featureFlag());
        details.put("logical_kb_lifecycle", kb.lifecycle());
        details.put("affected_binding_count", 1);
        details.put("runtime_binding_ids", runtimeBindingIds(binding.logicalKbId()));
        details.put("unrelated_knowledge_bases_remain", true);
        // Disable and Kill Switch are runtime controls only; an explicit Retire preview is the
        // operation that predicts whether this binding is the last actually retrievable one.
        details.put(
                "would_retire_logical_kb",
                operation == Operation.RETIRE && wouldRetireKnowledgeBase(binding));
        if (rollbackTarget != null) {
            details.put("rollback_target_config_version", rollbackTarget.configVersion());
        }

        auditEvents.insert(
                new AuditEventRecord(
                        previewId,
                        clock.instant(),
                        user.userId(),
                        binding.logicalKbId(),
                        binding.bindingId(),
                        binding.providerProfile(),
                        "impact_preview",
                        "allowed",
                        null,
                        null,
                        null,
                        "previewed",
                        null,
                        writeDetails(details)));

        Map<String, Object> response = new LinkedHashMap<>(details);
        response.put("new_retrieval_stopped", false);
        return response;
    }

    @Transactional
    public Map<String, Object> disable(
            AtlasUserRecord user, String bindingId, Confirmation confirmation) {
        return confirmBindingMutation(user, bindingId, confirmation, Operation.DISABLE);
    }

    @Transactional
    public Map<String, Object> killSwitch(
            AtlasUserRecord user, String bindingId, Confirmation confirmation) {
        return confirmBindingMutation(user, bindingId, confirmation, Operation.KILL_SWITCH);
    }

    @Transactional
    public Map<String, Object> rollback(
            AtlasUserRecord user, String bindingId, Confirmation confirmation) {
        requireAdmin(user);
        PreviewContext preview =
                requirePreview(bindingId, confirmation, Operation.ROLLBACK, user.userId());
        BindingRecord current = preview.binding();
        LogicalKnowledgeBaseRecord kb = requireKnowledgeBase(current.logicalKbId());
        if ("retired".equals(kb.lifecycle())) {
            throw new GovernanceConflictException(
                    "RETIRED_KNOWLEDGE_BASE",
                    "A Retired knowledge base is terminal; register a new version instead of restoring it.");
        }
        int targetVersion = preview.details().path("rollback_target_config_version").asInt(0);
        if (targetVersion <= 0) {
            throw new GovernanceConflictException(
                    "ROLLBACK_UNAVAILABLE", "The impact preview has no rollback target.");
        }
        BindingConfigHistoryRecord target =
                bindings
                        .findHistory(bindingId, targetVersion)
                        .orElseThrow(
                                () ->
                                        new GovernanceConflictException(
                                                "ROLLBACK_UNAVAILABLE",
                                                "The rollback target is no longer available."));
        requireRevalidation(target, kb);
        BindingRecord restored = bindings.restore(bindingId, current.configVersion(), target);
        auditMutation(
                user,
                restored,
                "rollback",
                confirmation.impactPreviewId(),
                current,
                restored,
                Map.of("restored_from_config_version", target.configVersion()));
        return bindingProjection(
                restored, !restored.enabled() || restored.killSwitch(), kb.lifecycle());
    }

    @Transactional
    public Map<String, Object> retire(
            AtlasUserRecord user, String bindingId, Confirmation confirmation) {
        requireAdmin(user);
        PreviewContext preview =
                requirePreview(bindingId, confirmation, Operation.RETIRE, user.userId());
        BindingRecord current = preview.binding();
        LogicalKnowledgeBaseRecord kb = requireKnowledgeBase(current.logicalKbId());
        if (!"active".equals(kb.lifecycle()) && !"suspended".equals(kb.lifecycle())) {
            throw new GovernanceValidationException(
                    "NOT_RETIRABLE",
                    "Only Active or Suspended knowledge bases can enter the Retired lifecycle.");
        }
        BindingRecord disabled =
                bindings.updateRuntime(bindingId, current.configVersion(), false, true);
        LogicalKnowledgeBaseRecord retired = kb;
        if (wouldRetireKnowledgeBase(disabled)) {
            retired = knowledgeBases.retire(kb.logicalKbId());
        }
        auditMutation(
                user,
                disabled,
                "retire",
                confirmation.impactPreviewId(),
                current,
                disabled,
                Map.of("logical_kb_retired", "retired".equals(retired.lifecycle())));
        return bindingProjection(disabled, true, retired.lifecycle());
    }

    @Transactional
    public Map<String, Object> suspendOwnerless(
            AtlasUserRecord user, String logicalKbId, boolean confirm) {
        requireAdmin(user);
        if (!confirm) {
            throw new GovernanceValidationException(
                    "CONFIRM_REQUIRED", "Owner-less suspend requires confirm=true.");
        }
        LogicalKnowledgeBaseRecord kb = requireKnowledgeBase(logicalKbId);
        if (!isOwnerless(kb)) {
            throw new GovernanceValidationException(
                    "NOT_OWNERLESS",
                    "This knowledge base still has an accountable Owner; transfer ownership instead.");
        }
        if ("suspended".equals(kb.lifecycle())) {
            auditKb(user, kb, "suspend_ownerless", "idempotent");
            return kbProjection(kb);
        }
        if (!"active".equals(kb.lifecycle())) {
            throw new GovernanceValidationException(
                    "NOT_ACTIVE",
                    "Owner-less Suspend applies to Active knowledge bases. Drafts without an Owner remain Draft.");
        }
        LogicalKnowledgeBaseRecord suspended = knowledgeBases.suspend(logicalKbId);
        auditKb(user, suspended, "suspend_ownerless", "success");
        return kbProjection(suspended);
    }

    private Map<String, Object> confirmBindingMutation(
            AtlasUserRecord user,
            String bindingId,
            Confirmation confirmation,
            Operation operation) {
        requireAdmin(user);
        PreviewContext preview = requirePreview(bindingId, confirmation, operation, user.userId());
        BindingRecord current = preview.binding();
        BindingRecord changed =
                switch (operation) {
                    case DISABLE ->
                            bindings.updateRuntime(
                                    bindingId, current.configVersion(), false, current.killSwitch());
                    case KILL_SWITCH ->
                            bindings.updateRuntime(
                                    bindingId, current.configVersion(), current.enabled(), true);
                    default -> throw new IllegalStateException("unsupported operation " + operation);
                };
        auditMutation(
                user,
                changed,
                operation.auditAction,
                confirmation.impactPreviewId(),
                current,
                changed,
                Map.of());
        return bindingProjection(changed, true, requireKnowledgeBase(changed.logicalKbId()).lifecycle());
    }

    private PreviewContext requirePreview(
            String bindingId,
            Confirmation confirmation,
            Operation expectedOperation,
            String actorUserId) {
        if (confirmation == null || !confirmation.confirm()) {
            throw new GovernanceValidationException(
                    "CONFIRM_REQUIRED", "Governance changes require confirm=true.");
        }
        String previewId = confirmation.impactPreviewId();
        if (previewId == null || previewId.isBlank()) {
            throw new GovernanceValidationException(
                    "IMPACT_PREVIEW_REQUIRED",
                    "Run an impact preview and provide its impact_preview_id before confirming.");
        }
        AuditEventRecord preview =
                auditEvents
                        .findById(previewId)
                        .orElseThrow(
                                () ->
                                        new GovernanceConflictException(
                                                "IMPACT_PREVIEW_INVALID",
                                                "The impact preview is missing or invalid."));
        if (!"impact_preview".equals(preview.action())
                || !bindingId.equals(preview.bindingId())) {
            throw new GovernanceConflictException(
                    "IMPACT_PREVIEW_INVALID", "The impact preview does not match this binding.");
        }
        JsonNode details = readDetails(preview.detailsJson());
        if (!expectedOperation.wireName.equals(details.path("operation").asText())
                || !bindingId.equals(details.path("binding_id").asText())) {
            throw new GovernanceConflictException(
                    "IMPACT_PREVIEW_MISMATCH", "The impact preview does not match this operation.");
        }
        // The unique claim is deliberately made before the mutable-state reads below. A
        // sequential replay gets the stable replay error, while a concurrent confirmation is
        // serialized by the database primary key. Any later validation failure rolls the claim
        // back with this transaction so the administrator can retry with a fresh state.
        previewClaims.claim(
                previewId,
                expectedOperation.wireName,
                bindingId,
                actorUserId,
                clock.instant());
        BindingRecord binding = requireBinding(bindingId);
        int expectedVersion = details.path("config_version").asInt(-1);
        if (expectedVersion != binding.configVersion()
                || details.path("enabled").asBoolean() != binding.enabled()
                || details.path("kill_switch").asBoolean() != binding.killSwitch()) {
            throw new GovernanceConflictException(
                    "IMPACT_PREVIEW_STALE",
                    "The binding changed after the impact preview; run a new preview.",
                    Map.of(
                            "preview_config_version", expectedVersion,
                            "current_config_version", binding.configVersion()));
        }
        if (expectedOperation == Operation.RETIRE
                && !Objects.equals(
                        details.path("runtime_binding_ids").toString(),
                        objectMapper.valueToTree(runtimeBindingIds(binding.logicalKbId())).toString())) {
            throw new GovernanceConflictException(
                    "IMPACT_PREVIEW_STALE",
                    "Another binding changed after the impact preview; run a new preview.");
        }
        return new PreviewContext(binding, details);
    }

    private void requireRevalidation(BindingConfigHistoryRecord target, LogicalKnowledgeBaseRecord kb) {
        BindingRecord candidate =
                new BindingRecord(
                        target.bindingId(),
                        target.logicalKbId(),
                        target.providerProfile(),
                        target.sourceIdentityJson(),
                        target.bindingRole(),
                        target.authMethod(),
                        target.health(),
                        target.enabled(),
                        target.killSwitch(),
                        target.featureFlag(),
                        target.freshnessPolicyJson(),
                        target.locatorRulesJson(),
                        target.credentialOwner(),
                        target.regionConstraintsJson(),
                        target.configVersion(),
                        Instant.EPOCH,
                        Instant.EPOCH);
        try {
            Map<String, String> checks = probe.connectionChecks(candidate);
            boolean passed =
                    probe.connectionPassed(checks)
                            && probe.hasOriginalVersionMapping(candidate)
                            && (!"git_markdown".equals(candidate.providerProfile())
                                    || !kb.modelEligible()
                                    || probe.gitKbValidated(candidate));
            if ("dify".equals(candidate.providerProfile())) {
                passed =
                        passed
                                && !probe
                                        .auditCounts(candidate)
                                        .exclusionReasons()
                                        .containsKey("acl_mixed");
            }
            if (!passed) {
                throw new GovernanceConflictException(
                        "REVALIDATION_REQUIRED",
                        "The rollback target did not pass the applicable source validation.");
            }
        } catch (GovernanceConflictException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GovernanceConflictException(
                    "REVALIDATION_REQUIRED",
                    "The rollback target could not be revalidated safely.");
        }
    }

    private boolean wouldRetireKnowledgeBase(BindingRecord binding) {
        LogicalKnowledgeBaseRecord kb = requireKnowledgeBase(binding.logicalKbId());
        boolean requireActiveLifecycle = !"suspended".equals(kb.lifecycle());
        return bindings.findByLogicalKbId(binding.logicalKbId()).stream()
                .noneMatch(
                        other ->
                                !other.bindingId().equals(binding.bindingId())
                                        && RetrievalEligibility.isEligible(
                                                kb,
                                                other,
                                                retrievalProperties,
                                                requireActiveLifecycle));
    }

    private List<String> runtimeBindingIds(String logicalKbId) {
        LogicalKnowledgeBaseRecord kb = requireKnowledgeBase(logicalKbId);
        return bindings.findByLogicalKbId(logicalKbId).stream()
                .filter(
                        binding ->
                                RetrievalEligibility.isEligible(kb, binding, retrievalProperties))
                .map(BindingRecord::bindingId)
                .sorted()
                .toList();
    }

    private BindingRecord requireBinding(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            throw new GovernanceNotFoundException("BINDING_NOT_FOUND", "Binding was not found.");
        }
        return bindings
                .findById(bindingId)
                .orElseThrow(
                        () ->
                                new GovernanceNotFoundException(
                                        "BINDING_NOT_FOUND", "Binding was not found: " + bindingId));
    }

    private LogicalKnowledgeBaseRecord requireKnowledgeBase(String logicalKbId) {
        return knowledgeBases
                .findById(logicalKbId)
                .orElseThrow(
                        () ->
                                new GovernanceNotFoundException(
                                        "KNOWLEDGE_BASE_NOT_FOUND",
                                        "Knowledge base was not found: " + logicalKbId));
    }

    private void requireAdmin(AtlasUserRecord user) {
        if (user == null || !AtlasRoles.has(user, AtlasRoles.ATLAS_ADMIN)) {
            throw new RegistryForbiddenException(
                    "ADMIN_REQUIRED",
                    "Only an Atlas Admin can operate governance controls.",
                    "request_atlas_admin_role");
        }
    }

    private boolean isOwnerless(LogicalKnowledgeBaseRecord kb) {
        if (kb.ownerUserId() == null || kb.ownerUserId().isBlank()) {
            return true;
        }
        return users
                .findById(kb.ownerUserId())
                .map(owner -> !AtlasRoles.has(owner, AtlasRoles.KB_OWNER))
                .orElse(true);
    }

    private void auditMutation(
            AtlasUserRecord user,
            BindingRecord after,
            String action,
            String previewId,
            BindingRecord before,
            BindingRecord changed,
            Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("impact_preview_id", previewId);
        details.put("binding_id", after.bindingId());
        details.put("logical_kb_id", after.logicalKbId());
        details.put("before_config_version", before.configVersion());
        details.put("after_config_version", changed.configVersion());
        details.put("before_enabled", before.enabled());
        details.put("after_enabled", changed.enabled());
        details.put("before_kill_switch", before.killSwitch());
        details.put("after_kill_switch", changed.killSwitch());
        if (extra != null) {
            details.putAll(extra);
        }
        auditEvents.insert(
                new AuditEventRecord(
                        "aud_" + SessionService.randomToken().substring(0, 16),
                        clock.instant(),
                        user.userId(),
                        after.logicalKbId(),
                        after.bindingId(),
                        after.providerProfile(),
                        action,
                        "allowed",
                        null,
                        null,
                        null,
                        "success",
                        null,
                        writeDetails(details)));
    }

    private void auditKb(
            AtlasUserRecord user, LogicalKnowledgeBaseRecord kb, String action, String status) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("logical_kb_id", kb.logicalKbId());
        details.put("lifecycle", kb.lifecycle());
        auditEvents.insert(
                new AuditEventRecord(
                        "aud_" + SessionService.randomToken().substring(0, 16),
                        clock.instant(),
                        user.userId(),
                        kb.logicalKbId(),
                        null,
                        null,
                        action,
                        "allowed",
                        null,
                        null,
                        null,
                        status,
                        null,
                        writeDetails(details)));
    }

    private Map<String, Object> bindingProjection(
            BindingRecord binding, boolean retrievalStopped, String lifecycle) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("binding_id", binding.bindingId());
        body.put("logical_kb_id", binding.logicalKbId());
        body.put("enabled", binding.enabled());
        body.put("kill_switch", binding.killSwitch());
        body.put("config_version", binding.configVersion());
        body.put("new_retrieval_stopped", retrievalStopped);
        body.put("logical_kb_lifecycle", lifecycle);
        return body;
    }

    private Map<String, Object> kbProjection(LogicalKnowledgeBaseRecord kb) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logical_kb_id", kb.logicalKbId());
        body.put("lifecycle", kb.lifecycle());
        body.put("health", kb.health());
        body.put("config_version", kb.configVersion());
        return body;
    }

    private String writeDetails(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize content-free governance details", ex);
        }
    }

    private JsonNode readDetails(String details) {
        if (details == null || details.isBlank()) {
            throw new GovernanceConflictException(
                    "IMPACT_PREVIEW_INVALID", "The impact preview has no verifiable details.");
        }
        try {
            return objectMapper.readTree(details);
        } catch (JsonProcessingException ex) {
            throw new GovernanceConflictException(
                    "IMPACT_PREVIEW_INVALID", "The impact preview details are invalid.");
        }
    }

    public record Confirmation(boolean confirm, String impactPreviewId) {}

    private record PreviewContext(BindingRecord binding, JsonNode details) {}

    private enum Operation {
        DISABLE("disable", "disable"),
        KILL_SWITCH("kill_switch", "kill_switch"),
        ROLLBACK("rollback", "rollback"),
        RETIRE("retire", "retire");

        private final String wireName;
        private final String auditAction;

        Operation(String wireName, String auditAction) {
            this.wireName = wireName;
            this.auditAction = auditAction;
        }

        private static Operation parse(String value) {
            String normalized = value == null ? "disable" : value.trim().toLowerCase(Locale.ROOT);
            if ("kill-switch".equals(normalized)) {
                normalized = "kill_switch";
            }
            for (Operation operation : values()) {
                if (operation.wireName.equals(normalized)) {
                    return operation;
                }
            }
            throw new GovernanceValidationException(
                    "OPERATION_INVALID",
                    "operation must be disable, kill_switch, rollback, or retire.");
        }
    }
}
