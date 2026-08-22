package com.atlas.knowledgebase.adapters;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Production generation refuses until a live gateway registration exists (FR-76 / TASK-022). */
@Component
@Profile("prod")
public class UnregisteredModelChannel implements ModelChannel {

    @Override
    public void generate(Request request, Listener listener) {
        throw new IllegalStateException("GATEWAY_OFFLINE");
    }
}
