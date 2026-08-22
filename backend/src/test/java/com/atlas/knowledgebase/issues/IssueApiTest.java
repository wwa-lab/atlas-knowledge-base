package com.atlas.knowledgebase.issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.chat.ChatMessageRecord;
import com.atlas.knowledgebase.chat.ChatMessageRepository;
import com.atlas.knowledgebase.chat.ChatThreadRecord;
import com.atlas.knowledgebase.chat.ChatThreadRepository;
import com.atlas.knowledgebase.evidence.CitationRecord;
import com.atlas.knowledgebase.evidence.CitationRepository;
import com.atlas.knowledgebase.registry.BindingRecord;
import com.atlas.knowledgebase.registry.BindingRepository;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import com.atlas.knowledgebase.session.SsoIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IssueApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private SessionService sessions;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LogicalKnowledgeBaseRepository knowledgeBases;
    @Autowired private BindingRepository bindings;
    @Autowired private ChatThreadRepository threads;
    @Autowired private ChatMessageRepository messages;
    @Autowired private CitationRepository citations;
    @Autowired private IssueReportRepository reports;

    @Test
    void routesGitCitationAndKeepsDiagnosticsContentFree() throws Exception {
        LoggedIn owner = login("kb_owner");
        Fixture fixture = fixture(owner, "git_markdown");

        MvcResult issueResponse =
                mockMvc.perform(
                        post("/api/v1/issues")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "message_id": "%s",
                                          "citation_id": "%s",
                                          "category": "citation",
                                          "note": "Line range looks wrong"
                                        }
                                        """.formatted(fixture.messageId(), fixture.citationId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issue_id").isString())
                .andExpect(jsonPath("$.route_target").value("kb_correct_flow"))
                .andExpect(jsonPath("$.diagnostics.request_id").value(fixture.requestId()))
                .andExpect(jsonPath("$.diagnostics.logical_kb_id").value(fixture.logicalKbId()))
                .andExpect(jsonPath("$.diagnostics.binding_id").value(fixture.bindingId()))
                .andExpect(jsonPath("$.diagnostics.authorization_result").value("allow"))
                .andReturn();
        assertThat(issueResponse.getResponse().getContentAsString()).doesNotContain("Line range looks wrong");

        IssueReportRecord report =
                reports.findById(latestIssueId(owner.userId())).orElseThrow();
        assertThat(report.category()).isEqualTo("citation");
        assertThat(report.routeTarget()).isEqualTo("kb_correct_flow");
        assertThat(report.diagnosticsJson())
                .doesNotContain("Line range looks wrong")
                .doesNotContain("secret prompt body")
                .doesNotContain("secret answer body")
                .doesNotContain("secret source excerpt");
        String auditDetails =
                jdbc().queryForObject(
                        "SELECT details FROM audit_event WHERE user_id = ? AND action = 'issue_report' ORDER BY occurred_at DESC",
                        String.class,
                        owner.userId());
        assertThat(auditDetails)
                .contains("kb_correct_flow")
                .doesNotContain("secret prompt body")
                .doesNotContain("secret answer body");
    }

    @Test
    void routesConnectorModelAndSecurityCategories() throws Exception {
        LoggedIn owner = login("kb_owner");
        Fixture fixture = fixture(owner, "dify");
        postIssue(owner, fixture.messageId(), null, "permission_connection")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.route_target").value("connector_owner"));
        postIssue(owner, fixture.messageId(), null, "retrieval")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.route_target").value("atlas_team"));
        postIssue(owner, fixture.messageId(), null, "model")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.route_target").value("atlas_team"));
        postIssue(owner, fixture.messageId(), null, "system_security")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.route_target").value("security_process"));
        postIssue(owner, fixture.messageId(), fixture.citationId(), "content")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.route_target").value("kb_owner_remediation"));
    }

    @Test
    void crossUserCitationIsNotDisclosedAndCsrfIsRequired() throws Exception {
        LoggedIn owner = login("kb_owner");
        Fixture fixture = fixture(owner, "confluence");
        SessionService.IssuedSession other =
                sessions.establish(
                        new SsoIdentity(
                                "other-issue-user-" + UUID.randomUUID(),
                                "Other Issue User",
                                "other-issue-user@localhost"));
        Cookie otherCookie =
                new Cookie(sessionProperties.getCookieName(), other.cookie().getValue());

        mockMvc.perform(
                        post("/api/v1/issues")
                                .cookie(otherCookie)
                                .header(SessionService.CSRF_HEADER, other.session().csrfSecret())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"citation_id\":\""
                                                + fixture.citationId()
                                                + "\",\"category\":\"citation\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ISSUE_CITATION_NOT_FOUND"));

        mockMvc.perform(
                        post("/api/v1/issues")
                                .cookie(owner.session())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"citation_id\":\""
                                                + fixture.citationId()
                                                + "\",\"category\":\"citation\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCategoryAndMissingContextFailClosed() throws Exception {
        LoggedIn owner = login("kb_owner");
        mockMvc.perform(
                        post("/api/v1/issues")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"category\":\"unknown\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ISSUE_CONTEXT_REQUIRED"));

        Fixture fixture = fixture(owner, "git_markdown");
        postIssue(owner, fixture.messageId(), fixture.citationId(), "unsupported")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions postIssue(
            LoggedIn user, String messageId, String citationId, String category) throws Exception {
        String citation = citationId == null ? "" : ",\"citation_id\":\"" + citationId + "\"";
        return mockMvc.perform(
                post("/api/v1/issues")
                        .cookie(user.session())
                        .header(SessionService.CSRF_HEADER, user.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"message_id\":\""
                                        + messageId
                                        + "\""
                                        + citation
                                        + ",\"category\":\""
                                        + category
                                        + "\"}"));
    }

    private Fixture fixture(LoggedIn owner, String provider) {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String logicalKbId = "lkb_issue_" + suffix;
        String bindingId = "bnd_issue_" + suffix;
        String threadId = "thr_issue_" + suffix;
        String messageId = "msg_issue_" + suffix;
        String citationId = "cit_issue_" + suffix;
        knowledgeBases.insert(
                new LogicalKnowledgeBaseRecord(
                        logicalKbId,
                        "Issue KB",
                        "Issue fixture",
                        owner.userId(),
                        "private",
                        "support",
                        "internal",
                        true,
                        "chat_ready",
                        "active",
                        "healthy",
                        1,
                        null,
                        false,
                        null,
                        now,
                        now,
                        now));
        bindings.insert(
                new BindingRecord(
                        bindingId,
                        logicalKbId,
                        provider,
                        "{\"source\":\"fixture\"}",
                        "canonical",
                        "delegated_user",
                        "healthy",
                        true,
                        false,
                        true,
                        null,
                        "{}",
                        owner.userId(),
                        null,
                        1,
                        now,
                        now));
        threads.insert(
                new ChatThreadRecord(
                        threadId,
                        owner.userId(),
                        "Issue fixture",
                        "[\"" + logicalKbId + "\"]",
                        null,
                        now,
                        now,
                        null));
        String requestId = "req_issue_" + suffix;
        messages.insert(
                new ChatMessageRecord(
                        messageId,
                        threadId,
                        "assistant",
                        "completed",
                        "secret prompt body",
                        "secret answer body",
                        "[\"" + logicalKbId + "\"]",
                        "[{\"binding_id\":\"" + bindingId + "\",\"binding_role\":\"canonical\"}]",
                        "{\"logical_kbs\":{\"" + logicalKbId + "\":1},\"bindings\":{\"" + bindingId + "\":1}}",
                        "{}",
                        null,
                        "internal",
                        requestId,
                        now,
                        now));
        citations.replaceForMessage(
                messageId,
                List.of(
                        new CitationRecord(
                                citationId,
                                messageId,
                                logicalKbId,
                                bindingId,
                                provider,
                                "{\"path\":\"guide.md\"}",
                                "v1",
                                "secret source excerpt",
                                "Guide",
                                "Owner",
                                "internal",
                                now,
                                now,
                                "ok")));
        return new Fixture(logicalKbId, bindingId, messageId, citationId, requestId);
    }

    private String latestIssueId(String userId) {
        return jdbc()
                .query(
                        "SELECT issue_id FROM issue_report WHERE user_id = ? AND category = 'citation' ORDER BY created_at DESC",
                        rs -> rs.next() ? rs.getString("issue_id") : null,
                        userId);
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private LoggedIn login(String extraRole) throws Exception {
        MvcResult start =
                mockMvc.perform(get("/api/v1/auth/sso/start")).andExpect(status().isFound()).andReturn();
        Cookie state = start.getResponse().getCookie(sessionProperties.ssoStateCookieName());
        MvcResult callback =
                mockMvc.perform(get(start.getResponse().getRedirectedUrl()).cookie(state))
                        .andExpect(status().isFound())
                        .andReturn();
        Cookie session = callback.getResponse().getCookie(sessionProperties.getCookieName());
        String me =
                mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String userId = field(me, "user_id");
        jdbcTemplate.update(
                "UPDATE atlas_user SET roles = ? WHERE user_id = ?",
                "kb_owner".equals(extraRole)
                        ? "[\"end_user\",\"kb_owner\"]"
                        : "[\"end_user\"]",
                userId);
        String csrfJson =
                mockMvc.perform(get("/api/v1/auth/csrf").cookie(session))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return new LoggedIn(session, field(csrfJson, "csrf_token"), userId);
    }

    private String field(String json, String field) throws Exception {
        JsonNode value = objectMapper.readTree(json).path(field);
        return value.asText();
    }

    private record LoggedIn(Cookie session, String csrf, String userId) {}

    private record Fixture(
            String logicalKbId,
            String bindingId,
            String messageId,
            String citationId,
            String requestId) {}
}
