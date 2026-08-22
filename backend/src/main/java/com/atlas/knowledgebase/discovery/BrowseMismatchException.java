package com.atlas.knowledgebase.discovery;

/** Browse requested on a knowledge base that is not Git Browse. */
public final class BrowseMismatchException extends RuntimeException {

    private final String code;

    public BrowseMismatchException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
