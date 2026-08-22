package com.atlas.knowledgebase.discovery;

/** Catalog/Browse authorization failure. */
public final class CatalogForbiddenException extends RuntimeException {

    private final String code;
    private final String nextStep;

    public CatalogForbiddenException(String code, String message, String nextStep) {
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
