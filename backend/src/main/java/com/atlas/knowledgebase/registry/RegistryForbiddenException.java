package com.atlas.knowledgebase.registry;

/** Ordinary user attempted Owner-wizard registration, or a draft owned by someone else. */
public final class RegistryForbiddenException extends RuntimeException {

    private final String code;
    private final String nextStep;

    public RegistryForbiddenException(String code, String message, String nextStep) {
        super(message);
        this.code = code;
        this.nextStep = nextStep;
    }

    public String code() {
        return code;
    }

    public String nextStep() {
        return nextStep;
    }
}
