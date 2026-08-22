package com.atlas.knowledgebase.adapters;

import com.atlas.knowledgebase.evidence.EvidenceLocatorValidator;
import com.atlas.knowledgebase.evidence.EvidenceNavigationPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.Set;

/** Provider boundary for current-user authorization and exact immutable evidence resolution. */
public interface EvidenceResolver {

    Set<String> providerProfiles();

    default boolean supports(String providerProfile) {
        return providerProfiles().contains(providerProfile);
    }

    AuthorizationResult authorize(AuthorizationRequest request);

    Result resolve(Request request);

    record AuthorizationRequest(
            String providerProfile,
            EvidenceLocatorValidator.ValidatedLocator locator,
            JsonNode authoritativeSourceIdentity,
            AuthorizationContext authorizationContext) {
        public AuthorizationRequest {
            requireRequest(providerProfile, locator, authoritativeSourceIdentity);
            if (authorizationContext == null) {
                throw new IllegalArgumentException("authorization context is required");
            }
            authoritativeSourceIdentity = authoritativeSourceIdentity.deepCopy();
        }

        @Override
        public JsonNode authoritativeSourceIdentity() {
            return authoritativeSourceIdentity.deepCopy();
        }
    }

    /** User-scoped adapter context. Raw provider credentials never cross this port. */
    record AuthorizationContext(String userId, String bindingId, String authMethod) {
        public AuthorizationContext {
            requireText(userId, "user id");
            requireText(bindingId, "binding id");
            requireText(authMethod, "authorization method");
        }
    }

    enum AuthorizationOutcome {
        AUTHORIZED,
        ACCESS_DENIED,
        UNKNOWN
    }

    record AuthorizationResult(AuthorizationOutcome outcome) {
        public AuthorizationResult {
            if (outcome == null) {
                throw new IllegalArgumentException("authorization outcome is required");
            }
        }

        public static AuthorizationResult authorized() {
            return new AuthorizationResult(AuthorizationOutcome.AUTHORIZED);
        }

        public static AuthorizationResult accessDenied() {
            return new AuthorizationResult(AuthorizationOutcome.ACCESS_DENIED);
        }

        public static AuthorizationResult unknown() {
            return new AuthorizationResult(AuthorizationOutcome.UNKNOWN);
        }
    }

    record Request(
            String providerProfile,
            EvidenceLocatorValidator.ValidatedLocator locator,
            JsonNode authoritativeSourceIdentity,
            AuthorizationContext authorizationContext,
            Operation operation) {
        public Request {
            requireRequest(providerProfile, locator, authoritativeSourceIdentity);
            if (authorizationContext == null) {
                throw new IllegalArgumentException("authorization context is required");
            }
            if (operation == null) {
                throw new IllegalArgumentException("evidence operation is required");
            }
            authoritativeSourceIdentity = authoritativeSourceIdentity.deepCopy();
        }

        @Override
        public JsonNode authoritativeSourceIdentity() {
            return authoritativeSourceIdentity.deepCopy();
        }
    }

    enum Operation {
        INSPECT,
        OPEN
    }

    enum Status {
        OK,
        MOVED,
        UNAVAILABLE,
        UNKNOWN;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum VerificationMode {
        FIXTURE,
        PROVIDER,
        NONE;

        public String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    record Result(
            Status status,
            VerificationMode verificationMode,
            boolean providerVerified,
            String navigationUrl,
            String trustedOrigin,
            Optional<JsonNode> movedToLocator) {
        public Result {
            if (status == null || verificationMode == null) {
                throw new IllegalArgumentException("status and verification mode are required");
            }
            movedToLocator = movedToLocator == null
                    ? Optional.empty()
                    : movedToLocator.map(JsonNode::deepCopy);
            if (providerVerified && verificationMode != VerificationMode.PROVIDER) {
                throw new IllegalArgumentException("only provider verification can be provider-verified");
            }
            if (status != Status.UNKNOWN) {
                boolean verifiedProvider =
                        verificationMode == VerificationMode.PROVIDER && providerVerified;
                boolean syntheticFixture =
                        verificationMode == VerificationMode.FIXTURE && !providerVerified;
                if (!verifiedProvider && !syntheticFixture) {
                    throw new IllegalArgumentException(
                            "resolved outcomes require provider verification or fixture semantics");
                }
            }
            if (navigationUrl != null && (status != Status.OK || navigationUrl.isBlank())) {
                throw new IllegalArgumentException("navigation is permitted only for an ok outcome");
            }
            if (trustedOrigin != null && (status != Status.OK || trustedOrigin.isBlank())) {
                throw new IllegalArgumentException("a trusted origin is permitted only for an ok outcome");
            }
            if (status == Status.OK && verificationMode == VerificationMode.PROVIDER) {
                if (!providerVerified || trustedOrigin == null || trustedOrigin.isBlank()) {
                    throw new IllegalArgumentException(
                            "provider ok requires provider verification and a trusted origin");
                }
            }
            if (status == Status.OK && verificationMode == VerificationMode.FIXTURE
                    && !EvidenceNavigationPolicy.FIXTURE_ORIGIN.equals(trustedOrigin)) {
                throw new IllegalArgumentException("fixture ok requires the fixed fixture origin");
            }
            if (status == Status.UNKNOWN && (navigationUrl != null || trustedOrigin != null)) {
                throw new IllegalArgumentException("unknown evidence cannot carry navigation authority");
            }
            if (movedToLocator.isPresent() && status != Status.MOVED) {
                throw new IllegalArgumentException("a move target is permitted only for a moved outcome");
            }
            if (status == Status.MOVED && movedToLocator.isEmpty()) {
                throw new IllegalArgumentException("a moved outcome requires a verified move target");
            }
        }

        public static Result fixtureOk(String navigationUrl) {
            return new Result(
                    Status.OK,
                    VerificationMode.FIXTURE,
                    false,
                    navigationUrl,
                    EvidenceNavigationPolicy.FIXTURE_ORIGIN,
                    Optional.empty());
        }

        public static Result fixtureMoved(JsonNode movedToLocator) {
            if (movedToLocator == null) {
                throw new IllegalArgumentException("fixture moved requires a move target");
            }
            return new Result(
                    Status.MOVED,
                    VerificationMode.FIXTURE,
                    false,
                    null,
                    null,
                    Optional.of(movedToLocator));
        }

        public static Result fixtureUnavailable() {
            return new Result(
                    Status.UNAVAILABLE,
                    VerificationMode.FIXTURE,
                    false,
                    null,
                    null,
                    Optional.empty());
        }

        public static Result unknown(VerificationMode mode) {
            if (mode == null) {
                throw new IllegalArgumentException("verification mode is required");
            }
            return new Result(Status.UNKNOWN, mode, false, null, null, Optional.empty());
        }
    }

    private static void requireRequest(
            String providerProfile,
            EvidenceLocatorValidator.ValidatedLocator locator,
            JsonNode authoritativeSourceIdentity) {
        if (providerProfile == null || providerProfile.isBlank()) {
            throw new IllegalArgumentException("provider profile is required");
        }
        if (locator == null || authoritativeSourceIdentity == null) {
            throw new IllegalArgumentException("locator and authoritative source identity are required");
        }
        if (!providerProfile.equals(locator.providerProfile())) {
            throw new IllegalArgumentException("provider profile and locator do not match");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
