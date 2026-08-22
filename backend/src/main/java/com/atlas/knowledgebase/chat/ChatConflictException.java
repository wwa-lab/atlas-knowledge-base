package com.atlas.knowledgebase.chat;

public final class ChatConflictException extends RuntimeException {

    private final String code;

    public ChatConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
