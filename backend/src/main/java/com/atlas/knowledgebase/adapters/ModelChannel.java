package com.atlas.knowledgebase.adapters;

import java.util.List;
import java.util.function.Consumer;

/**
 * Generation channel. Production uses the per-user local SME gateway (ADR-0007 / TASK-022). Local
 * and non-prod use {@link StubModelChannel}, which must not receive real internal excerpts (FR-78).
 */
public interface ModelChannel {

    void generate(Request request, Listener listener);

    record Request(String requestId, String question, String userId, List<String> evidenceIds) {
        public Request {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }
    }

    interface Listener {
        void onToken(String delta);

        void onComplete(String answer);

        void onCancelled();

        boolean isCancelled();
    }

    default void generate(Request request, Consumer<String> tokens, Runnable done) {
        generate(
                request,
                new Listener() {
                    @Override
                    public void onToken(String delta) {
                        tokens.accept(delta);
                    }

                    @Override
                    public void onComplete(String answer) {
                        done.run();
                    }

                    @Override
                    public void onCancelled() {}

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                });
    }
}
