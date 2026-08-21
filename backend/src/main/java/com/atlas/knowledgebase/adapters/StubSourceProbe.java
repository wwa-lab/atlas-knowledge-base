package com.atlas.knowledgebase.adapters;

import com.atlas.knowledgebase.registry.BindingRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Spike-gated stub for Connection Test and Content Audit. Does not call Dify, Git, or Confluence.
 * Real adapters replace this in TASK-019–021.
 */
@Component
public class StubSourceProbe implements SourceProbe {

    public static final String CHECK_AUTHENTICATION = "authentication";
    public static final String CHECK_RETRIEVAL = "retrieval";
    public static final String CHECK_EXACT_FETCH = "exact_fetch";
    public static final String CHECK_STABLE_VERSION = "stable_version";

    private final ObjectMapper objectMapper;

    public StubSourceProbe(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode identity(BindingRecord binding) {
        if (binding.sourceIdentityJson() == null || binding.sourceIdentityJson().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(binding.sourceIdentityJson());
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    @Override
    public Map<String, String> connectionChecks(BindingRecord binding) {
        JsonNode identity = identity(binding);
        Map<String, String> checks = new LinkedHashMap<>();
        boolean forcedFail = identity.path("fail_connection_test").asBoolean(false);
        checks.put(CHECK_AUTHENTICATION, forcedFail ? "fail" : "pass");
        checks.put(CHECK_RETRIEVAL, forcedFail ? "fail" : "pass");
        checks.put(CHECK_EXACT_FETCH, forcedFail ? "fail" : "pass");
        checks.put(CHECK_STABLE_VERSION, hasOriginalVersionMapping(binding, identity) ? "pass" : "fail");
        return checks;
    }

    @Override
    public boolean connectionPassed(Map<String, String> checks) {
        return checks.values().stream().allMatch("pass"::equals);
    }

    @Override
    public boolean hasOriginalVersionMapping(BindingRecord binding) {
        return hasOriginalVersionMapping(binding, identity(binding));
    }

    @Override
    public boolean gitKbValidated(BindingRecord binding) {
        if (!"git_markdown".equals(binding.providerProfile())) {
            return true;
        }
        JsonNode identity = identity(binding);
        if (identity.path("kb_validated").asBoolean(false)) {
            return true;
        }
        if (identity.hasNonNull("kb_contract") && !identity.get("kb_contract").isNull()) {
            return true;
        }
        String path = identity.path("kb_path").asText("");
        return !path.isBlank();
    }

    /**
     * Stub Content Audit counters. Remediation lists use opaque document ids only — never titles
     * used as citations.
     */
    @Override
    public AuditCounts auditCounts(BindingRecord binding) {
        JsonNode identity = identity(binding);
        int total = identity.path("audit_total").asInt(10);
        if (identity.path("acl_mixed").asBoolean(false)) {
            int excluded = Math.max(1, total / 5);
            return new AuditCounts(
                    total, total - excluded, excluded, Map.of("acl_mixed", excluded));
        }
        if (!hasOriginalVersionMapping(binding, identity)) {
            int excluded = total;
            return new AuditCounts(
                    total, 0, excluded, Map.of("missing_version_mapping", excluded));
        }
        return new AuditCounts(total, total, 0, Map.of());
    }

    private boolean hasOriginalVersionMapping(BindingRecord binding, JsonNode identity) {
        if (identity.hasNonNull("original_version_mapping") || identity.hasNonNull("version_mapping")) {
            return true;
        }
        return switch (binding.providerProfile()) {
            case "dify" -> identity.hasNonNull("document_version") || identity.hasNonNull("chunk_version");
            case "git_markdown" ->
                    identity.hasNonNull("commit")
                            || identity.hasNonNull("commit_sha")
                            || gitKbValidated(binding);
            case "confluence" -> identity.hasNonNull("page_version") || identity.hasNonNull("version");
            default -> false;
        };
    }
}
