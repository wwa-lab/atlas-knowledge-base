package com.atlas.knowledgebase.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthSessionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private AtlasUserRepository userRepository;
    @Autowired private AtlasSessionRepository sessionRepository;

    @Test
    void meWithoutCookieIs401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.category").value("authentication"))
                .andExpect(jsonPath("$.error.code").value("SESSION_REQUIRED"));
    }

    @Test
    void localSsoIssuesHttpOnlySessionCookieAndMeProjection() throws Exception {
        Cookie sessionCookie = completeLocalSso();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getSecure()).isFalse();
        assertThat(sessionCookie.getName()).isEqualTo("Atlas-Session");
        assertThat(sessionCookie.getName()).doesNotStartWith("__Host-");

        String body =
                mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.user_id").value(SessionService.deriveUserId("local-dev")))
                        .andExpect(jsonPath("$.roles[0]").value("end_user"))
                        .andExpect(jsonPath("$.session.issued_at").exists())
                        .andExpect(jsonPath("$.csrf_secret").doesNotExist())
                        .andExpect(jsonPath("$.session_id").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain("csrf_secret");
        assertThat(body.toLowerCase()).doesNotContain("bearer ");
        assertThat(body).doesNotContain("ghp_");
    }

    @Test
    void csrfRequiredOnLogoutThenSessionEnds() throws Exception {
        Cookie sessionCookie = completeLocalSso();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_MISMATCH"));

        String csrf =
                mockMvc.perform(get("/api/v1/auth/csrf").cookie(sessionCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(csrf).contains("csrf_token");
        String token =
                csrf.replaceAll(".*\"csrf_token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(sessionCookie)
                                .header(SessionService.CSRF_HEADER, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSessionIsUnauthorized() throws Exception {
        Instant now = Instant.parse("2026-08-20T07:00:00Z");
        userRepository.insert(
                new AtlasUserRecord(
                        "usr_expired_008",
                        "sso-expired-008",
                        "Expired",
                        null,
                        "[\"end_user\"]",
                        false,
                        now,
                        now));
        sessionRepository.insert(
                new AtlasSessionRecord(
                        "sess_expired_008",
                        "usr_expired_008",
                        now.minus(2, ChronoUnit.HOURS),
                        now.minus(2, ChronoUnit.HOURS),
                        now.minus(1, ChronoUnit.HOURS),
                        now.minus(1, ChronoUnit.MINUTES),
                        null,
                        "csrf-expired"));

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .cookie(new Cookie(sessionProperties.getCookieName(), "sess_expired_008")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unconfiguredSsoFailsClosed() {
        UnconfiguredSsoAdapter adapter = new UnconfiguredSsoAdapter();
        assertThatThrownBy(() -> adapter.authorizationUrl("state"))
                .isInstanceOf(SsoNotConfiguredException.class);
    }

    @Test
    void ssoStartSetsStateCookieAndDoesNotSetSession() throws Exception {
        MvcResult start =
                mockMvc.perform(get("/api/v1/auth/sso/start"))
                        .andExpect(status().isFound())
                        .andExpect(header().exists("Location"))
                        .andReturn();
        assertThat(start.getResponse().getCookie(sessionProperties.getCookieName())).isNull();
        assertThat(start.getResponse().getCookie(sessionProperties.ssoStateCookieName())).isNotNull();
    }

    private Cookie completeLocalSso() throws Exception {
        MvcResult start =
                mockMvc.perform(get("/api/v1/auth/sso/start")).andExpect(status().isFound()).andReturn();
        Cookie state = start.getResponse().getCookie(sessionProperties.ssoStateCookieName());
        String location = start.getResponse().getRedirectedUrl();
        MvcResult callback =
                mockMvc.perform(get(location).cookie(state)).andExpect(status().isFound()).andReturn();
        Cookie session = callback.getResponse().getCookie(sessionProperties.getCookieName());
        assertThat(session).isNotNull();
        assertThat(session.getValue()).isNotBlank();
        return session;
    }
}
