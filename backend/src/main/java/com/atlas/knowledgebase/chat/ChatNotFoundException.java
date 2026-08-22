package com.atlas.knowledgebase.chat;

public final class ChatNotFoundException extends RuntimeException {

    public ChatNotFoundException(String threadId) {
        super("Chat thread not found: " + threadId);
    }
}
