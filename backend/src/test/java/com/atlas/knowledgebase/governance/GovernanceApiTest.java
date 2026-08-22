package com.atlas.knowledgebase.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
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
class GovernanceApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void nonAdminCannotPreviewOrMutate() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance auth " + UUID.randomUUID());
        LoggedIn endUser = login("end_user");

        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/impact-preview")
                                .cookie(endUser.session())
                                .header(SessionService.CSRF_HEADER, endUser.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void disableRequiresPreviewStopsOnlyItsBindingAndAuditsContentFree() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance disable " + UUID.randomUUID());
        LoggedIn admin = loginAdminKeepingOwner();

        MvcResult preview =
                mockMvc.perform(
                                post("/api/v1/admin/bindings/" + bindingId + "/impact-preview")
                                        .cookie(admin.session())
                                        .header(SessionService.CSRF_HEADER, admin.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.operation").value("disable"))
                        .andExpect(jsonPath("$.enabled").value(true))
                        .andExpect(jsonPath("$.new_retrieval_stopped").value(false))
                        .andReturn();
        String previewId = field(preview, "impact_preview_id");

        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/disable")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + previewId
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binding_id").value(bindingId))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.new_retrieval_stopped").value(true));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled FROM binding WHERE binding_id = ?", Integer.class, bindingId))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_event WHERE binding_id = ? AND action = 'disable'",
                                Integer.class,
                                bindingId))
                .isEqualTo(1);
        String details =
                jdbcTemplate.queryForObject(
                        "SELECT details FROM audit_event WHERE binding_id = ? AND action = 'disable'",
                        String.class,
                        bindingId);
        assertThat(details).doesNotContain("dataset_id").doesNotContain("source_identity");
    }

    @Test
    void killSwitchIsIndependentAndStalePreviewFailsClosed() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance kill " + UUID.randomUUID());
        LoggedIn admin = loginAdminKeepingOwner();
        String previewId =
                preview(admin, bindingId, "{\"operation\":\"kill_switch\"}");

        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/kill-switch")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + previewId
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kill_switch").value(true))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/kill-switch")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + previewId
                                                + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IMPACT_PREVIEW_REPLAYED"));
    }

    @Test
    void rollbackRestoresHistoricalConfigurationAfterRevalidation() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance rollback " + UUID.randomUUID());
        LoggedIn admin = loginAdminKeepingOwner();
        String disablePreview = preview(admin, bindingId, "{}");
        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/disable")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + disablePreview
                                                + "\"}"))
                .andExpect(status().isOk());

        String rollbackPreview = preview(admin, bindingId, "{\"operation\":\"rollback\"}");
        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/rollback")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + rollbackPreview
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.kill_switch").value(false));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT source_identity FROM binding WHERE binding_id = ?", String.class, bindingId))
                .contains("original_version_mapping");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM binding_config_history WHERE binding_id = ?", Integer.class, bindingId))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void rollbackFailsClosedWhenHistoricalTargetNeedsRevalidation() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance rollback gate " + UUID.randomUUID());
        LoggedIn admin = loginAdminKeepingOwner();
        String disablePreview = preview(admin, bindingId, "{}");
        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/disable")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + disablePreview
                                                + "\"}"))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE binding_config_history SET source_identity = ? WHERE binding_id = ? AND config_version = 1",
                "{\"dataset_id\":\"unsafe\",\"original_version_mapping\":{\"doc_1\":\"v1\"},\"acl_mixed\":true}",
                bindingId);
        String rollbackPreview = preview(admin, bindingId, "{\"operation\":\"rollback\"}");
        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/rollback")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + rollbackPreview
                                                + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVALIDATION_REQUIRED"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT enabled FROM binding WHERE binding_id = ?", Integer.class, bindingId))
                .isZero();
    }

    @Test
    void retireDisablesBindingAndRetiresSingleBindingKnowledgeBase() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance retire " + UUID.randomUUID());
        String logicalKbId = jdbcTemplate.queryForObject(
                "SELECT logical_kb_id FROM binding WHERE binding_id = ?", String.class, bindingId);
        LoggedIn admin = loginAdminKeepingOwner();
        String preview = preview(admin, bindingId, "{\"operation\":\"retire\"}");

        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/retire")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + preview
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.new_retrieval_stopped").value(true))
                .andExpect(jsonPath("$.logical_kb_lifecycle").value("retired"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT lifecycle FROM logical_knowledge_base WHERE logical_kb_id = ?",
                        String.class,
                        logicalKbId))
                .isEqualTo("retired");
    }

    @Test
    void retireTreatsFeatureDisabledRemainingBindingAsUnavailable() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance feature gate " + UUID.randomUUID());
        String logicalKbId =
                jdbcTemplate.queryForObject(
                        "SELECT logical_kb_id FROM binding WHERE binding_id = ?", String.class, bindingId);
        String featureDisabledBindingId = "bnd_feature_disabled_" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO binding (
                  binding_id, logical_kb_id, provider_profile, source_identity, binding_role,
                  auth_method, health, enabled, kill_switch, feature_flag, freshness_policy,
                  locator_rules, credential_owner, region_constraints, config_version,
                  created_at, updated_at)
                VALUES (?, ?, 'confluence', '{}', 'supplemental', 'delegated_user', 'healthy',
                        1, 0, 0, NULL, '{}', 'owner@example.com', NULL, 1, ?, ?)
                """,
                featureDisabledBindingId,
                logicalKbId,
                Timestamp.from(now),
                Timestamp.from(now));

        LoggedIn admin = loginAdminKeepingOwner();
        MvcResult previewResult =
                mockMvc.perform(
                                post("/api/v1/admin/bindings/" + bindingId + "/impact-preview")
                                        .cookie(admin.session())
                                        .header(SessionService.CSRF_HEADER, admin.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"operation\":\"retire\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.would_retire_logical_kb").value(true))
                        .andExpect(jsonPath("$.runtime_binding_ids[0]").value(bindingId))
                        .andReturn();
        String preview = field(previewResult, "impact_preview_id");

        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/retire")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + preview
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logical_kb_lifecycle").value("retired"));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT enabled FROM binding WHERE binding_id = ?",
                                Integer.class,
                                featureDisabledBindingId))
                .isEqualTo(1);
    }

    @Test
    void retireKeepsSuspendedKnowledgeBaseWhenAnotherBindingCanResume() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance suspended retire " + UUID.randomUUID());
        String logicalKbId =
                jdbcTemplate.queryForObject(
                        "SELECT logical_kb_id FROM binding WHERE binding_id = ?", String.class, bindingId);
        String remainingBindingId = "bnd_suspended_remaining_" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO binding (
                  binding_id, logical_kb_id, provider_profile, source_identity, binding_role,
                  auth_method, health, enabled, kill_switch, feature_flag, freshness_policy,
                  locator_rules, credential_owner, region_constraints, config_version,
                  created_at, updated_at)
                VALUES (?, ?, 'confluence', '{}', 'supplemental', 'delegated_user', 'healthy',
                        1, 0, 1, NULL, '{}', 'owner@example.com', NULL, 1, ?, ?)
                """,
                remainingBindingId,
                logicalKbId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbcTemplate.update(
                "UPDATE logical_knowledge_base SET lifecycle = 'suspended' WHERE logical_kb_id = ?",
                logicalKbId);

        LoggedIn admin = loginAdminKeepingOwner();
        MvcResult previewResult =
                mockMvc.perform(
                                post("/api/v1/admin/bindings/" + bindingId + "/impact-preview")
                                        .cookie(admin.session())
                                        .header(SessionService.CSRF_HEADER, admin.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"operation\":\"retire\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.would_retire_logical_kb").value(false))
                        .andExpect(jsonPath("$.runtime_binding_ids").isEmpty())
                        .andReturn();
        String preview = field(previewResult, "impact_preview_id");

        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/retire")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"confirm\":true,\"impact_preview_id\":\""
                                                + preview
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logical_kb_lifecycle").value("suspended"));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT enabled FROM binding WHERE binding_id = ?",
                                Integer.class,
                                remainingBindingId))
                .isEqualTo(1);
    }

    @Test
    void missingCsrfAndMissingConfirmationAreRejected() throws Exception {
        LoggedIn owner = login("kb_owner");
        String bindingId = activeDifyBinding(owner, "Governance csrf " + UUID.randomUUID());
        LoggedIn admin = loginAdminKeepingOwner();
        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/impact-preview")
                                .cookie(admin.session())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/v1/admin/bindings/" + bindingId + "/disable")
                                .cookie(admin.session())
                                .header(SessionService.CSRF_HEADER, admin.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CONFIRM_REQUIRED"));
    }

    private String activeDifyBinding(LoggedIn owner, String name) throws Exception {
        MvcResult created =
                mockMvc.perform(
                                post("/api/v1/knowledge-bases/drafts")
                                        .cookie(owner.session())
                                        .header(SessionService.CSRF_HEADER, owner.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\""
                                                        + name
                                                        + "\",\"description\":\"Governance fixture\",\"discoverability\":\"private\",\"purpose\":\"support\",\"classification\":\"internal\",\"model_eligible\":true}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String logicalKbId = field(created, "logical_kb_id");
        mockMvc.perform(
                        patch("/api/v1/knowledge-bases/drafts/" + logicalKbId)
                                .cookie(owner.session())
                                .header(SessionService.CSRF_HEADER, owner.csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"config_version\":1,\"bindings\":[{\"provider_profile\":\"dify\",\"role\":\"canonical\",\"source_identity\":{\"dataset_id\":\"ds_"
                                                + UUID.randomUUID()
                                                + "\",\"original_version_mapping\":{\"doc_1\":\"v1\"}}}]}"))
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
        return jdbcTemplate.queryForObject(
                "SELECT binding_id FROM binding WHERE logical_kb_id = ?", String.class, logicalKbId);
    }

    private String preview(LoggedIn admin, String bindingId, String body) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/admin/bindings/" + bindingId + "/impact-preview")
                                        .cookie(admin.session())
                                        .header(SessionService.CSRF_HEADER, admin.csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn();
        return field(result, "impact_preview_id");
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
        String userId = field(me, "user_id");
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
        return new LoggedIn(session, field(csrfJson, "csrf_token"), userId);
    }

    private String field(MvcResult result, String field) throws Exception {
        return field(result.getResponse().getContentAsString(), field);
    }

    private String field(String json, String field) throws Exception {
        JsonNode value = objectMapper.readTree(json).path(field);
        return value.asText();
    }

    private record LoggedIn(Cookie session, String csrf, String userId) {}
}
