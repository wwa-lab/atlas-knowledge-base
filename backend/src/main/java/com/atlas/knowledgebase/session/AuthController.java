package com.atlas.knowledgebase.session;

import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SsoAdapter ssoAdapter;
    private final SessionService sessionService;
    private final SessionProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditEventRepository auditEvents;
    private final Clock clock;

    public AuthController(
            SsoAdapter ssoAdapter,
            SessionService sessionService,
            SessionProperties properties,
            ObjectMapper objectMapper,
            AuditEventRepository auditEvents,
            Clock clock) {
        this.ssoAdapter = ssoAdapter;
        this.sessionService = sessionService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditEvents = auditEvents;
        this.clock = clock;
    }

    @GetMapping("/sso/start")
    public ResponseEntity<Void> startSso() {
        String state = SessionService.randomToken();
        ResponseCookie stateCookie = sessionService.ssoStateCookie(state);
        audit(null, null, "sso_start", "unknown", "redirected", "authentication");
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, ssoAdapter.authorizationUrl(state))
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .build();
    }

    @GetMapping("/sso/callback")
    public ResponseEntity<Void> ssoCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletRequest request) {
        String expectedState =
                SessionAuthFilter.cookieValue(request, properties.ssoStateCookieName());
        if (!SessionService.constantTimeEquals(expectedState, state)) {
            audit(null, null, "sso_callback", "deny", "state_mismatch", "authentication");
            throw new UnauthenticatedException("SSO state mismatch.");
        }
        SsoIdentity identity = ssoAdapter.redeem(code);
        SessionService.IssuedSession issued = sessionService.establish(identity);
        audit(issued.user().userId(), null, "sign_in", "allow", "success", null);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/")
                .header(HttpHeaders.SET_COOKIE, issued.cookie().toString())
                .header(HttpHeaders.SET_COOKIE, sessionService.clearSsoStateCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        ResolvedAuth auth = requireAuth(request);
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("issued_at", auth.session().issuedAt().toString());
        session.put("idle_expires_at", auth.session().idleExpiresAt().toString());
        session.put("absolute_expires_at", auth.session().absoluteExpiresAt().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", auth.user().userId());
        body.put("display_name", auth.user().displayName());
        body.put("email", auth.user().email());
        body.put("roles", parseRoles(auth.user().rolesJson()));
        body.put("model_entitled", auth.user().modelEntitled());
        body.put("session", session);
        return body;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        ResolvedAuth auth = requireAuth(request);
        return Map.of("csrf_token", auth.session().csrfSecret());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        ResolvedAuth auth = requireAuth(request);
        ResponseCookie cleared = sessionService.logout(auth.session().sessionId());
        audit(auth.user().userId(), null, "sign_out", "allow", "success", null);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    private void audit(
            String userId,
            String connector,
            String action,
            String authorization,
            String status,
            String errorCategory) {
        auditEvents.insert(
                new AuditEventRecord(
                        "aud_" + SessionService.randomToken().substring(0, 16),
                        clock.instant(),
                        userId,
                        null,
                        null,
                        connector,
                        action,
                        authorization,
                        null,
                        null,
                        null,
                        status,
                        errorCategory,
                        null));
    }

    private ResolvedAuth requireAuth(HttpServletRequest request) {
        AtlasSessionRecord session =
                (AtlasSessionRecord) request.getAttribute(SessionService.REQUEST_SESSION_ATTRIBUTE);
        AtlasUserRecord user =
                (AtlasUserRecord) request.getAttribute(SessionService.REQUEST_USER_ATTRIBUTE);
        if (session == null || user == null) {
            throw new UnauthenticatedException("Sign in with corporate SSO to continue.");
        }
        return new ResolvedAuth(user, session);
    }

    private List<String> parseRoles(String rolesJson) {
        try {
            return objectMapper.readValue(rolesJson, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            return List.of("end_user");
        }
    }

    private record ResolvedAuth(AtlasUserRecord user, AtlasSessionRecord session) {}
}
