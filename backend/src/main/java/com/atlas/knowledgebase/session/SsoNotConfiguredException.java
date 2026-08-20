package com.atlas.knowledgebase.session;

/** Deployed plane has no IdP yet. Mapped to HTTP 503. */
public final class SsoNotConfiguredException extends RuntimeException {

    public SsoNotConfiguredException(String message) {
        super(message);
    }
}
