package com.atlas.knowledgebase.registry;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class RegistryController {

    private final RegistryService registry;

    public RegistryController(RegistryService registry) {
        this.registry = registry;
    }

    @PostMapping("/drafts")
    public ResponseEntity<Map<String, Object>> createDraft(
            HttpServletRequest request, @RequestBody CreateDraftRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        LogicalKnowledgeBaseRecord created =
                registry.createDraft(
                        user,
                        new RegistryService.CreateDraftCommand(
                                body.name(),
                                body.description(),
                                body.discoverability(),
                                body.purpose(),
                                body.classification(),
                                Boolean.TRUE.equals(body.modelEligible())));
        return ResponseEntity.status(HttpStatus.CREATED).body(projection(created));
    }

    @PatchMapping("/drafts/{logicalKbId}")
    public Map<String, Object> updateDraft(
            HttpServletRequest request,
            @PathVariable String logicalKbId,
            @RequestBody UpdateDraftRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        if (body.configVersion() == null) {
            throw new DraftValidationException(
                    "CONFIG_VERSION_REQUIRED", "config_version is required to update a draft.");
        }
        LogicalKnowledgeBaseRecord updated =
                registry.updateDraft(
                        user,
                        new RegistryService.UpdateDraftCommand(
                                logicalKbId,
                                body.configVersion(),
                                body.name(),
                                body.description(),
                                body.discoverability(),
                                body.purpose(),
                                body.classification(),
                                body.modelEligible(),
                                body.bindings()));
        return projection(updated);
    }

    private static Map<String, Object> projection(LogicalKnowledgeBaseRecord kb) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logical_kb_id", kb.logicalKbId());
        body.put("lifecycle", kb.lifecycle());
        body.put("config_version", kb.configVersion());
        body.put("capability", kb.capability());
        body.put("name", kb.name());
        return body;
    }

    public record CreateDraftRequest(
            String name,
            String description,
            String discoverability,
            String purpose,
            String classification,
            @JsonProperty("model_eligible") Boolean modelEligible) {}

    public record UpdateDraftRequest(
            @JsonProperty("config_version") Integer configVersion,
            String name,
            String description,
            String discoverability,
            String purpose,
            String classification,
            @JsonProperty("model_eligible") Boolean modelEligible,
            List<RegistryService.BindingInput> bindings) {}
}
