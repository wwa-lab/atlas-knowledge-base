package com.atlas.knowledgebase.session;

/** Missing or expired Atlas session. Mapped to HTTP 401. */
public final class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
