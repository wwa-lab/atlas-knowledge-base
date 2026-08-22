package com.atlas.knowledgebase.adapters;

import java.time.Duration;
import java.util.List;

/**
 * Provider retrieval port. Real Dify/Git/Confluence adapters are TASK-019–021; stubs return
 * fixture evidence only.
 */
public interface Retriever {

    boolean supports(String providerProfile);

    Result retrieve(Request request);

    record Request(
            String requestId,
            String question,
            String userId,
            String logicalKbId,
            String bindingId,
            String providerProfile,
            String sourceIdentityJson,
            Duration timeout) {}

    record Hit(
            String documentId,
            String title,
            String excerpt,
            String version,
            String locatorJson,
            int rank,
            String fingerprint) {}

    enum Outcome {
        SUCCESS,
        TIMEOUT,
        FAILED,
        SECURITY
    }

    record Result(Outcome outcome, List<Hit> hits, List<Hit> omittedItems) {
        public static Result success(List<Hit> hits, List<Hit> omitted) {
            return new Result(Outcome.SUCCESS, List.copyOf(hits), List.copyOf(omitted));
        }

        public static Result timeout() {
            return new Result(Outcome.TIMEOUT, List.of(), List.of());
        }

        public static Result failed() {
            return new Result(Outcome.FAILED, List.of(), List.of());
        }

        public static Result security() {
            return new Result(Outcome.SECURITY, List.of(), List.of());
        }
    }
}
