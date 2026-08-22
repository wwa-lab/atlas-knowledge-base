package com.atlas.knowledgebase.adapters;

import com.atlas.knowledgebase.evidence.EvidenceNavigationPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Synthetic exact-evidence resolver. It is deliberately unavailable outside local/test. */
@Component
@Profile({"local", "test"})
public final class StubEvidenceResolver implements EvidenceResolver {

    private static final Set<String> PROVIDERS = Set.of("dify", "git_markdown", "confluence");
    private final EvidenceNavigationPolicy navigationPolicy;

    public StubEvidenceResolver(EvidenceNavigationPolicy navigationPolicy) {
        this.navigationPolicy = navigationPolicy;
    }

    @Override
    public Set<String> providerProfiles() {
        return PROVIDERS;
    }

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request) {
        if (!fixtureBoundaryValid(request.locator(), request.authoritativeSourceIdentity())) {
            return AuthorizationResult.unknown();
        }
        String configured = text(request.authoritativeSourceIdentity(), "evidence_authorization_fixture");
        if (configured == null) {
            return AuthorizationResult.authorized();
        }
        return switch (configured.toLowerCase(Locale.ROOT)) {
            case "authorized" -> AuthorizationResult.authorized();
            case "denied" -> AuthorizationResult.accessDenied();
            default -> AuthorizationResult.unknown();
        };
    }

    @Override
    public Result resolve(Request request) {
        if (!fixtureBoundaryValid(request.locator(), request.authoritativeSourceIdentity())) {
            return Result.unknown(VerificationMode.NONE);
        }
        String configured = text(request.authoritativeSourceIdentity(), "evidence_resolution_fixture");
        if (configured == null) {
            return Result.unknown(VerificationMode.FIXTURE);
        }
        return switch (configured.toLowerCase(Locale.ROOT)) {
            case "ok" -> ok(request);
            case "moved" -> request.locator()
                    .movedToLocator()
                    .map(Result::fixtureMoved)
                    .orElseGet(() -> Result.unknown(VerificationMode.FIXTURE));
            case "unavailable" -> Result.fixtureUnavailable();
            default -> Result.unknown(VerificationMode.FIXTURE);
        };
    }

    private Result ok(Request request) {
        if (request.operation() == Operation.INSPECT) {
            return Result.fixtureOk(null);
        }
        URI navigation = navigationPolicy.fixtureNavigation(request.locator());
        return Result.fixtureOk(navigation.toString());
    }

    private static boolean fixtureBoundaryValid(
            com.atlas.knowledgebase.evidence.EvidenceLocatorValidator.ValidatedLocator locator,
            JsonNode authoritativeSourceIdentity) {
        JsonNode marker = authoritativeSourceIdentity.get("atlas_fixture");
        return locator.fixtureMarked()
                && marker != null
                && marker.isBoolean()
                && marker.booleanValue();
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue()
                : null;
    }
}
