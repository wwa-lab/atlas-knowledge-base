package com.atlas.knowledgebase.governance;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class GovernanceController {

    private final GovernanceService governance;

    public GovernanceController(GovernanceService governance) {
        this.governance = governance;
    }

    @PostMapping("/bindings/{bindingId}/impact-preview")
    public Map<String, Object> impactPreview(
            HttpServletRequest request,
            @PathVariable String bindingId,
            @RequestBody(required = false) ImpactPreviewRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        String operation =
                body == null
                        ? null
                        : body.operation() != null ? body.operation() : body.action();
        return governance.impactPreview(user, bindingId, operation);
    }

    @PostMapping("/bindings/{bindingId}/disable")
    public Map<String, Object> disable(
            HttpServletRequest request,
            @PathVariable String bindingId,
            @RequestBody(required = false) ConfirmationRequest body) {
        return mutate(request, bindingId, body, governance::disable);
    }

    @PostMapping("/bindings/{bindingId}/kill-switch")
    public Map<String, Object> killSwitch(
            HttpServletRequest request,
            @PathVariable String bindingId,
            @RequestBody(required = false) ConfirmationRequest body) {
        return mutate(request, bindingId, body, governance::killSwitch);
    }

    @PostMapping("/bindings/{bindingId}/rollback")
    public Map<String, Object> rollback(
            HttpServletRequest request,
            @PathVariable String bindingId,
            @RequestBody(required = false) ConfirmationRequest body) {
        return mutate(request, bindingId, body, governance::rollback);
    }

    @PostMapping("/bindings/{bindingId}/retire")
    public Map<String, Object> retire(
            HttpServletRequest request,
            @PathVariable String bindingId,
            @RequestBody(required = false) ConfirmationRequest body) {
        return mutate(request, bindingId, body, governance::retire);
    }

    @PostMapping("/knowledge-bases/{logicalKbId}/suspend-ownerless")
    public Map<String, Object> suspendOwnerless(
            HttpServletRequest request,
            @PathVariable String logicalKbId,
            @RequestBody(required = false) ConfirmRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return governance.suspendOwnerless(
                user, logicalKbId, body != null && Boolean.TRUE.equals(body.confirm()));
    }

    private Map<String, Object> mutate(
            HttpServletRequest request,
            String bindingId,
            ConfirmationRequest body,
            Mutation mutation) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        GovernanceService.Confirmation confirmation =
                body == null
                        ? null
                        : new GovernanceService.Confirmation(
                                Boolean.TRUE.equals(body.confirm()), body.impactPreviewId());
        return mutation.apply(user, bindingId, confirmation);
    }

    @FunctionalInterface
    private interface Mutation {
        Map<String, Object> apply(
                AtlasUserRecord user, String bindingId, GovernanceService.Confirmation confirmation);
    }

    public record ImpactPreviewRequest(
            String operation, String action) {}

    public record ConfirmationRequest(
            @JsonProperty("confirm") Boolean confirm,
            @JsonProperty("impact_preview_id") String impactPreviewId) {}

    public record ConfirmRequest(@JsonProperty("confirm") Boolean confirm) {}
}
