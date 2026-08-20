package com.atlas.knowledgebase.providers;

import com.atlas.knowledgebase.audit.AuditEventRecord;
import com.atlas.knowledgebase.audit.AuditEventRepository;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderConnectionService {

    public static final String PENDING_SECRET_REF = "pending:oauth";
    public static final String REVOKED_SECRET_REF = "revoked:none";
    static final String STATE_COOKIE_SUFFIX = "-provider-oauth-state-";
    static final List<String> SETTINGS_PROVIDERS = List.of("github", "confluence");

    /** [ASSUMPTION] Channel name in GET /settings until model-channel adapter (TASK-022). */
    static final String MODEL_CHANNEL = "enterprise_approved";

    private final ProviderConnectionRepository repository;
    private final ProviderProperties properties;
    private final ProviderAuthorizationClient authorizationClient;
    private final SecretStore secretStore;
    private final com.atlas.knowledgebase.session.SessionProperties sessionProperties;
    private final SessionService sessionService;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProviderConnectionService(
            ProviderConnectionRepository repository,
            ProviderProperties properties,
            ProviderAuthorizationClient authorizationClient,
            SecretStore secretStore,
            com.atlas.knowledgebase.session.SessionProperties sessionProperties,
            SessionService sessionService,
            AuditEventRepository auditEvents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.authorizationClient = authorizationClient;
        this.secretStore = secretStore;
        this.sessionProperties = sessionProperties;
        this.sessionService = sessionService;
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public StartConnectResult startConnect(AtlasUserRecord user, String rawProvider) {
        String provider = normalizeProvider(rawProvider);
        repository
                .findByUserAndProvider(user.userId(), provider)
                .ifPresent(
                        existing -> {
                            if (isLiveConnected(existing)) {
                                throw new AlreadyConnectingException(provider);
                            }
                        });
        return beginAuthorization(user, provider);
    }

    @Transactional
    public StartConnectResult startReconnect(AtlasUserRecord user, String rawProvider) {
        String provider = normalizeProvider(rawProvider);
        StartConnectResult started = beginAuthorization(user, provider);
        writeAudit(user.userId(), provider, "reconnect", contentFreeProviderDetails(provider));
        return started;
    }

    @Transactional
    public void revoke(AtlasUserRecord user, String rawProvider) {
        String provider = normalizeProvider(rawProvider);
        Instant now = clock.instant();
        repository
                .findByUserAndProvider(user.userId(), provider)
                .ifPresentOrElse(
                        existing -> {
                            String previousRef = existing.secretRef();
                            repository.update(
                                    new ProviderConnectionRecord(
                                            existing.connectionId(),
                                            existing.userId(),
                                            provider,
                                            "revoked",
                                            "[]",
                                            null,
                                            existing.lastVerifiedAt(),
                                            REVOKED_SECRET_REF,
                                            now));
                            deleteStoredSecret(previousRef);
                        },
                        () ->
                                repository.insert(
                                        new ProviderConnectionRecord(
                                                newConnectionId(),
                                                user.userId(),
                                                provider,
                                                "revoked",
                                                "[]",
                                                null,
                                                null,
                                                REVOKED_SECRET_REF,
                                                now)));
        writeAudit(user.userId(), provider, "revoke", contentFreeProviderDetails(provider));
    }

    /**
     * [ASSUMPTION] Compromise is not in the API guide. {@code POST
     * /api/v1/providers/{provider}/compromise} (Session+CSRF) deletes the provider secret,
     * sets reconnect-required, terminates every Atlas session for the user, and writes a
     * content-free audit event. Shared KB name/Owner rows are not mutated.
     */
    @Transactional
    public ResponseCookie compromise(AtlasUserRecord user, String rawProvider) {
        String provider = normalizeProvider(rawProvider);
        Instant now = clock.instant();
        repository
                .findByUserAndProvider(user.userId(), provider)
                .ifPresentOrElse(
                        existing -> {
                            String previousRef = existing.secretRef();
                            repository.update(
                                    new ProviderConnectionRecord(
                                            existing.connectionId(),
                                            existing.userId(),
                                            provider,
                                            "reconnect_required",
                                            "[]",
                                            null,
                                            existing.lastVerifiedAt(),
                                            REVOKED_SECRET_REF,
                                            now));
                            deleteStoredSecret(previousRef);
                        },
                        () ->
                                repository.insert(
                                        new ProviderConnectionRecord(
                                                newConnectionId(),
                                                user.userId(),
                                                provider,
                                                "reconnect_required",
                                                "[]",
                                                null,
                                                null,
                                                REVOKED_SECRET_REF,
                                                now)));
        sessionService.revokeAllForUser(user.userId());
        writeAudit(user.userId(), provider, "compromise", contentFreeProviderDetails(provider));
        return sessionService.clearedSessionCookie();
    }

    /**
     * GET /settings projection. No writes: an expired {@code expires_at} is projected as
     * {@code expired} without updating the row, so KB name/Owner metadata is untouched.
     */
    public Map<String, Object> settingsProjection(AtlasUserRecord user) {
        Instant now = clock.instant();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("user_id", user.userId());
        identity.put("display_name", user.displayName());

        Map<String, Object> modelChannel = new LinkedHashMap<>();
        modelChannel.put("eligible", user.modelEntitled());
        modelChannel.put("channel", MODEL_CHANNEL);

        List<Map<String, Object>> providers = new ArrayList<>();
        for (String provider : SETTINGS_PROVIDERS) {
            providers.add(projectProvider(user.userId(), provider, now));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("identity", identity);
        body.put("model_channel", modelChannel);
        body.put("providers", providers);
        return body;
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

    public ResponseCookie clearStateCookie(String rawProvider) {
        return stateCookie(normalizeProvider(rawProvider), "");
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

    public String stateCookieName(String rawProvider) {
        return sessionProperties.getCookieName()
                + STATE_COOKIE_SUFFIX
                + normalizeProvider(rawProvider);
    }

    List<String> parseScopes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private StartConnectResult beginAuthorization(AtlasUserRecord user, String provider) {
        List<String> requested = properties.requestedScopes(provider);
        Instant now = clock.instant();
        ProviderConnectionRecord row =
                repository
                        .findByUserAndProvider(user.userId(), provider)
                        .map(
                                existing -> {
                                    String previousRef = existing.secretRef();
                                    ProviderConnectionRecord updated =
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
                                                            now));
                                    deleteStoredSecret(previousRef);
                                    return updated;
                                })
                        .orElseGet(
                                () ->
                                        repository.insert(
                                                new ProviderConnectionRecord(
                                                        newConnectionId(),
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
        return new StartConnectResult(url, state, stateCookie(provider, state), row.connectionId());
    }

    private Map<String, Object> projectProvider(String userId, String provider, Instant now) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("provider", provider);
        repository
                .findByUserAndProvider(userId, provider)
                .ifPresentOrElse(
                        row -> {
                            item.put("status", projectedStatus(row, now));
                            item.put("granted_scopes", parseScopes(row.grantedScopesJson()));
                            item.put(
                                    "expires_at",
                                    row.expiresAt() == null ? null : row.expiresAt().toString());
                            item.put(
                                    "last_verified_at",
                                    row.lastVerifiedAt() == null
                                            ? null
                                            : row.lastVerifiedAt().toString());
                        },
                        () -> {
                            item.put("status", "reconnect_required");
                            item.put("granted_scopes", List.of());
                            item.put("expires_at", null);
                            item.put("last_verified_at", null);
                        });
        return item;
    }

    static String projectedStatus(ProviderConnectionRecord row, Instant now) {
        if ("connected".equals(row.status())
                && row.expiresAt() != null
                && row.expiresAt().isBefore(now)) {
            return "expired";
        }
        return row.status();
    }

    static boolean isLiveConnected(ProviderConnectionRecord existing) {
        return "connected".equals(existing.status()) && !isSentinelSecret(existing.secretRef());
    }

    static boolean isSentinelSecret(String secretRef) {
        return PENDING_SECRET_REF.equals(secretRef) || REVOKED_SECRET_REF.equals(secretRef);
    }

    private void deleteStoredSecret(String secretRef) {
        if (secretRef == null || isSentinelSecret(secretRef)) {
            return;
        }
        secretStore.delete(secretRef);
    }

    private String contentFreeProviderDetails(String provider) {
        try {
            return objectMapper.writeValueAsString(Map.of("provider", provider));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize content-free audit details", e);
        }
    }

    private void writeAudit(String userId, String provider, String action, String detailsJson) {
        auditEvents.insert(
                new AuditEventRecord(
                        "aud_" + SessionService.randomToken().substring(0, 16),
                        clock.instant(),
                        userId,
                        null,
                        null,
                        provider,
                        action,
                        null,
                        null,
                        null,
                        null,
                        "success",
                        null,
                        detailsJson));
    }

    private ResponseCookie stateCookie(String provider, String value) {
        Duration maxAge = value.isEmpty() ? Duration.ZERO : Duration.ofMinutes(10);
        return ResponseCookie.from(stateCookieName(provider), value)
                .httpOnly(true)
                .secure(sessionProperties.isCookieSecure())
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    private static String newConnectionId() {
        return "pcn_" + SessionService.randomToken().substring(0, 16);
    }

    private String toJson(List<String> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize granted_scopes", e);
        }
    }

    public record StartConnectResult(
            String authorizationUrl, String state, ResponseCookie stateCookie, String connectionId) {}
}
