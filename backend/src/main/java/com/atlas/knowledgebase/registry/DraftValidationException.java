package com.atlas.knowledgebase.registry;

/** Wizard field or multi-source compatibility failure (HTTP 422). */
public final class DraftValidationException extends RuntimeException {

    private final String code;

    public DraftValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
