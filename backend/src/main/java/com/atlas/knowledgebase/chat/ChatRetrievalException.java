package com.atlas.knowledgebase.chat;

import java.util.Map;

public final class ChatRetrievalException extends RuntimeException {

    private final String code;
    private final String category;
    private final String nextStep;
    private final Map<String, Object> details;

    public ChatRetrievalException(
            String category,
            String code,
            String message,
            String nextStep,
            Map<String, Object> details) {
        super(message);
        this.category = category;
        this.code = code;
        this.nextStep = nextStep;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public String category() {
        return category;
    }

    public String nextStep() {
        return nextStep;
    }

    public Map<String, Object> details() {
        return details;
    }
}
