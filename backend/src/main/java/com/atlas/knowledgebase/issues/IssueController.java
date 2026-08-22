package com.atlas.knowledgebase.issues;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates a content-free issue report from an authenticated answer/citation context. */
@RestController
@RequestMapping("/api/v1/issues")
public final class IssueController {

    private final IssueService issues;

    public IssueController(IssueService issues) {
        this.issues = issues;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            HttpServletRequest request, @RequestBody(required = false) CreateIssueRequest body) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        IssueService.CreateIssueCommand command =
                body == null
                        ? null
                        : new IssueService.CreateIssueCommand(
                                body.messageId(), body.citationId(), body.category(), body.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(issues.create(user, command));
    }

    public record CreateIssueRequest(
            @JsonProperty("message_id") String messageId,
            @JsonProperty("citation_id") String citationId,
            String category,
            String note) {}
}
