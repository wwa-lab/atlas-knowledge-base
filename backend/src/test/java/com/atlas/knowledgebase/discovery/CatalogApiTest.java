package com.atlas.knowledgebase.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
class CatalogApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LogicalKnowledgeBaseRepository knowledgeBases;
    @Autowired private AtlasUserRepository users;

    @Test
    void unauthenticatedCatalogIs401() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REQUIRED"));
    }

    @Test
    void privateKbIsHiddenFromUnauthorizedUser() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Private Git", "private");
        reassignOwner(logicalKbId);
        LoggedIn stranger = login("end_user");
        String body =
                mockMvc.perform(get("/api/v1/knowledge-bases").cookie(stranger.session()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain(logicalKbId);
        mockMvc.perform(get("/api/v1/knowledge-bases/" + logicalKbId).cookie(stranger.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KB_NOT_FOUND"));
    }

    @Test
    void catalogUnauthorizedShowsRequestPathOnly() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Public Catalog Git", "catalog");
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET access_request_url = ? WHERE logical_kb_id = ?",
                "https://iam.example/request/" + logicalKbId,
                logicalKbId);
        reassignOwner(logicalKbId);
        LoggedIn stranger = login("end_user");
        mockMvc.perform(get("/api/v1/knowledge-bases").cookie(stranger.session()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')].access.authorized")
                                .value(hasItem(false)))
                .andExpect(
                        jsonPath(
                                        "$.items[?(@.logical_kb_id=='"
                                                + logicalKbId
                                                + "')].access.access_request_url")
                                .value(hasItem("https://iam.example/request/" + logicalKbId)))
                .andExpect(
                        jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')].description")
                                .isEmpty());
        String detail =
                mockMvc.perform(get("/api/v1/knowledge-bases/" + logicalKbId).cookie(stranger.session()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.access.authorized").value(false))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(detail).doesNotContain("source_identity");
        assertThat(detail).doesNotContain("secret");
        assertThat(detail).doesNotContain("\"bindings\"");
        assertThat(detail).doesNotContain("\"description\"");
        assertThat(detail).doesNotContain("\"overview\"");
    }

    @Test
    void ownerSeesAuthorizedCatalogAndGitBrowse() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Owner Git", "private");
        mockMvc.perform(get("/api/v1/knowledge-bases").cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')].access.authorized")
                                .value(hasItem(true)))
                .andExpect(
                        jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')].capability")
                                .value(hasItem("browse_only")))
                .andExpect(
                        jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')].scale.git_markdown.paths")
                                .value(hasItem(2)));
        mockMvc.perform(get("/api/v1/knowledge-bases/" + logicalKbId).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.authorized").value(true))
                .andExpect(jsonPath("$.bindings[0].provider_profile").value("git_markdown"))
                .andExpect(jsonPath("$.chat_disabled_reason").exists())
                .andExpect(jsonPath("$.chat_start_allowed").value(false))
                .andExpect(jsonPath("$.overview.capability").value("browse_only"))
                .andExpect(jsonPath("$.sources[0].provider_profile").value("git_markdown"))
                .andExpect(jsonPath("$.content.browse_available").value(true))
                .andExpect(jsonPath("$.content.summary_available").value(false))
                .andExpect(jsonPath("$.content.cross_file_search_available").value(false))
                .andExpect(jsonPath("$.audit_summary").exists());
        MvcResult tree =
                mockMvc.perform(
                                get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/tree")
                                        .cookie(owner.session()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.entries[?(@.path=='manifest.json')]").exists())
                        .andExpect(jsonPath("$.original_url").exists())
                        .andReturn();
        String treeBody = tree.getResponse().getContentAsString();
        assertThat(treeBody.toLowerCase()).doesNotContain("summary");
        assertThat(treeBody.toLowerCase()).doesNotContain("\"chat\"");
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/preview")
                                .cookie(owner.session())
                                .param("path", "docs/runbook.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").exists())
                .andExpect(jsonPath("$.path").value("docs/runbook.md"));
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/preview")
                                .cookie(owner.session())
                                .param("path", "manifest.json"))
                .andExpect(status().isOk());
        assertThat(knowledgeBases.findById(logicalKbId).orElseThrow().capability()).isEqualTo("browse_only");
    }

    @Test
    void draftIsOmittedFromCatalog() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraft(owner, "Still Draft", "catalog");
        String body =
                mockMvc.perform(get("/api/v1/knowledge-bases").cookie(owner.session()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain(logicalKbId);
        mockMvc.perform(get("/api/v1/knowledge-bases/" + logicalKbId).cookie(owner.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedUserCannotBrowseCatalogKb() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Catalog no browse", "catalog");
        reassignOwner(logicalKbId);
        LoggedIn stranger = login("end_user");
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/tree")
                                .cookie(stranger.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("BROWSE_FORBIDDEN"));
    }

    @Test
    void difyKbCannotBrowseTree() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = createDraft(owner, "Dify only", "private");
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
                                            "source_identity": {
                                              "dataset_id": "ds_1",
                                              "original_version_mapping": {"doc_1": "v1"}
                                            }
                                          }]
                                        }
                                        """))
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
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/tree")
                                .cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GIT_BROWSE_REQUIRED"));
    }

    @Test
    void privateBrowseIsHiddenNotForbidden() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Private no browse leak", "private");
        reassignOwner(logicalKbId);
        LoggedIn stranger = login("end_user");
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/tree")
                                .cookie(stranger.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KB_NOT_FOUND"));
    }

    @Test
    void previewRequiresKnownFilePath() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Preview paths", "private");
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/preview")
                                .cookie(owner.session()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PATH_REQUIRED"));
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/preview")
                                .cookie(owner.session())
                                .param("path", "missing.md"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PATH_NOT_FOUND"));
    }

    @Test
    void atlasAdminCanBrowseAfterOwnerReassigned() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Admin browse", "private");
        reassignOwner(logicalKbId);
        LoggedIn admin = loginAdminKeepingOwner();
        mockMvc.perform(get("/api/v1/knowledge-bases/" + logicalKbId).cookie(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.authorized").value(true));
        mockMvc.perform(
                        get("/api/v1/knowledge-bases/" + logicalKbId + "/browse/tree")
                                .cookie(admin.session()))
                .andExpect(status().isOk());
    }

    @Test
    void suspendedCatalogKbIsHiddenFromOrdinaryUsers() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "Suspended catalog", "catalog");
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET lifecycle = 'suspended' WHERE logical_kb_id = ?",
                logicalKbId);
        mockMvc.perform(get("/api/v1/knowledge-bases").cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')].lifecycle")
                                .value(hasItem("suspended")));
        reassignOwner(logicalKbId);
        LoggedIn stranger = login("end_user");
        String body =
                mockMvc.perform(get("/api/v1/knowledge-bases").cookie(stranger.session()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain(logicalKbId);
        mockMvc.perform(get("/api/v1/knowledge-bases/" + logicalKbId).cookie(stranger.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void catalogSearchMatchesLogicalMetadataNotFileContent() throws Exception {
        LoggedIn owner = login("kb_owner");
        String logicalKbId = activateGit(owner, "UniqueSearchZebra Git", "private");
        mockMvc.perform(get("/api/v1/knowledge-bases").cookie(owner.session()).param("q", "Zebra"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')].name")
                                .value(hasItem("UniqueSearchZebra Git")));
        mockMvc.perform(
                        get("/api/v1/knowledge-bases")
                                .cookie(owner.session())
                                .param("q", "runbook.md")
                                .param("capability", "browse_only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')]").isEmpty());
        mockMvc.perform(
                        get("/api/v1/knowledge-bases")
                                .cookie(owner.session())
                                .param("provider", "git_markdown")
                                .param("freshness", "stale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.logical_kb_id=='" + logicalKbId + "')]").isEmpty());
    }

    private String activateGit(LoggedIn owner, String name, String discoverability) throws Exception {
        String logicalKbId = createDraft(owner, name, discoverability);
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
                                                  "description": "Catalog fixture",
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

    private void reassignOwner(String logicalKbId) {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        if (users.findById("usr_other").isEmpty()) {
            users.insert(
                    new AtlasUserRecord(
                            "usr_other",
                            "other-sso",
                            "Other Owner",
                            null,
                            "[\"end_user\",\"kb_owner\"]",
                            false,
                            now,
                            now));
        }
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET owner_user_id = ? WHERE logical_kb_id = ?",
                "usr_other",
                logicalKbId);
    }

    private LoggedIn loginAdminKeepingOwner() throws Exception {
        LoggedIn admin = login("atlas_admin");
        jdbcTemplate.update(
                "UPDATE atlas_user SET roles = ? WHERE user_id = ?",
                "[\"end_user\",\"kb_owner\",\"atlas_admin\"]",
                admin.userId());
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
