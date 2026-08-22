package com.atlas.knowledgebase.registry;

import com.atlas.knowledgebase.adapters.SourceProbe;
import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.session.AtlasRoles;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connection Test, Content Audit, Admin activation hard gates, and Owner-less Suspend. Real
 * provider calls stay spike-gated (TASK-019–021).
 */
@Service
public class ActivationService {

    private static final TypeReference<Map<String, Integer>> REASON_TYPE = new TypeReference<>() {};

    private final LogicalKnowledgeBaseRepository knowledgeBases;
    private final BindingRepository bindings;
    private final ContentAuditResultRepository audits;
    private final AtlasUserRepository users;
    private final AuditEventRepository auditEvents;
    private final SourceProbe probe;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ActivationService(
            LogicalKnowledgeBaseRepository knowledgeBases,
            BindingRepository bindings,
            ContentAuditResultRepository audits,
            AtlasUserRepository users,
            AuditEventRepository auditEvents,
            SourceProbe probe,
            ObjectMapper objectMapper,
            Clock clock) {
        this.knowledgeBases = knowledgeBases;
        this.bindings = bindings;
        this.audits = audits;
        this.users = users;
        this.auditEvents = auditEvents;
        this.probe = probe;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public LogicalKnowledgeBaseRecord requireDraft(String logicalKbId) {
        return knowledgeBases
                .findById(logicalKbId)
                .orElseThrow(() -> new DraftNotFoundException(logicalKbId));
    }

    public void requireDraftOwner(AtlasUserRecord user, LogicalKnowledgeBaseRecord kb) {
        if (!AtlasRoles.has(user, AtlasRoles.KB_OWNER)) {
            throw new RegistryForbiddenException(
                    "KB_OWNER_REQUIRED",
                    "Only a verified knowledge-base Owner can run wizard validation.",
                    "request_kb_owner_role");
        }
        if (!user.userId().equals(kb.ownerUserId())) {
            throw new RegistryForbiddenException(
                    "NOT_DRAFT_OWNER",
                    "Only the knowledge-base Owner can run wizard validation on this draft.",
                    "open_own_draft");
        }
    }

    public void requireAdmin(AtlasUserRecord user) {
        if (!AtlasRoles.has(user, AtlasRoles.ATLAS_ADMIN)) {
            throw new RegistryForbiddenException(
                    "ADMIN_REQUIRED",
                    "Only an Atlas Admin can activate or suspend knowledge bases.",
                    "request_atlas_admin_role");
        }
    }

    @Transactional
    public Map<String, Object> connectionTest(AtlasUserRecord user, String logicalKbId) {
        LogicalKnowledgeBaseRecord kb = requireDraft(logicalKbId);
        requireDraftOwner(user, kb);
        if (!"draft".equals(kb.lifecycle())) {
            throw new DraftValidationException("NOT_A_DRAFT", "Connection Test applies to Drafts.");
        }
        List<BindingRecord> found = bindings.findByLogicalKbId(logicalKbId);
        if (found.isEmpty()) {
            throw new DraftValidationException("NO_BINDINGS", "Add at least one source before Connection Test.");
        }
        List<Map<String, Object>> bindingResults = new ArrayList<>();
        boolean passed = true;
        for (BindingRecord binding : found) {
            Map<String, String> checks = probe.connectionChecks(binding);
            boolean bindingPassed = probe.connectionPassed(checks);
            passed = passed && bindingPassed;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("binding_id", binding.bindingId());
            row.put("provider_profile", binding.providerProfile());
            row.put("passed", bindingPassed);
            row.put("checks", checks);
            bindingResults.add(row);
        }
        audit(user.userId(), logicalKbId, null, "connection_test", "allowed", "success");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logical_kb_id", logicalKbId);
        body.put("passed", passed);
        body.put("bindings", bindingResults);
        return body;
    }

    @Transactional
    public Map<String, Object> contentAudit(AtlasUserRecord user, String logicalKbId) {
        LogicalKnowledgeBaseRecord kb = requireDraft(logicalKbId);
        requireDraftOwner(user, kb);
        if (!"draft".equals(kb.lifecycle())) {
            throw new DraftValidationException("NOT_A_DRAFT", "Content Audit applies to Drafts.");
        }
        List<BindingRecord> found = bindings.findByLogicalKbId(logicalKbId);
        if (found.isEmpty()) {
            throw new DraftValidationException("NO_BINDINGS", "Add at least one source before Content Audit.");
        }
        Instant now = clock.instant();
        int total = 0;
        int eligible = 0;
        int excluded = 0;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        String latestAuditId = null;
        for (BindingRecord binding : found) {
            SourceProbe.AuditCounts counts = probe.auditCounts(binding);
            String auditId = "aud_" + SessionService.randomToken().substring(0, 16);
            latestAuditId = auditId;
            audits.insert(
                    new ContentAuditResultRecord(
                            auditId,
                            logicalKbId,
                            binding.bindingId(),
                            counts.total(),
                            counts.chatEligible(),
                            counts.excluded(),
                            writeJson(counts.exclusionReasons()),
                            "audit:" + auditId,
                            now));
            total += counts.total();
            eligible += counts.chatEligible();
            excluded += counts.excluded();
            counts.exclusionReasons()
                    .forEach((reason, count) -> reasons.merge(reason, count, Integer::sum));
        }
        audit(user.userId(), logicalKbId, null, "content_audit", "allowed", "success");
        return auditProjection(logicalKbId, latestAuditId, total, eligible, excluded, reasons, now);
    }

    public String remediationCsv(AtlasUserRecord user, String logicalKbId) {
        LogicalKnowledgeBaseRecord kb =
                knowledgeBases
                        .findById(logicalKbId)
                        .orElseThrow(() -> new DraftNotFoundException(logicalKbId));
        if (!user.userId().equals(kb.ownerUserId()) && !AtlasRoles.has(user, AtlasRoles.ATLAS_ADMIN)) {
            throw new RegistryForbiddenException(
                    "FORBIDDEN",
                    "Only the Owner or an Atlas Admin can download the remediation list.",
                    "open_own_draft");
        }
        List<ContentAuditResultRecord> rows = audits.findLatestByLogicalKbId(logicalKbId);
        if (rows.isEmpty()) {
            throw new DraftValidationException(
                    "CONTENT_AUDIT_REQUIRED", "Run Content Audit before downloading remediation.");
        }
        StringBuilder csv = new StringBuilder("document_id,reason\n");
        for (ContentAuditResultRecord row : rows) {
            Map<String, Integer> reasons = readReasons(row.exclusionReasonsJson());
            for (Map.Entry<String, Integer> reason : reasons.entrySet()) {
                int n = reason.getValue() == null ? 0 : reason.getValue();
                for (int i = 1; i <= n; i++) {
                    csv.append(row.bindingId())
                            .append(':')
                            .append(reason.getKey())
                            .append(':')
                            .append(i)
                            .append(',')
                            .append(reason.getKey())
                            .append('\n');
                }
            }
        }
        return csv.toString();
    }

    @Transactional
    public LogicalKnowledgeBaseRecord activate(AtlasUserRecord user, String logicalKbId, boolean confirm) {
        requireAdmin(user);
        if (!confirm) {
            throw new DraftValidationException("CONFIRM_REQUIRED", "Activation requires confirm=true.");
        }
        LogicalKnowledgeBaseRecord kb = requireDraft(logicalKbId);
        if (!"draft".equals(kb.lifecycle())) {
            throw new DraftValidationException("NOT_A_DRAFT", "Only Draft knowledge bases can be activated.");
        }
        if (isOwnerless(kb)) {
            throw new HardGateException(
                    "OWNER_REQUIRED",
                    "A knowledge base without an accountable Owner cannot be activated.",
                    Map.of("logical_kb_id", logicalKbId));
        }
        List<BindingRecord> found = bindings.findByLogicalKbId(logicalKbId);
        if (found.isEmpty()) {
            throw new HardGateException(
                    "HARD_GATE_FAILURE",
                    "Activation requires at least one binding that passes hard gates.",
                    Map.of("logical_kb_id", logicalKbId, "reason", "no_bindings"));
        }

        List<Map<String, Object>> failures = new ArrayList<>();
        boolean anyGitWithoutKb = false;
        for (BindingRecord binding : found) {
            Map<String, String> checks = probe.connectionChecks(binding);
            if ("git_markdown".equals(binding.providerProfile()) && !probe.gitKbValidated(binding)) {
                anyGitWithoutKb = true;
            }
            if (!probe.connectionPassed(checks) || !probe.hasOriginalVersionMapping(binding)) {
                failures.add(gateFailure(binding, checks, "original_version_or_connection"));
            }
            if ("dify".equals(binding.providerProfile())) {
                ContentAuditResultRecord audit =
                        audits.findLatestForBinding(logicalKbId, binding.bindingId()).orElse(null);
                SourceProbe.AuditCounts live = probe.auditCounts(binding);
                boolean stale =
                        audit == null
                                || (binding.updatedAt() != null
                                        && audit.auditedAt() != null
                                        && audit.auditedAt().isBefore(binding.updatedAt()));
                if (audit == null || stale) {
                    failures.add(gateFailure(binding, checks, "content_audit_required"));
                }
                boolean mixedAcl =
                        live.exclusionReasons().containsKey("acl_mixed")
                                || (audit != null
                                        && readReasons(audit.exclusionReasonsJson()).containsKey("acl_mixed"));
                if (mixedAcl) {
                    failures.add(gateFailure(binding, checks, "acl_mixed"));
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new HardGateException(
                    "HARD_GATE_FAILURE",
                    "Hard gates failed; the knowledge base remains Draft. Admin override is forbidden.",
                    Map.of("logical_kb_id", logicalKbId, "failed_bindings", failures));
        }

        String capability = kb.capability();
        boolean modelEligible = kb.modelEligible();
        if (anyGitWithoutKb) {
            capability = "browse_only";
            modelEligible = false;
        }
        LogicalKnowledgeBaseRecord activated =
                knowledgeBases.activate(logicalKbId, kb.configVersion(), capability, modelEligible);
        audit(user.userId(), logicalKbId, null, "activate", "allowed", "success");
        return activated;
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

    private static Map<String, Object> gateFailure(
            BindingRecord binding, Map<String, String> checks, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("binding_id", binding.bindingId());
        row.put("provider_profile", binding.providerProfile());
        row.put("reason", reason);
        row.put("checks", checks);
        return row;
    }

    private Map<String, Object> auditProjection(
            String logicalKbId,
            String auditId,
            int total,
            int eligible,
            int excluded,
            Map<String, Integer> reasons,
            Instant auditedAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("audit_id", auditId);
        body.put("total", total);
        body.put("chat_eligible", eligible);
        body.put("excluded", excluded);
        body.put("exclusion_reasons", reasons);
        body.put("last_audited_at", auditedAt);
        body.put(
                "remediation_download_path",
                "/api/v1/knowledge-bases/" + logicalKbId + "/content-audit/remediation");
        return body;
    }

    private void audit(
            String userId, String logicalKbId, String bindingId, String action, String authorization, String status) {
        try {
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
                            objectMapper.writeValueAsString(Map.of("logical_kb_id", logicalKbId))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize content-free audit details", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize Content Audit reasons", e);
        }
    }

    private Map<String, Integer> readReasons(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Integer> parsed = objectMapper.readValue(json, REASON_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
