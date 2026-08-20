package com.atlas.knowledgebase.providers;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import com.atlas.knowledgebase.session.SessionAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
        Map<String, String> body = new LinkedHashMap<>();
        body.put("authorization_url", started.authorizationUrl());
        body.put("state", started.state());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, started.stateCookie().toString())
                .body(body);
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
                SessionAuthFilter.cookieValue(request, connections.stateCookieName());
        connections.requireMatchingState(expected, state);
        connections.completeCallback(user, provider, code, scope);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/settings")
                .header(HttpHeaders.SET_COOKIE, connections.clearStateCookie().toString())
                .build();
    }
}
