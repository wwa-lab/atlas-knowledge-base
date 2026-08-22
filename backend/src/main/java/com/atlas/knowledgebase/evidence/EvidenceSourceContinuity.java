package com.atlas.knowledgebase.evidence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Verifies a citation locator against the current authoritative binding identity. */
@Component
public final class EvidenceSourceContinuity {

    private static final int MAX_SOURCE_IDENTITY_BYTES = 65_536;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,256}");
    private final ObjectMapper strictMapper;

    public EvidenceSourceContinuity(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.copy();
        this.strictMapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.strictMapper
                .getFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).build());
    }

    public Check check(
            EvidenceLocatorValidator.ValidatedLocator locator, String authoritativeSourceIdentityJson) {
        if (locator == null
                || authoritativeSourceIdentityJson == null
                || authoritativeSourceIdentityJson.getBytes(StandardCharsets.UTF_8).length
                        > MAX_SOURCE_IDENTITY_BYTES) {
            return Check.failed("source_identity_invalid");
        }
        JsonNode identity;
        try {
            identity = strictMapper.readTree(authoritativeSourceIdentityJson);
        } catch (Exception exception) {
            return Check.failed("source_identity_invalid");
        }
        return check(locator, identity);
    }

    public Check check(
            EvidenceLocatorValidator.ValidatedLocator locator, JsonNode authoritativeSourceIdentity) {
        if (locator == null || authoritativeSourceIdentity == null || !authoritativeSourceIdentity.isObject()) {
            return Check.failed("source_identity_invalid");
        }
        Boolean identityFixture = literalFixtureMarker(authoritativeSourceIdentity);
        if (identityFixture == null || locator.fixtureMarked() != identityFixture) {
            return Check.failed("fixture_marker_mismatch");
        }
        JsonNode source = authoritativeSourceIdentity;
        JsonNode evidence = locator.locator();
        boolean continuous = switch (locator.providerProfile()) {
            case "git_markdown" -> sameText(evidence, "repository", source, "repo");
            case "dify" -> sameText(evidence, "dataset_id", source, "dataset_id");
            case "confluence" -> sameText(evidence, "instance", source, "instance")
                    && validIdentifier(source.get("space_id"))
                    && (!source.has("page_root_id") || validIdentifier(source.get("page_root_id")));
            default -> false;
        };
        return continuous
                ? Check.continuous(locator.fixtureMarked())
                : Check.failed("source_identity_mismatch");
    }

    private static Boolean literalFixtureMarker(JsonNode identity) {
        JsonNode marker = identity.get("atlas_fixture");
        if (marker == null) {
            return false;
        }
        return marker.isBoolean() && marker.booleanValue() ? true : null;
    }

    private static boolean sameText(JsonNode left, String leftField, JsonNode right, String rightField) {
        JsonNode leftValue = left.get(leftField);
        JsonNode rightValue = right.get(rightField);
        return leftValue != null
                && leftValue.isTextual()
                && rightValue != null
                && rightValue.isTextual()
                && !rightValue.textValue().isBlank()
                && leftValue.textValue().equals(rightValue.textValue());
    }

    private static boolean validIdentifier(JsonNode value) {
        return value != null && value.isTextual() && IDENTIFIER.matcher(value.textValue()).matches();
    }

    public record Check(boolean continuous, boolean fixtureDispatchAllowed, String failureReason) {
        public static Check continuous(boolean fixtureDispatchAllowed) {
            return new Check(true, fixtureDispatchAllowed, null);
        }

        public static Check failed(String reason) {
            return new Check(false, false, reason);
        }
    }
}
