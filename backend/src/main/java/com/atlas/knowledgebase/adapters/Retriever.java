package com.atlas.knowledgebase.adapters;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Provider retrieval port. Real Dify/Git/Confluence adapters are TASK-019–021; stubs return
 * fixture evidence only.
 */
public interface Retriever {

    Set<String> providerProfiles();

    default boolean supports(String providerProfile) {
        return providerProfiles().contains(providerProfile);
    }

    AuthorizationResult authorize(AuthorizationRequest request);

    Result retrieve(Request request);

    record AuthorizationRequest(
            String requestId,
            String userId,
            String logicalKbId,
            String bindingId,
            String providerProfile,
            String sourceIdentityJson,
            Duration timeout,
            CancellationToken cancellation) {
        public AuthorizationRequest {
            cancellation = cancellation == null ? neverCancelled() : cancellation;
        }
    }

    enum AuthorizationOutcome {
        AUTHORIZED,
        ACCESS_DENIED,
        QUOTA,
        TIMEOUT,
        FAILED,
        SECURITY,
        UNKNOWN
    }

    record AuthorizationResult(AuthorizationOutcome outcome, Duration retryAfter) {
        public AuthorizationResult {
            if (outcome == null) {
                throw new IllegalArgumentException("authorization outcome is required");
            }
            retryAfter = normalizeRetryAfter(outcome == AuthorizationOutcome.QUOTA, retryAfter);
        }

        public static AuthorizationResult authorized() {
            return new AuthorizationResult(AuthorizationOutcome.AUTHORIZED, null);
        }

        public static AuthorizationResult accessDenied() {
            return new AuthorizationResult(AuthorizationOutcome.ACCESS_DENIED, null);
        }

        public static AuthorizationResult quota(Duration retryAfter) {
            return new AuthorizationResult(AuthorizationOutcome.QUOTA, retryAfter);
        }

        public static AuthorizationResult timeout() {
            return new AuthorizationResult(AuthorizationOutcome.TIMEOUT, null);
        }

        public static AuthorizationResult failed() {
            return new AuthorizationResult(AuthorizationOutcome.FAILED, null);
        }

        public static AuthorizationResult security() {
            return new AuthorizationResult(AuthorizationOutcome.SECURITY, null);
        }

        public static AuthorizationResult unknown() {
            return new AuthorizationResult(AuthorizationOutcome.UNKNOWN, null);
        }
    }

    record Request(
            String requestId,
            String question,
            String userId,
            String logicalKbId,
            String bindingId,
            String providerProfile,
            String sourceIdentityJson,
            Duration timeout,
            CancellationToken cancellation) {
        public Request {
            cancellation = cancellation == null ? neverCancelled() : cancellation;
        }
    }

    record Hit(
            String canonicalSourceIdentity,
            String sourceUrl,
            String documentId,
            String title,
            String excerpt,
            String version,
            String locatorJson,
            int rank,
            String fingerprint) {
        public Hit {
            requireIdentity(canonicalSourceIdentity, "canonicalSourceIdentity");
            requireIdentity(sourceUrl, "sourceUrl");
            requireIdentity(version, "version");
            requireIdentity(fingerprint, "fingerprint");
        }

        private static void requireIdentity(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required for evidence identity");
            }
        }
    }

    enum Outcome {
        SUCCESS,
        QUOTA,
        TIMEOUT,
        FAILED,
        SECURITY,
        UNKNOWN
    }

    record Result(
            Outcome outcome, List<Hit> hits, List<Hit> omittedItems, Duration retryAfter) {
        public Result {
            if (outcome == null) {
                throw new IllegalArgumentException("retrieval outcome is required");
            }
            hits = hits == null ? List.of() : List.copyOf(hits);
            omittedItems = omittedItems == null ? List.of() : List.copyOf(omittedItems);
            retryAfter = normalizeRetryAfter(outcome == Outcome.QUOTA, retryAfter);
        }

        public static Result success(List<Hit> hits, List<Hit> omitted) {
            return new Result(Outcome.SUCCESS, List.copyOf(hits), List.copyOf(omitted), null);
        }

        public static Result quota(Duration retryAfter) {
            return new Result(Outcome.QUOTA, List.of(), List.of(), retryAfter);
        }

        public static Result timeout() {
            return new Result(Outcome.TIMEOUT, List.of(), List.of(), null);
        }

        public static Result failed() {
            return new Result(Outcome.FAILED, List.of(), List.of(), null);
        }

        public static Result security() {
            return new Result(Outcome.SECURITY, List.of(), List.of(), null);
        }

        public static Result unknown() {
            return new Result(Outcome.UNKNOWN, List.of(), List.of(), null);
        }
    }

    private static Duration normalizeRetryAfter(boolean required, Duration retryAfter) {
        if (!required) {
            return null;
        }
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            throw new IllegalArgumentException("a positive retryAfter is required for quota outcomes");
        }
        return retryAfter;
    }

    private static CancellationToken neverCancelled() {
        BooleanSupplier cancelled = () -> false;
        return new CancellationToken() {
            @Override
            public boolean isCancelled() {
                return cancelled.getAsBoolean();
            }

            @Override
            public Registration onCancel(Runnable callback) {
                return () -> {};
            }
        };
    }
}
