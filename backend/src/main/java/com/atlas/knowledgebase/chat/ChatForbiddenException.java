package com.atlas.knowledgebase.chat;

public final class ChatForbiddenException extends RuntimeException {

    private final String code;
    private final String nextStep;

    public ChatForbiddenException(String code, String message, String nextStep) {
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
