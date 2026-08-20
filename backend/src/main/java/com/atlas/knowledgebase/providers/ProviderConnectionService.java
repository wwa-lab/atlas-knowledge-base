package com.atlas.knowledgebase.providers;

import com.atlas.knowledgebase.secrets.SecretStore;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderConnectionService {

    static final String PENDING_SECRET_REF = "pending:oauth";
    static final String STATE_COOKIE_SUFFIX = "-provider-oauth-state";

    private final ProviderConnectionRepository repository;
    private final ProviderProperties properties;
    private final ProviderAuthorizationClient authorizationClient;
    private final SecretStore secretStore;
    private final com.atlas.knowledgebase.session.SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProviderConnectionService(
            ProviderConnectionRepository repository,
            ProviderProperties properties,
            ProviderAuthorizationClient authorizationClient,
            SecretStore secretStore,
            com.atlas.knowledgebase.session.SessionProperties sessionProperties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.authorizationClient = authorizationClient;
        this.secretStore = secretStore;
        this.sessionProperties = sessionProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public StartConnectResult startConnect(AtlasUserRecord user, String rawProvider) {
        String provider = normalizeProvider(rawProvider);
        List<String> requested = properties.requestedScopes(provider);
        repository
                .findByUserAndProvider(user.userId(), provider)
                .ifPresent(
                        existing -> {
                            if ("connected".equals(existing.status())
                                    || PENDING_SECRET_REF.equals(existing.secretRef())) {
                                throw new AlreadyConnectingException(provider);
                            }
                        });

        Instant now = clock.instant();
        ProviderConnectionRecord row =
                repository
                        .findByUserAndProvider(user.userId(), provider)
                        .map(
                                existing ->
                                        repository.update(
                                                new ProviderConnectionRecord(
                                                        existing.connectionId(),
                                                        existing.userId(),
                                                        provider,
                                                        "reconnect_required",
                                                        "[]",
                                                        null,
                                                        null,
                                                        PENDING_SECRET_REF,
                                                        now)))
                        .orElseGet(
                                () ->
                                        repository.insert(
                                                new ProviderConnectionRecord(
                                                        "pcn_" + SessionService.randomToken().substring(0, 16),
                                                        user.userId(),
                                                        provider,
                                                        "reconnect_required",
                                                        "[]",
                                                        null,
                                                        null,
                                                        PENDING_SECRET_REF,
                                                        now)));

        String state = SessionService.randomToken();
        String url = authorizationClient.authorizationUrl(provider, state, requested);
        return new StartConnectResult(url, state, stateCookie(state), row.connectionId());
    }

    @Transactional
    public void completeCallback(
            AtlasUserRecord user, String rawProvider, String code, String presentedScopeParam) {
        String provider = normalizeProvider(rawProvider);
        List<String> requested = properties.requestedScopes(provider);
        ProviderConnectionRecord existing =
                repository
                        .findByUserAndProvider(user.userId(), provider)
                        .orElseThrow(() -> new AlreadyConnectingException(provider));
        if (!PENDING_SECRET_REF.equals(existing.secretRef())) {
            throw new AlreadyConnectingException(provider);
        }

        char[] token = authorizationClient.redeemAccessToken(provider, code);
        String secretRef;
        try {
            secretRef = secretStore.store(provider + "-" + user.userId(), token);
        } finally {
            Arrays.fill(token, '\0');
        }

        List<String> granted = intersectScopes(requested, presentedScopeParam);
        Instant now = clock.instant();
        String status = properties.isStubCompletesAsConnected() ? "connected" : "reconnect_required";
        repository.update(
                new ProviderConnectionRecord(
                        existing.connectionId(),
                        existing.userId(),
                        provider,
                        status,
                        toJson(granted),
                        null,
                        now,
                        secretRef,
                        now));
    }

    public void requireMatchingState(String expected, String actual) {
        if (!SessionService.constantTimeEquals(expected, actual)) {
            throw new com.atlas.knowledgebase.session.UnauthenticatedException(
                    "Provider OAuth state mismatch.");
        }
    }

    public ResponseCookie clearStateCookie() {
        return stateCookie("");
    }

    static String normalizeProvider(String rawProvider) {
        if (rawProvider == null) {
            throw new InvalidProviderException("null");
        }
        String provider = rawProvider.trim().toLowerCase(Locale.ROOT);
        if (!"github".equals(provider) && !"confluence".equals(provider)) {
            throw new InvalidProviderException(rawProvider);
        }
        return provider;
    }

    /**
     * Persist only the intersection of requested least-privilege scopes and what the provider
     * returned. Extra scopes are dropped, never stored.
     */
    static List<String> intersectScopes(List<String> requested, String presentedScopeParam) {
        List<String> presented;
        if (presentedScopeParam == null || presentedScopeParam.isBlank()) {
            presented = requested;
        } else {
            String decoded =
                    java.net.URLDecoder.decode(presentedScopeParam, java.nio.charset.StandardCharsets.UTF_8);
            presented = List.of(decoded.trim().split("\\s+"));
        }
        Set<String> allowed = new LinkedHashSet<>(requested);
        List<String> granted = new ArrayList<>();
        for (String scope : presented) {
            if (allowed.contains(scope) && !granted.contains(scope)) {
                granted.add(scope);
            }
        }
        return List.copyOf(granted);
    }

    private ResponseCookie stateCookie(String value) {
        Duration maxAge = value.isEmpty() ? Duration.ZERO : Duration.ofMinutes(10);
        return ResponseCookie.from(stateCookieName(), value)
                .httpOnly(true)
                .secure(sessionProperties.isCookieSecure())
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    public String stateCookieName() {
        return sessionProperties.getCookieName() + STATE_COOKIE_SUFFIX;
    }

    private String toJson(List<String> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize granted_scopes", e);
        }
    }

    List<String> parseScopes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public record StartConnectResult(
            String authorizationUrl, String state, ResponseCookie stateCookie, String connectionId) {}
}
