package com.atlas.knowledgebase.session;

import jakarta.servlet.http.HttpServletRequest;

/** Public accessor for the session attached by {@link SessionAuthFilter}. */
public final class CurrentRequestAuth {

    private CurrentRequestAuth() {}

    public static AtlasUserRecord requireUser(HttpServletRequest request) {
        Object user = request.getAttribute(SessionService.REQUEST_USER_ATTRIBUTE);
        if (user instanceof AtlasUserRecord record) {
            return record;
        }
        throw new UnauthenticatedException("Sign in with corporate SSO to continue.");
    }

    public static AtlasSessionRecord requireSession(HttpServletRequest request) {
        Object session = request.getAttribute(SessionService.REQUEST_SESSION_ATTRIBUTE);
        if (session instanceof AtlasSessionRecord record) {
            return record;
        }
        throw new UnauthenticatedException("Sign in with corporate SSO to continue.");
    }
}
