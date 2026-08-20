package com.atlas.knowledgebase.providers;

/** Deployed plane has no provider OAuth client. Mapped to HTTP 503. */
public final class ProviderOauthNotConfiguredException extends RuntimeException {

    public ProviderOauthNotConfiguredException(String message) {
        super(message);
    }
}
