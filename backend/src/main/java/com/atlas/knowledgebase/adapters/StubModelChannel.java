package com.atlas.knowledgebase.adapters;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * FR-78 local/non-prod mock: streams and can cancel. Does not accept or emit real internal
 * excerpts.
 */
@Component
@Profile({"local", "non-prod"})
public class StubModelChannel implements ModelChannel {

    static final String STUB_ANSWER =
            "Local mock stub: retrieval is not sending real excerpts. Evidence is insufficient for a grounded answer until the retrieval orchestrator and live gateway are in use.";

    @Override
    public void generate(Request request, Listener listener) {
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onCancelled();
            return;
        }
        String[] parts = STUB_ANSWER.split("(?<=\\. )");
        StringBuilder assembled = new StringBuilder();
        for (String part : parts) {
            if (listener.isCancelled()) {
                listener.onCancelled();
                return;
            }
            assembled.append(part);
            listener.onToken(part);
        }
        if (listener.isCancelled()) {
            listener.onCancelled();
            return;
        }
        listener.onComplete(assembled.toString());
    }
}
