package com.atlas.knowledgebase.governance;

import java.util.Map;

/** Governance command was stale, replayed, or failed a required safety validation. */
public final class GovernanceConflictException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public GovernanceConflictException(String code, String message) {
        this(code, message, Map.of());
    }

    public GovernanceConflictException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
