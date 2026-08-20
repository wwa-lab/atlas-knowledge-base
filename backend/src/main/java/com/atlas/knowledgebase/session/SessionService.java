package com.atlas.knowledgebase.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    public static final String REQUEST_SESSION_ATTRIBUTE = "atlas.session";
    public static final String REQUEST_USER_ATTRIBUTE = "atlas.user";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AtlasUserRepository userRepository;
    private final AtlasSessionRepository sessionRepository;
    private final SessionProperties properties;
    private final Clock clock;

    public SessionService(
            AtlasUserRepository userRepository,
            AtlasSessionRepository sessionRepository,
            SessionProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedSession establish(SsoIdentity identity) {
        Instant now = clock.instant();
        AtlasUserRecord user =
                userRepository
                        .findBySsoSubject(identity.subject())
                        .map(
                                existing ->
                                        userRepository.refreshIdentity(
                                                existing.userId(),
                                                identity.displayName(),
                                                identity.email(),
                                                now))
                        .orElseGet(() -> insertUser(identity, now));
        String sessionId = randomToken();
        String csrfSecret = randomToken();
        AtlasSessionRecord session =
                sessionRepository.insert(
                        new AtlasSessionRecord(
                                sessionId,
                                user.userId(),
                                now,
                                now,
                                now.plus(properties.getAbsoluteTtl()),
                                now.plus(properties.getIdleTtl()),
                                null,
                                csrfSecret));
        return new IssuedSession(user, session, sessionCookie(sessionId, properties.getAbsoluteTtl()));
    }

    public Optional<ResolvedSession> resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<AtlasSessionRecord> found = sessionRepository.findById(sessionId);
        if (found.isEmpty() || !found.get().isUsable(now)) {
            return Optional.empty();
        }
        AtlasSessionRecord session = found.get();
        Instant idleExpires = now.plus(properties.getIdleTtl());
        if (idleExpires.isAfter(session.absoluteExpiresAt())) {
            idleExpires = session.absoluteExpiresAt();
        }
        sessionRepository.touchIdle(session.sessionId(), now, idleExpires);
        AtlasSessionRecord touched =
                new AtlasSessionRecord(
                        session.sessionId(),
                        session.userId(),
                        session.issuedAt(),
                        now,
                        session.absoluteExpiresAt(),
                        idleExpires,
                        session.revokedAt(),
                        session.csrfSecret());
        AtlasUserRecord user =
                userRepository
                        .findById(session.userId())
                        .orElseThrow(() -> new UnauthenticatedException("Session user is missing."));
        return Optional.of(new ResolvedSession(user, touched));
    }

    @Transactional
    public ResponseCookie logout(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRepository.revoke(sessionId, clock.instant());
        }
        return sessionCookie("", java.time.Duration.ZERO);
    }

    public void requireCsrf(AtlasSessionRecord session, String presentedToken) {
        if (presentedToken == null || !constantTimeEquals(session.csrfSecret(), presentedToken)) {
            throw new CsrfMismatchException("CSRF token missing or does not match the session.");
        }
    }

    public ResponseCookie ssoStateCookie(String state) {
        return cookie(properties.ssoStateCookieName(), state, java.time.Duration.ofMinutes(10));
    }

    public ResponseCookie clearSsoStateCookie() {
        return cookie(properties.ssoStateCookieName(), "", java.time.Duration.ZERO);
    }

    private AtlasUserRecord insertUser(SsoIdentity identity, Instant now) {
        return userRepository.insert(
                new AtlasUserRecord(
                        deriveUserId(identity.subject()),
                        identity.subject(),
                        identity.displayName(),
                        identity.email(),
                        "[\"end_user\"]",
                        false,
                        now,
                        now));
    }

    public static String deriveUserId(String ssoSubject) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(ssoSubject.getBytes(StandardCharsets.UTF_8));
            return "usr_" + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to derive user_id", e);
        }
    }

    private ResponseCookie sessionCookie(String value, java.time.Duration maxAge) {
        return cookie(properties.getCookieName(), value, maxAge);
    }

    private ResponseCookie cookie(String name, String value, java.time.Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .path("/")
                .sameSite(properties.getCookieSameSite())
                .maxAge(maxAge)
                .build();
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    public record IssuedSession(
            AtlasUserRecord user, AtlasSessionRecord session, ResponseCookie cookie) {}

    public record ResolvedSession(AtlasUserRecord user, AtlasSessionRecord session) {}
}
