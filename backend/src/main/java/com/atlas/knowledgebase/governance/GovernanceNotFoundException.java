package com.atlas.knowledgebase.governance;

/** Requested governance resource or preview does not exist. */
public final class GovernanceNotFoundException extends RuntimeException {

    private final String code;

    public GovernanceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
