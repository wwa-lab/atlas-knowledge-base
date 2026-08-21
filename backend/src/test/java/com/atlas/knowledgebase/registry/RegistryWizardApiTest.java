package com.atlas.knowledgebase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class RegistryWizardApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AtlasUserRepository userRepository;
    @Autowired private BindingRepository bindingRepository;

    @Test
    void endUserCannotCreateDraft() throws Exception {
        LoggedIn user = login(false);
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(basicsJson("AMH Support KB")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KB_OWNER_REQUIRED"));
    }

    @Test
    void ownerCreatesAndPatchesDraft() throws Exception {
        LoggedIn owner = login(true);
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/knowledge-bases/drafts")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(basicsJson("AMH Support KB")))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.lifecycle").value("draft"))
                        .andExpect(jsonPath("$.config_version").value(1))
                        .andExpect(jsonPath("$.logical_kb_id").exists())
                        .andReturn();
        String logicalKbId = jsonString(created.getResponse().getContentAsString(), "logical_kb_id");
        assertThat(auditCount(logicalKbId, "register_draft")).isEqualTo(1);

        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "name": "AMH Support KB v2",
                                          "bindings": [
                                            {
                                              "provider_profile": "dify",
                                              "role": "canonical",
                                              "source_identity": {"dataset_id": "ds_1"},
                                              "credential_owner": "%s",
                                              "region_constraints": {"region": "eu"}
                                            },
                                            {
                                              "provider_profile": "git_markdown",
                                              "role": "supplemental",
                                              "source_identity": {"repo": "org/kb"},
                                              "credential_owner": "%s",
                                              "region_constraints": {"region": "eu"}
                                            }
                                          ]
                                        }
                                        """
                                                .formatted(owner.userId(), owner.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config_version").value(2))
                .andExpect(jsonPath("$.name").value("AMH Support KB v2"));
        assertThat(bindingRepository.findByLogicalKbId(logicalKbId)).hasSize(2);
        assertThat(auditCount(logicalKbId, "update_draft")).isEqualTo(1);
        assertThat(latestAuditDetails(logicalKbId, "update_draft")).doesNotContain("vault://");
        assertThat(latestAuditDetails(logicalKbId, "update_draft")).doesNotContain("ghp_");
    }

    @Test
    void incompatibleRegionIs422() throws Exception {
        LoggedIn owner = login(true);
        String logicalKbId = createDraft(owner, "Region KB");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "bindings": [
                                            {
                                              "provider_profile": "dify",
                                              "role": "canonical",
                                              "source_identity": {"dataset_id": "ds_1"},
                                              "region_constraints": {"region": "eu"}
                                            },
                                            {
                                              "provider_profile": "confluence",
                                              "role": "mirror",
                                              "source_identity": {"space_id": "SP"},
                                              "region_constraints": {"region": "us"}
                                            }
                                          ]
                                        }
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INCOMPATIBLE_BINDINGS"));
        assertThat(bindingRepository.findByLogicalKbId(logicalKbId)).isEmpty();
    }

    @Test
    void staleConfigVersionIs409() throws Exception {
        LoggedIn owner = login(true);
        String logicalKbId = createDraft(owner, "Conflict KB");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"config_version\":1,\"name\":\"once\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"config_version\":1,\"name\":\"twice\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIG_VERSION_CONFLICT"));
    }

    @Test
    void mixedModelEligibilityBecomesBrowseOnly() throws Exception {
        LoggedIn owner = login(true);
        String logicalKbId = createDraft(owner, "Mixed eligibility");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "bindings": [
                                            {
                                              "provider_profile": "dify",
                                              "role": "canonical",
                                              "source_identity": {"dataset_id": "ds_1"},
                                              "model_eligible": true,
                                              "region_constraints": {"region": "eu"}
                                            },
                                            {
                                              "provider_profile": "confluence",
                                              "role": "supplemental",
                                              "source_identity": {"space_id": "SP"},
                                              "model_eligible": false,
                                              "region_constraints": {"region": "eu"}
                                            }
                                          ]
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capability").value("browse_only"))
                .andExpect(jsonPath("$.config_version").value(2));
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"config_version\":2,\"name\":\"Mixed eligibility v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mixed eligibility v2"))
                .andExpect(jsonPath("$.capability").value("browse_only"))
                .andExpect(jsonPath("$.config_version").value(3));
    }

    @Test
    void distinctCredentialOwnersWithSharedRegionAreAccepted() throws Exception {
        LoggedIn owner = login(true);
        String logicalKbId = createDraft(owner, "Split owners");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "config_version": 1,
                                          "bindings": [
                                            {
                                              "provider_profile": "dify",
                                              "role": "canonical",
                                              "source_identity": {"dataset_id": "ds_1"},
                                              "credential_owner": "%s",
                                              "region_constraints": {"region": "eu"}
                                            },
                                            {
                                              "provider_profile": "git_markdown",
                                              "role": "supplemental",
                                              "source_identity": {"repo": "org/kb"},
                                              "credential_owner": "someone-else",
                                              "region_constraints": {"region": "eu"}
                                            }
                                          ]
                                        }
                                        """
                                                .formatted(owner.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config_version").value(2));
        assertThat(bindingRepository.findByLogicalKbId(logicalKbId)).hasSize(2);
    }

    @Test
    void atlasAdminWithoutKbOwnerCannotCreateDraft() throws Exception {
        LoggedIn admin = login(false);
        jdbcTemplate.update(
                "UPDATE atlas_user SET roles = ? WHERE user_id = ?",
                "[\"atlas_admin\"]",
                admin.userId());
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(basicsJson("Admin console")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("KB_OWNER_REQUIRED"));
    }

    @Test
    void otherOwnerCannotPatchDraft() throws Exception {
        LoggedIn owner = login(true);
        String logicalKbId = createDraft(owner, "Owned draft");
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        if (userRepository.findById("usr_other_owner").isEmpty()) {
            userRepository.insert(
                    new AtlasUserRecord(
                            "usr_other_owner",
                            "sso-other-owner",
                            "Other",
                            null,
                            "[\"kb_owner\"]",
                            false,
                            now,
                            now));
        }
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET owner_user_id = ? WHERE logical_kb_id = ?",
                "usr_other_owner",
                logicalKbId);
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"config_version\":1,\"name\":\"hijack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_DRAFT_OWNER"));
    }

    @Test
    void missingNameIs422() throws Exception {
        LoggedIn owner = login(true);
        mockMvc.perform(
                        post("/api/v1/knowledge-bases/drafts")
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "",
                                          "discoverability": "private",
                                          "purpose": "support",
                                          "classification": "internal"
                                        }
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NAME_REQUIRED"));
    }

    private String createDraft(LoggedIn owner, String name) throws Exception {
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/knowledge-bases/drafts")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(basicsJson(name)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return jsonString(created.getResponse().getContentAsString(), "logical_kb_id");
    }

    private LoggedIn login(boolean kbOwner) throws Exception {
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
        String roles = kbOwner ? "[\"end_user\",\"kb_owner\"]" : "[\"end_user\"]";
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

    private static String basicsJson(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Support answers",
                  "discoverability": "private",
                  "purpose": "support",
                  "classification": "internal",
                  "model_eligible": true
                }
                """
                .formatted(name);
    }

    private static String jsonString(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        int from = start + needle.length();
        int end = json.indexOf('"', from);
        return json.substring(from, end);
    }

    private int auditCount(String logicalKbId, String action) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_event WHERE logical_kb_id = ? AND action = ?",
                        Integer.class,
                        logicalKbId,
                        action);
        return count == null ? 0 : count;
    }

    private String latestAuditDetails(String logicalKbId, String action) {
        String details =
                jdbcTemplate.query(
                        """
                        SELECT details FROM audit_event
                        WHERE logical_kb_id = ? AND action = ?
                        ORDER BY occurred_at DESC
                        """,
                        rs -> rs.next() ? rs.getString("details") : "",
                        logicalKbId,
                        action);
        return details == null ? "" : details;
    }

    private record LoggedIn(Cookie session, String csrf, String userId) {}
}
