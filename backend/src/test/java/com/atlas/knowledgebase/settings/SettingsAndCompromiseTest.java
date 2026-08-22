package com.atlas.knowledgebase.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.atlas.knowledgebase.providers.ProviderConnectionRecord;
import com.atlas.knowledgebase.providers.ProviderConnectionRepository;
import com.atlas.knowledgebase.providers.ProviderConnectionService;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRecord;
import com.atlas.knowledgebase.registry.LogicalKnowledgeBaseRepository;
import com.atlas.knowledgebase.secrets.SecretResolver;
import com.atlas.knowledgebase.session.AtlasSessionRepository;
import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SettingsAndCompromiseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private ProviderConnectionRepository connections;
    @Autowired private ProviderConnectionService connectionService;
    @Autowired private SecretResolver secretResolver;
    @Autowired private AtlasSessionRepository sessions;
    @Autowired private AuditEventRepository auditEvents;
    @Autowired private LogicalKnowledgeBaseRepository knowledgeBases;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProviderState() {
        jdbcTemplate.update("DELETE FROM provider_connection");
        jdbcTemplate.update("DELETE FROM audit_event");
    }

    @Test
    void settingsRequiresSession() throws Exception {
        mockMvc.perform(get("/api/v1/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void settingsProjectsBothProvidersWithoutTokensWhenDisconnected() throws Exception {
        LoggedIn user = login();
        String body =
                mockMvc.perform(get("/api/v1/settings").cookie(user.session()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.identity.user_id").exists())
                        .andExpect(jsonPath("$.identity.display_name").exists())
                        .andExpect(jsonPath("$.model_channel.eligible").value(false))
                        .andExpect(jsonPath("$.model_channel.channel").value("enterprise_approved"))
                        .andExpect(jsonPath("$.providers[0].provider").value("github"))
                        .andExpect(jsonPath("$.providers[0].status").value("reconnect_required"))
                        .andExpect(jsonPath("$.providers[0].granted_scopes").isEmpty())
                        .andExpect(jsonPath("$.providers[1].provider").value("confluence"))
                        .andExpect(jsonPath("$.providers[1].status").value("reconnect_required"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("bearer");
        assertThat(body).doesNotContain("ghp_");
        assertThat(body).doesNotContain("access_token");
        assertThat(body).doesNotContain("pending:oauth");
        assertThat(body).doesNotContain("file:");
    }

    @Test
    void settingsProjectsConnectedGithubAfterCallback() throws Exception {
        LoggedIn user = login();
        connectProvider(user, "github");

        String body =
                mockMvc.perform(get("/api/v1/settings").cookie(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].provider").value("github"))
                .andExpect(jsonPath("$.providers[0].status").value("connected"))
                .andExpect(jsonPath("$.providers[0].granted_scopes[0]").value("repo:read"))
                .andExpect(jsonPath("$.providers[1].status").value("reconnect_required"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("access_token", "bearer ", "pending:oauth", "file:");
    }

    @Test
    void expiredConnectionIsProjectedWithoutWriting() throws Exception {
        LoggedIn user = login();
        connectProvider(user, "github");
        String userId = SessionService.deriveUserId("local-dev");
        ProviderConnectionRecord connected =
                connections.findByUserAndProvider(userId, "github").orElseThrow();
        Instant past = Instant.parse("2020-01-01T00:00:00Z");
        connections.update(
                new ProviderConnectionRecord(
                        connected.connectionId(),
                        connected.userId(),
                        connected.provider(),
                        "connected",
                        connected.grantedScopesJson(),
                        past,
                        connected.lastVerifiedAt(),
                        connected.secretRef(),
                        connected.updatedAt()));

        mockMvc.perform(get("/api/v1/settings").cookie(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].status").value("expired"))
                .andExpect(jsonPath("$.providers[0].expires_at").value(past.toString()));

        ProviderConnectionRecord stored =
                connections.findByUserAndProvider(userId, "github").orElseThrow();
        assertThat(stored.status()).isEqualTo("connected");
        assertThat(stored.secretRef()).isEqualTo(connected.secretRef());
    }

    @Test
    void reconnectReplacesPendingAndConnected() throws Exception {
        LoggedIn user = login();
        mockMvc.perform(
                        post("/api/v1/providers/github/connect")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/v1/providers/github/reconnect")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_url").exists())
                .andExpect(jsonPath("$.state").exists());

        connectProvider(user, "github");
        mockMvc.perform(
                        post("/api/v1/providers/github/reconnect")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isOk());
        var row =
                connections
                        .findByUserAndProvider(SessionService.deriveUserId("local-dev"), "github")
                        .orElseThrow();
        assertThat(row.status()).isEqualTo("reconnect_required");
        assertThat(row.secretRef()).isEqualTo(ProviderConnectionService.PENDING_SECRET_REF);
    }

    @Test
    void revokeDeletesSecretAndProjectsRevoked() throws Exception {
        LoggedIn user = login();
        connectProvider(user, "github");
        String userId = SessionService.deriveUserId("local-dev");
        String secretRef =
                connections.findByUserAndProvider(userId, "github").orElseThrow().secretRef();

        mockMvc.perform(
                        post("/api/v1/providers/github/revoke")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isNoContent());

        var row = connections.findByUserAndProvider(userId, "github").orElseThrow();
        assertThat(row.status()).isEqualTo("revoked");
        assertThat(row.secretRef()).isEqualTo(ProviderConnectionService.REVOKED_SECRET_REF);
        assertThat(row.grantedScopesJson()).isEqualTo("[]");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> secretResolver.resolve(secretRef))
                .isInstanceOf(com.atlas.knowledgebase.secrets.SecretResolutionException.class);

        String body =
                mockMvc.perform(get("/api/v1/settings").cookie(user.session()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.providers[0].status").value("revoked"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain(secretRef);
        assertThat(auditEvents.countByUserAction(userId, "revoke")).isEqualTo(1);
    }

    @Test
    void compromiseRevokesTokensSessionsAndPreservesKbMetadata() throws Exception {
        LoggedIn first = login();
        LoggedIn second = login();
        connectProvider(second, "github");
        String userId = SessionService.deriveUserId("local-dev");
        String secretRef =
                connections.findByUserAndProvider(userId, "github").orElseThrow().secretRef();
        assertThat(sessions.countNotRevokedForUser(userId)).isGreaterThanOrEqualTo(2);

        jdbcTemplate.update("DELETE FROM logical_knowledge_base WHERE logical_kb_id = 'lkb_010_preserve'");
        knowledgeBases.insert(
                new LogicalKnowledgeBaseRecord(
                        "lkb_010_preserve",
                        "Kept Name",
                        "desc",
                        userId,
                        "catalog",
                        "purpose",
                        "internal",
                        false,
                        "browse_only",
                        "draft",
                        "healthy",
                        1,
                        null,
                        false,
                        null,
                        Instant.parse("2026-08-20T00:00:00Z"),
                        Instant.parse("2026-08-20T00:00:00Z"),
                        null));

        mockMvc.perform(
                        post("/api/v1/providers/github/compromise")
                                .cookie(second.session())
                                .header(SessionService.CSRF_HEADER, second.csrf()))
                .andExpect(status().isNoContent());

        var row = connections.findByUserAndProvider(userId, "github").orElseThrow();
        assertThat(row.status()).isEqualTo("reconnect_required");
        assertThat(row.secretRef()).isEqualTo(ProviderConnectionService.REVOKED_SECRET_REF);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> secretResolver.resolve(secretRef))
                .isInstanceOf(com.atlas.knowledgebase.secrets.SecretResolutionException.class);
        assertThat(sessions.countNotRevokedForUser(userId)).isZero();

        mockMvc.perform(get("/api/v1/settings").cookie(second.session()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/settings").cookie(first.session()))
                .andExpect(status().isUnauthorized());

        LogicalKnowledgeBaseRecord kb = knowledgeBases.findById("lkb_010_preserve").orElseThrow();
        assertThat(kb.name()).isEqualTo("Kept Name");
        assertThat(kb.ownerUserId()).isEqualTo(userId);

        String details = auditEvents.latestDetailsByUserAction(userId, "compromise");
        assertThat(details).contains("\"provider\":\"github\"");
        assertThat(details.toLowerCase()).doesNotContain("bearer");
        assertThat(details).doesNotContain("ghp_");
        assertThat(details).doesNotContain(secretRef);
        assertThat(auditEvents.countByUserAction(userId, "compromise")).isEqualTo(1);
    }

    @Test
    void reconnectAndRevokeRequireCsrf() throws Exception {
        LoggedIn user = login();
        mockMvc.perform(post("/api/v1/providers/github/reconnect").cookie(user.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/providers/github/revoke").cookie(user.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/providers/github/compromise").cookie(user.session()))
                .andExpect(status().isForbidden());
    }

    private void connectProvider(LoggedIn user, String provider) throws Exception {
        MvcResult start =
                mockMvc.perform(
                                post("/api/v1/providers/" + provider + "/connect")
                                        .cookie(user.session())
                                        .header(SessionService.CSRF_HEADER, user.csrf()))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie oauthState =
                start.getResponse().getCookie(connectionService.stateCookieName(provider));
        String location = jsonString(start.getResponse().getContentAsString(), "authorization_url");
        mockMvc.perform(get(location).cookie(user.session(), oauthState)).andExpect(status().isFound());
    }

    private LoggedIn login() throws Exception {
        MvcResult start =
                mockMvc.perform(get("/api/v1/auth/sso/start")).andExpect(status().isFound()).andReturn();
        Cookie state = start.getResponse().getCookie(sessionProperties.ssoStateCookieName());
        MvcResult callback =
                mockMvc.perform(get(start.getResponse().getRedirectedUrl()).cookie(state))
                        .andExpect(status().isFound())
                        .andReturn();
        Cookie session = callback.getResponse().getCookie(sessionProperties.getCookieName());
        String csrfJson =
                mockMvc.perform(get("/api/v1/auth/csrf").cookie(session))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String csrf = csrfJson.replaceAll(".*\"csrf_token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return new LoggedIn(session, csrf);
    }

    private static String jsonString(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        int from = start + needle.length();
        int end = json.indexOf('"', from);
        return json.substring(from, end).replace("\\u0026", "&").replace("\\/", "/");
    }

    private record LoggedIn(Cookie session, String csrf) {}
}
