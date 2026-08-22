package com.atlas.knowledgebase.chat;

import java.util.Map;

public final class ChatForbiddenException extends RuntimeException {

    private final String code;
    private final String nextStep;
    private final Map<String, Object> details;

    public ChatForbiddenException(String code, String message, String nextStep) {
        this(code, message, nextStep, Map.of());
    }

    public ChatForbiddenException(
            String code, String message, String nextStep, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.nextStep = nextStep;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public String nextStep() {
        return nextStep;
    }

    public Map<String, Object> details() {
        return details;
    }
}
