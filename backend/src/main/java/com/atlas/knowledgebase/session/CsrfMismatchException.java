package com.atlas.knowledgebase.session;

/** CSRF header does not match session material. Mapped to HTTP 403. */
public final class CsrfMismatchException extends RuntimeException {

    public CsrfMismatchException(String message) {
        super(message);
    }
}
