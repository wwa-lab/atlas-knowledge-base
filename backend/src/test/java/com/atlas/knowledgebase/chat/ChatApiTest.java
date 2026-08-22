package com.atlas.knowledgebase.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ChatApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetModelEntitlement() {
        jdbcTemplate.update("UPDATE atlas_user SET model_entitled = 0");
    }

    @Test
    void unauthenticatedChatsAre401() throws Exception {
        mockMvc.perform(get("/api/v1/chats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REQUIRED"));
    }

    @Test
    void createAskCancelAndRetryStayIncompleteWhenCancelled() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Chat Ready Dify");
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/chats")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.thread_id").exists())
                        .andReturn();
        String threadId = jsonString(created.getResponse().getContentAsString(), "thread_id");

        mockMvc.perform(get("/api/v1/chats").cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_valid_logical_kb_ids[0]").value(kbId));

        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"How do we rotate the gateway cert?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String assistantId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT message_id FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant'
                        ORDER BY created_at DESC
                        """,
                        String.class,
                        threadId);
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/cancel")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("incomplete_cancelled"));
        mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk());
        String status =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM chat_message WHERE message_id = ?", String.class, assistantId);
        String answer =
                jdbcTemplate.queryForObject(
                        "SELECT answer_text FROM chat_message WHERE message_id = ?", String.class, assistantId);
        assertThat(status).isEqualTo("incomplete_cancelled");
        assertThat(answer).isNull();

        MvcResult retry =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/retry")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .accept(MediaType.TEXT_EVENT_STREAM))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String retryBody =
                mockMvc.perform(asyncDispatch(retry)).andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString();
        assertThat(retryBody).contains("insufficient");
        assertThat(retryBody.toLowerCase()).doesNotContain("secret");
        Integer completed =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant' AND status = 'completed'
                        """,
                        Integer.class,
                        threadId);
        assertThat(completed).isEqualTo(1);
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/scope")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MODE_REQUIRED"));
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/scope")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"],\"mode\":\"branch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branched_from_thread_id").value(threadId));
    }

    @Test
    void browseOnlyGitCannotEnterChat() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateGitBrowseOnly(owner, "Browse Git");
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NOT_CHAT_READY"));
    }

    @Test
    void moreThanFiveKnowledgeBasesIs422() throws Exception {
        LoggedIn owner = loginOwner();
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"logical_kb_ids\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SCOPE_LIMIT"));
    }

    @Test
    void restoreLastValidScopeWhenCreateOmitsIds() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Restore Chat KB");
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(
                        post("/api/v1/chats")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.logical_kb_ids[0]").value(kbId));
    }

    @Test
    void completedRetryIsIdempotent() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId = activateDify(owner, "Idempotent Chat");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"What is the runbook?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk());
        String assistantId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT message_id FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant' AND status = 'completed'
                        """,
                        String.class,
                        threadId);
        MvcResult retry =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages/" + assistantId + "/retry")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .accept(MediaType.TEXT_EVENT_STREAM))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mockMvc.perform(asyncDispatch(retry)).andExpect(status().isOk());
        Integer completed =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM chat_message
                        WHERE thread_id = ? AND message_role = 'assistant' AND status = 'completed'
                        """,
                        Integer.class,
                        threadId);
        assertThat(completed).isEqualTo(1);
    }

    @Test
    void ordinaryTimeoutIsDisclosedPartialCoverage() throws Exception {
        LoggedIn owner = loginOwner();
        String okKb = activateDify(owner, "Partial Ok");
        String slowKb =
                activateDify(
                        owner,
                        "Partial Slow",
                        """
                        {"dataset_id":"ds_slow","original_version_mapping":{"doc_1":"v1"},"retrieval_fixture":"timeout"}
                        """);
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"logical_kb_ids\":[\""
                                                                + okKb
                                                                + "\",\""
                                                                + slowKb
                                                                + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        MvcResult ask =
                mockMvc.perform(
                                post("/api/v1/chats/" + threadId + "/messages")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .content("{\"question\":\"How do we rotate the gateway cert?\"}"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        String body =
                mockMvc.perform(asyncDispatch(ask)).andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString();
        assertThat(body).contains("timed_out");
        assertThat(body).contains("successful");
        assertThat(body).doesNotContain("Local retrieval fixture");
        assertThat(body.toLowerCase()).doesNotContain("secret");
        String slowBinding =
                jdbcTemplate.queryForObject(
                        "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, slowKb);
        assertThat(body).contains(slowBinding);
    }

    @Test
    void securityRetrievalSuspendsTheKnowledgeBase() throws Exception {
        LoggedIn owner = loginOwner();
        String kbId =
                activateDify(
                        owner,
                        "Security Fail KB",
                        "{\"dataset_id\":\"ds_sec\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"retrieval_fixture\":\"security\"}");
        String threadId =
                jsonString(
                        mockMvc.perform(
                                        post("/api/v1/chats")
                                                .cookie(owner.session())
                                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"logical_kb_ids\":[\"" + kbId + "\"]}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "thread_id");
        mockMvc.perform(
                        post("/api/v1/chats/" + threadId + "/messages")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("{\"question\":\"How do we rotate the gateway cert?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KB_SECURITY_FAILURE"));
        String lifecycle =
                jdbcTemplate.queryForObject(
                        "SELECT lifecycle FROM logical_knowledge_base WHERE logical_kb_id = ?",
                        String.class,
                        kbId);
        assertThat(lifecycle).isEqualTo("suspended");
    }

    private String activateDify(LoggedIn owner, String name) throws Exception {
        return activateDify(
                owner,
                name,
                """
                {
                  "dataset_id": "ds_1",
                  "original_version_mapping": {"doc_1": "v1"}
                }
                """);
    }

    private String activateDify(LoggedIn owner, String name, String sourceIdentityJson) throws Exception {
        String logicalKbId = createDraft(owner, name, "private");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "bindings": [{
                                            "provider_profile": "dify",
                                            "role": "canonical",
                                            "source_identity": %s
                                          }]
                                        }
                                        """
                                                .formatted(sourceIdentityJson.trim())))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts/" + logicalKbId + "/content-audit")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk());
        LoggedIn admin = loginAdminKeepingOwner();
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability").value("chat_ready"));
        return logicalKbId;
    }

    private String activateGitBrowseOnly(LoggedIn owner, String name) throws Exception {
        String logicalKbId = createDraft(owner, name, "private");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "bindings": [{
                                            "provider_profile": "git_markdown",
                                            "role": "canonical",
                                            "source_identity": {"repo": "org/runbooks", "commit": "abc123def"}
                                          }]
                                        }
                                        """))
                .andExpect(status().isOk());
        LoggedIn admin = loginAdminKeepingOwner();
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability").value("browse_only"));
        return logicalKbId;
    }

    private String createDraft(LoggedIn owner, String name, String discoverability) throws Exception {
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/knowledge-bases/drafts")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Chat fixture",
                                                  "discoverability": "%s",
                                                  "purpose": "support",
                                                  "classification": "internal",
                                                  "model_eligible": true
                                                }
                                                """
                                                        .formatted(name, discoverability)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return jsonString(created.getResponse().getContentAsString(), "logical_kb_id");
    }

    private LoggedIn loginOwner() throws Exception {
        LoggedIn owner = login("kb_owner");
        jdbcTemplate.update("UPDATE atlas_user SET model_entitled = 1 WHERE user_id = ?", owner.userId());
        return owner;
    }

    private LoggedIn loginAdminKeepingOwner() throws Exception {
        LoggedIn admin = login("atlas_admin");
        jdbcTemplate.update(
                "UPDATE atlas_user SET roles = ? WHERE user_id = ?",
                "[\"end_user\",\"kb_owner\",\"atlas_admin\"]",
                admin.userId());
        jdbcTemplate.update("UPDATE atlas_user SET model_entitled = 1 WHERE user_id = ?", admin.userId());
        return admin;
    }

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
        String userId = jsonString(me, "user_id");
        String roles =
                "kb_owner".equals(extraRole)
                        ? "[\"end_user\",\"kb_owner\"]"
                        : "atlas_admin".equals(extraRole)
                                ? "[\"end_user\",\"atlas_admin\"]"
                                : "[\"end_user\"]";
        jdbcTemplate.update("UPDATE atlas_user SET roles = ? WHERE user_id = ?", roles, userId);
        String csrfJson =
                mockMvc.perform(get("/api/v1/auth/csrf").cookie(session))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String csrf = csrfJson.replaceAll(".*\"csrf_token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return new LoggedIn(session, csrf, userId);
    }

    private static String jsonString(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        int from = start + needle.length();
        int end = json.indexOf('"', from);
        return json.substring(from, end);
    }

    private record LoggedIn(Cookie session, String csrf, String userId) {}
}
