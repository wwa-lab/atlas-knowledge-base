package com.atlas.knowledgebase.providers;

/** A live or in-flight connection already exists. Mapped to HTTP 409. */
public final class AlreadyConnectingException extends RuntimeException {

    public AlreadyConnectingException(String provider) {
        super("Provider " + provider + " is already connecting or connected.");
    }
}
