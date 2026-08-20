package com.atlas.knowledgebase.providers;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.atlas.knowledgebase.session.SessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderConnectionService connections;

    public ProviderController(ProviderConnectionService connections) {
        this.connections = connections;
    }

    @PostMapping("/{provider}/connect")
    public ResponseEntity<Map<String, String>> connect(
            @PathVariable("provider") String provider, HttpServletRequest request) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        ProviderConnectionService.StartConnectResult started = connections.startConnect(user, provider);
        return authorizationStart(started);
    }

    @PostMapping("/{provider}/reconnect")
    public ResponseEntity<Map<String, String>> reconnect(
            @PathVariable("provider") String provider, HttpServletRequest request) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        ProviderConnectionService.StartConnectResult started =
                connections.startReconnect(user, provider);
        return authorizationStart(started);
    }

    @PostMapping("/{provider}/revoke")
    public ResponseEntity<Void> revoke(
            @PathVariable("provider") String provider, HttpServletRequest request) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        connections.revoke(user, provider);
        return ResponseEntity.noContent().build();
    }

    /**
     * [ASSUMPTION] Not listed in the API guide. Session+CSRF. Ends every Atlas session for
     * the current user after deleting the provider secret.
     */
    @PostMapping("/{provider}/compromise")
    public ResponseEntity<Void> compromise(
            @PathVariable("provider") String provider, HttpServletRequest request) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        ResponseCookie cleared = connections.compromise(user, provider);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable("provider") String provider,
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            @RequestParam(value = "scope", required = false) String scope,
            HttpServletRequest request) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        String expected =
                SessionAuthFilter.cookieValue(request, connections.stateCookieName(provider));
        connections.requireMatchingState(expected, state);
        connections.completeCallback(user, provider, code, scope);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/settings")
                .header(HttpHeaders.SET_COOKIE, connections.clearStateCookie(provider).toString())
                .build();
    }

    private static ResponseEntity<Map<String, String>> authorizationStart(
            ProviderConnectionService.StartConnectResult started) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("authorization_url", started.authorizationUrl());
        body.put("state", started.state());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, started.stateCookie().toString())
                .body(body);
    }
}
