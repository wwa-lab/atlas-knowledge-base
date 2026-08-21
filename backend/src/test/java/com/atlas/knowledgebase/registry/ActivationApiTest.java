package com.atlas.knowledgebase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import jakarta.servlet.http.Cookie;
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
class ActivationApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LogicalKnowledgeBaseRepository knowledgeBases;

    @Test
    void endUserCannotRunConnectionTest() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraftWithDify(owner, "Gated KB", true);
        LoggedIn user = login("end_user");
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts/" + logicalKbId + "/connection-test")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KB_OWNER_REQUIRED"));
    }

    @Test
    void ownerRunsConnectionTestAndContentAuditThenAdminActivates() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraftWithDify(owner, "Ready KB", true);
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts/" + logicalKbId + "/connection-test")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
        MvcResult audit =
                mockMvc.perform(
                                post("/api/v1/knowledge-bases/drafts/" + logicalKbId + "/content-audit")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(10))
                        .andExpect(jsonPath("$.chat_eligible").value(10))
                        .andExpect(jsonPath("$.excluded").value(0))
                        .andReturn();
        String auditBody = audit.getResponse().getContentAsString();
        assertThat(auditBody).doesNotContain("Runbook");
        assertThat(auditBody.toLowerCase()).doesNotContain("citation");

        String csv =
                mockMvc.perform(
                                get("/api/v1/knowledge-bases/" + logicalKbId + "/content-audit/remediation")
                                        .cookie(owner.session()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(csv).startsWith("document_id,reason");
        assertThat(csv.toLowerCase()).doesNotContain("title");

        LoggedIn admin = login("atlas_admin");
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("active"))
                .andExpect(jsonPath("$.capability").value("chat_ready"))
                .andExpect(jsonPath("$.activated_at").exists());
        assertThat(knowledgeBases.findById(logicalKbId).orElseThrow().lifecycle()).isEqualTo("active");
    }

    @Test
    void activateWithoutDifyAuditStaysDraft() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraftWithDify(owner, "No audit", true);
        LoggedIn admin = login("atlas_admin");
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HARD_GATE_FAILURE"));
        assertThat(knowledgeBases.findById(logicalKbId).orElseThrow().lifecycle()).isEqualTo("draft");
    }

    @Test
    void gitWithoutKbActivatesBrowseOnly() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraft(owner, "Git browse");
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
                                            "source_identity": {"repo": "org/runbooks"}
                                          }]
                                        }
                                        """))
                .andExpect(status().isOk());
        LoggedIn admin = login("atlas_admin");
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("active"))
                .andExpect(jsonPath("$.capability").value("browse_only"));
    }

    @Test
    void ownerCannotActivate() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraftWithDify(owner, "Owner activate", true);
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void ownerlessActiveKbIsSuspended() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraftWithDify(owner, "Orphan", true);
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts/" + logicalKbId + "/content-audit")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf()))
                .andExpect(status().isOk());
        LoggedIn admin = login("atlas_admin");
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/" + logicalKbId + "/activate")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE atlas_user SET roles = ? WHERE user_id = ?",
                "[\"end_user\",\"atlas_admin\"]",
                owner.userId());
        mockMvc.perform(
                        post("/api/v1/admin/knowledge-bases/" + logicalKbId + "/suspend-ownerless")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("suspended"));
    }

    @Test
    void suspendOwnerlessRejectedWhenOwnerAccountable() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraft(owner, "Still owned");
        jdbcTemplate.update(
                "UPDATE atlas_user SET roles = ? WHERE user_id = ?",
                "[\"end_user\",\"kb_owner\",\"atlas_admin\"]",
                owner.userId());
        mockMvc.perform(
                        post("/api/v1/admin/knowledge-bases/" + logicalKbId + "/suspend-ownerless")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NOT_OWNERLESS"));
    }

    private String createDraftWithDify(LoggedIn owner, String name, boolean versionMapped) throws Exception {
        String logicalKbId = createDraft(owner, name);
        String mapping =
                versionMapped
                        ? ", \"original_version_mapping\": {\"doc_1\": \"v1\"}"
                        : "";
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
                                            "source_identity": {"dataset_id": "ds_1"%s}
                                          }]
                                        }
                                        """
                                                .formatted(mapping)))
                .andExpect(status().isOk());
        return logicalKbId;
    }

    private String createDraft(LoggedIn owner, String name) throws Exception {
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
                                                  "description": "Activation fixture",
                                                  "discoverability": "private",
                                                  "purpose": "support",
                                                  "classification": "internal",
                                                  "model_eligible": true
                                                }
                                                """
                                                        .formatted(name)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return jsonString(created.getResponse().getContentAsString(), "logical_kb_id");
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
