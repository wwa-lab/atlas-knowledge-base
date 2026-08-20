package com.atlas.knowledgebase.session;

import com.atlas.knowledgebase.web.ApiErrorResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Loads the opaque Atlas session cookie, rejects expired/revoked sessions, and requires CSRF on
 * mutating {@code /api/v1} requests. Provider tokens never enter this filter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionAuthFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final SessionProperties properties;
    private final ObjectMapper objectMapper;

    public SessionAuthFilter(
            SessionService sessionService, SessionProperties properties, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.equals("/api/v1/auth/sso/start")
                || path.equals("/api/v1/auth/sso/callback");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean api = path.startsWith("/api/v1/");
        Optional<SessionService.ResolvedSession> resolved =
                sessionService.resolve(cookieValue(request, properties.getCookieName()));

        if (resolved.isPresent()) {
            request.setAttribute(SessionService.REQUEST_SESSION_ATTRIBUTE, resolved.get().session());
            request.setAttribute(SessionService.REQUEST_USER_ATTRIBUTE, resolved.get().user());
        }

        if (!api) {
            filterChain.doFilter(request, response);
            return;
        }

        if (resolved.isEmpty()) {
            write(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "authentication",
                    "SESSION_REQUIRED",
                    "Sign in with corporate SSO to continue.",
                    "start_sso");
            return;
        }

        if (isMutating(request.getMethod())) {
            try {
                sessionService.requireCsrf(
                        resolved.get().session(), request.getHeader(SessionService.CSRF_HEADER));
            } catch (CsrfMismatchException ex) {
                write(
                        response,
                        HttpServletResponse.SC_FORBIDDEN,
                        "authorization",
                        "CSRF_MISMATCH",
                        "Mutating requests require a CSRF token that matches the session.",
                        "fetch_csrf_and_retry");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void write(
            HttpServletResponse response,
            int status,
            String category,
            String code,
            String message,
            String nextStep)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(), ApiErrorResponses.body(category, code, message, nextStep));
    }

    static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean isMutating(String method) {
        String upper = method.toUpperCase(Locale.ROOT);
        return HttpMethod.POST.matches(upper)
                || HttpMethod.PUT.matches(upper)
                || HttpMethod.PATCH.matches(upper)
                || HttpMethod.DELETE.matches(upper);
    }
}
