package com.atlas.knowledgebase.providers;

/** Path provider is not {@code github} or {@code confluence}. Mapped to HTTP 400. */
public final class InvalidProviderException extends RuntimeException {

    public InvalidProviderException(String provider) {
        super("Unsupported provider: " + provider);
    }
}
