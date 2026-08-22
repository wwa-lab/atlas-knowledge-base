package com.atlas.knowledgebase.discovery;

public final class CatalogValidationException extends RuntimeException {

    private final String code;

    public CatalogValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
