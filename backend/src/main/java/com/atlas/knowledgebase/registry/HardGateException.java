package com.atlas.knowledgebase.registry;

import java.util.Map;

/** Activation or hard-gate failure (HTTP 409). Knowledge base remains Draft. */
public final class HardGateException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public HardGateException(String code, String message, Map<String, Object> details) {
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
