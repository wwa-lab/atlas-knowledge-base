package com.atlas.knowledgebase.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.secrets.SecretResolver;
import com.atlas.knowledgebase.session.SessionProperties;
import com.atlas.knowledgebase.session.SessionService;
import jakarta.servlet.http.Cookie;
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
class ProviderConnectTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private ProviderConnectionRepository connections;
    @Autowired private SecretResolver secretResolver;
    @Autowired private ProviderConnectionService connectionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearProviderConnections() {
        jdbcTemplate.update("DELETE FROM provider_connection");
    }

    @Test
    void connectRequiresSession() throws Exception {
        mockMvc.perform(post("/api/v1/providers/github/connect"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidProviderIs400() throws Exception {
        LoggedIn user = login();
        mockMvc.perform(
                        post("/api/v1/providers/bitbucket/connect")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PROVIDER"));
    }

    @Test
    void connectAndCallbackStoreSecretRefWithoutLeakingToken() throws Exception {
        LoggedIn user = login();
        MvcResult start =
                mockMvc.perform(
                                post("/api/v1/providers/github/connect")
                                        .cookie(user.session())
                                        .header(SessionService.CSRF_HEADER, user.csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.authorization_url").exists())
                        .andExpect(jsonPath("$.state").exists())
                        .andReturn();
        String body = start.getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("bearer");
        assertThat(body).doesNotContain("ghp_");
        assertThat(body).doesNotContain("access_token");

        Cookie oauthState =
                start.getResponse().getCookie(connectionService.stateCookieName("github"));
        String location = jsonString(body, "authorization_url");
        location = location + "%20admin:org";

        mockMvc.perform(get(location).cookie(user.session(), oauthState))
                .andExpect(status().isFound());

        var row =
                connections
                        .findByUserAndProvider(
                                com.atlas.knowledgebase.session.SessionService.deriveUserId("local-dev"),
                                "github")
                        .orElseThrow();
        assertThat(row.status()).isEqualTo("connected");
        assertThat(row.secretRef()).startsWith("file:");
        assertThat(row.grantedScopesJson()).isEqualTo("[\"repo:read\"]");
        assertThat(row.grantedScopesJson()).doesNotContain("admin:org");

        char[] stored = secretResolver.resolve(row.secretRef());
        try {
            assertThat(stored.length).isPositive();
        } finally {
            java.util.Arrays.fill(stored, '\0');
        }
    }

    @Test
    void secondConnectWhileConnectedIs409() throws Exception {
        LoggedIn user = login();
        MvcResult start =
                mockMvc.perform(
                                post("/api/v1/providers/confluence/connect")
                                        .cookie(user.session())
                                        .header(SessionService.CSRF_HEADER, user.csrf()))
                        .andExpect(status().isOk())
                        .andReturn();
        Cookie oauthState =
                start.getResponse().getCookie(connectionService.stateCookieName("confluence"));
        String location = jsonString(start.getResponse().getContentAsString(), "authorization_url");
        mockMvc.perform(get(location).cookie(user.session(), oauthState)).andExpect(status().isFound());

        mockMvc.perform(
                        post("/api/v1/providers/confluence/connect")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CONNECTING"));
    }

    @Test
    void abandonedPendingConnectIsReplaceable() throws Exception {
        LoggedIn user = login();
        mockMvc.perform(
                        post("/api/v1/providers/github/connect")
                                .cookie(user.session())
                                .header(SessionService.CSRF_HEADER, user.csrf()))
                .andExpect(status().isOk());

        MvcResult retry =
                mockMvc.perform(
                                post("/api/v1/providers/github/connect")
                                        .cookie(user.session())
                                        .header(SessionService.CSRF_HEADER, user.csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.authorization_url").exists())
                        .andReturn();
        assertThat(retry.getResponse().getCookie(connectionService.stateCookieName("github")))
                .isNotNull();
    }

    @Test
    void failClosedClientRefusesRedeemOnDeployedPlane() {
        FailClosedProviderAuthorizationClient client = new FailClosedProviderAuthorizationClient();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> client.authorizationUrl("github", "state", java.util.List.of("repo:read")))
                .isInstanceOf(ProviderOauthNotConfiguredException.class);
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
