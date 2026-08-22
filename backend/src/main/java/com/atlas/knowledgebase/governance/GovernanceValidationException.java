package com.atlas.knowledgebase.governance;

/** Client supplied governance command cannot be applied safely. */
public final class GovernanceValidationException extends RuntimeException {

    private final String code;

    public GovernanceValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
