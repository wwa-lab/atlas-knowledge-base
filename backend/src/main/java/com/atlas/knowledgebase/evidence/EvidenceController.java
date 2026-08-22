package com.atlas.knowledgebase.evidence;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Private evidence drawer and exact-original endpoints. */
@RestController
@RequestMapping("/api/v1/citations")
public final class EvidenceController {

    private final EvidenceService evidence;

    public EvidenceController(EvidenceService evidence) {
        this.evidence = evidence;
    }

    @GetMapping("/{citationId}")
    public Map<String, Object> drawer(
            HttpServletRequest request, @PathVariable String citationId) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return evidence.drawer(user, citationId);
    }

    @PostMapping("/{citationId}/open-original")
    public Map<String, Object> openOriginal(
            HttpServletRequest request,
            @PathVariable String citationId,
            @RequestBody(required = false) JsonNode body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return evidence.openOriginal(user, citationId, body);
    }
}
