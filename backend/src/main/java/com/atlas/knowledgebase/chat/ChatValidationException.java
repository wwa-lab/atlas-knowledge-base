package com.atlas.knowledgebase.chat;

public final class ChatValidationException extends RuntimeException {

    private final String code;

    public ChatValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
