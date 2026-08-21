package com.atlas.knowledgebase.registry;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ActivationController {

    private final ActivationService activation;

    public ActivationController(ActivationService activation) {
        this.activation = activation;
    }

    @PostMapping("/knowledge-bases/drafts/{logicalKbId}/connection-test")
    public Map<String, Object> connectionTest(
            HttpServletRequest request, @PathVariable String logicalKbId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return activation.connectionTest(user, logicalKbId);
    }

    @PostMapping("/knowledge-bases/drafts/{logicalKbId}/content-audit")
    public Map<String, Object> contentAudit(
            HttpServletRequest request, @PathVariable String logicalKbId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return activation.contentAudit(user, logicalKbId);
    }

    @GetMapping(
            path = "/knowledge-bases/{logicalKbId}/content-audit/remediation",
            produces = "text/csv")
    public ResponseEntity<String> remediation(
            HttpServletRequest request, @PathVariable String logicalKbId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        String csv = activation.remediationCsv(user, logicalKbId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"remediation.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping("/knowledge-bases/{logicalKbId}/activate")
    public Map<String, Object> activate(
            HttpServletRequest request,
            @PathVariable String logicalKbId,
            @RequestBody(required = false) ConfirmRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        LogicalKnowledgeBaseRecord activated =
                activation.activate(user, logicalKbId, body != null && Boolean.TRUE.equals(body.confirm()));
        return activationProjection(activated);
    }

    @PostMapping("/admin/knowledge-bases/{logicalKbId}/suspend-ownerless")
    public Map<String, Object> suspendOwnerless(
            HttpServletRequest request,
            @PathVariable String logicalKbId,
            @RequestBody(required = false) ConfirmRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        LogicalKnowledgeBaseRecord suspended =
                activation.suspendOwnerless(
                        user, logicalKbId, body != null && Boolean.TRUE.equals(body.confirm()));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("logical_kb_id", suspended.logicalKbId());
        response.put("lifecycle", suspended.lifecycle());
        return response;
    }

    private static Map<String, Object> activationProjection(LogicalKnowledgeBaseRecord kb) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logical_kb_id", kb.logicalKbId());
        body.put("lifecycle", kb.lifecycle());
        body.put("health", kb.health());
        body.put("capability", kb.capability());
        body.put("config_version", kb.configVersion());
        body.put(
                "activated_at",
                kb.activatedAt() == null ? null : kb.activatedAt().toString());
        return body;
    }

    public record ConfirmRequest(@JsonProperty("confirm") Boolean confirm) {}
}
