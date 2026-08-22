package com.atlas.knowledgebase.evidence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Parses and validates the closed immutable locator schemas defined by ADR-0008. */
@Component
public final class EvidenceLocatorValidator {

    private static final int MAX_JSON_BYTES = 16_384;
    private static final int MAX_PATH_BYTES = 2_048;
    private static final Pattern REPOSITORY =
            Pattern.compile("[A-Za-z0-9._-]{1,100}/[A-Za-z0-9._-]{1,100}");
    private static final Pattern COMMIT = Pattern.compile("[A-Fa-f0-9]{7,64}");
    private static final Pattern INSTANCE = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,256}");
    private static final Pattern URI_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");

    private final ObjectMapper strictMapper;

    public EvidenceLocatorValidator(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.copy();
        this.strictMapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.strictMapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.strictMapper
                .getFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).build());
    }

    public ValidatedLocator validate(String providerProfile, String rawLocatorJson) {
        if (providerProfile == null || rawLocatorJson == null) {
            throw invalid("provider and locator are required");
        }
        if (rawLocatorJson.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw invalid("locator exceeds the JSON size limit");
        }
        JsonNode locator;
        try {
            locator = strictMapper.readTree(rawLocatorJson);
        } catch (Exception exception) {
            throw invalid("locator is not valid bounded JSON", exception);
        }
        requireObject(locator, "locator");
        switch (providerProfile) {
            case "git_markdown" -> validateGit(locator, true);
            case "confluence" -> validateConfluence(locator);
            case "dify" -> validateDify(locator);
            default -> throw invalid("unknown evidence provider");
        }
        boolean fixtureMarked = fixtureMarker(locator);
        Optional<JsonNode> movedTo = Optional.ofNullable(locator.path("move_mapping").path("moved_to_locator"))
                .filter(node -> !node.isMissingNode())
                .map(JsonNode::deepCopy);
        return new ValidatedLocator(providerProfile, locator, fixtureMarked, movedTo);
    }

    /**
     * Defensively validates a move target returned by an adapter against the frozen source
     * locator. Adapter assertions are not sufficient to cross the provider-neutral boundary.
     */
    public ValidatedLocator validateMoveTarget(ValidatedLocator source, JsonNode target) {
        if (source == null || target == null || !target.isObject() || target.has("move_mapping")) {
            throw invalid("move target must be a locator object without a nested move mapping");
        }
        final ValidatedLocator validated;
        try {
            validated = validate(source.providerProfile(), strictMapper.writeValueAsString(target));
        } catch (InvalidLocatorException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("move target cannot be validated", exception);
        }
        if (validated.fixtureMarked() != source.fixtureMarked()
                || validated.movedToLocator().isPresent()
                || !sameStableIdentity(source, validated)) {
            throw invalid("move target does not preserve the frozen source identity boundary");
        }
        return validated;
    }

    private static boolean sameStableIdentity(
            ValidatedLocator source, ValidatedLocator target) {
        JsonNode left = source.locator();
        JsonNode right = target.locator();
        return switch (source.providerProfile()) {
            case "git_markdown" -> sameRequiredText(left, right, "stable_source_id");
            case "confluence" ->
                    sameRequiredText(left, right, "instance")
                            && sameRequiredText(left, right, "page_id");
            case "dify" ->
                    sameRequiredText(
                            left.path("original_version"),
                            right.path("original_version"),
                            "source_id");
            default -> false;
        };
    }

    private static boolean sameRequiredText(JsonNode left, JsonNode right, String field) {
        JsonNode leftValue = left.get(field);
        JsonNode rightValue = right.get(field);
        return leftValue != null
                && rightValue != null
                && leftValue.isTextual()
                && rightValue.isTextual()
                && !leftValue.textValue().isBlank()
                && leftValue.textValue().equals(rightValue.textValue());
    }

    private void validateGit(JsonNode locator, boolean allowMoveMapping) {
        Set<String> allowed = new HashSet<>(
                Set.of("repository", "commit_sha", "path", "line_range", "stable_source_id", "atlas_fixture"));
        if (allowMoveMapping) {
            allowed.add("move_mapping");
        }
        requireExactFields(locator, allowed, Set.of("repository", "commit_sha", "path", "line_range"));
        String repository = requireText(locator, "repository", REPOSITORY);
        String[] repositoryParts = repository.split("/", -1);
        if (".".equals(repositoryParts[0])
                || "..".equals(repositoryParts[0])
                || ".".equals(repositoryParts[1])
                || "..".equals(repositoryParts[1])) {
            throw invalid("repository components cannot be dot segments");
        }
        requireText(locator, "commit_sha", COMMIT);
        validatePath(requireText(locator, "path", null));
        validateLineRange(locator.path("line_range"));
        optionalIdentifier(locator, "stable_source_id");
        fixtureMarker(locator);

        JsonNode mapping = locator.get("move_mapping");
        if (mapping == null) {
            return;
        }
        if (!allowMoveMapping) {
            throw invalid("nested move mappings are forbidden");
        }
        requireObject(mapping, "move_mapping");
        requireExactFields(mapping, Set.of("moved_to_locator"), Set.of("moved_to_locator"));
        JsonNode target = mapping.path("moved_to_locator");
        requireObject(target, "moved_to_locator");
        validateGit(target, false);
        String sourceId = optionalIdentifier(locator, "stable_source_id");
        String targetSourceId = optionalIdentifier(target, "stable_source_id");
        if (sourceId == null || !sourceId.equals(targetSourceId)) {
            throw invalid("move mapping must preserve the same stable source identity");
        }
        if (fixtureMarker(locator) != fixtureMarker(target)) {
            throw invalid("move mapping fixture markers must match");
        }
    }

    private void validateConfluence(JsonNode locator) {
        requireExactFields(
                locator,
                Set.of(
                        "instance",
                        "page_id",
                        "page_version",
                        "attachment_id",
                        "attachment_version",
                        "atlas_fixture"),
                Set.of("instance", "page_id", "page_version"));
        requireText(locator, "instance", INSTANCE);
        requireText(locator, "page_id", IDENTIFIER);
        requirePositiveInt(locator, "page_version");
        boolean attachmentId = locator.has("attachment_id");
        boolean attachmentVersion = locator.has("attachment_version");
        if (attachmentId != attachmentVersion) {
            throw invalid("attachment id and version must be supplied together");
        }
        if (attachmentId) {
            requireText(locator, "attachment_id", IDENTIFIER);
            requirePositiveInt(locator, "attachment_version");
        }
        fixtureMarker(locator);
    }

    private void validateDify(JsonNode locator) {
        requireExactFields(
                locator,
                Set.of("dataset_id", "document_id", "chunk_id", "original_version", "atlas_fixture"),
                Set.of("dataset_id", "document_id", "chunk_id", "original_version"));
        requireText(locator, "dataset_id", IDENTIFIER);
        requireText(locator, "document_id", IDENTIFIER);
        requireText(locator, "chunk_id", IDENTIFIER);
        JsonNode originalVersion = locator.path("original_version");
        requireObject(originalVersion, "original_version");
        requireExactFields(originalVersion, Set.of("source_id", "version"), Set.of("source_id", "version"));
        requireText(originalVersion, "source_id", IDENTIFIER);
        requireText(originalVersion, "version", IDENTIFIER);
        fixtureMarker(locator);
    }

    private static void validatePath(String path) {
        if (!Normalizer.isNormalized(path, Normalizer.Form.NFC)
                || path.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES
                || path.startsWith("/")
                || path.indexOf('\\') >= 0
                || path.indexOf('?') >= 0
                || path.indexOf('#') >= 0
                || URI_SCHEME.matcher(path).matches()) {
            throw invalid("unsafe Git path");
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw invalid("Git path contains an invalid segment");
            }
            if (segment.codePoints().anyMatch(Character::isISOControl)) {
                throw invalid("Git path contains a control character");
            }
        }
    }

    private static void validateLineRange(JsonNode range) {
        if (!range.isArray() || range.size() != 2) {
            throw invalid("line_range must contain exactly two integers");
        }
        int start = positiveInt(range.get(0), "line_range start");
        int end = positiveInt(range.get(1), "line_range end");
        if (start > end) {
            throw invalid("line_range start must not exceed end");
        }
    }

    private static String requireText(JsonNode object, String field, Pattern pattern) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be a non-blank string");
        }
        String text = value.textValue();
        if (pattern != null && !pattern.matcher(text).matches()) {
            throw invalid(field + " has an invalid format");
        }
        return text;
    }

    private static String optionalIdentifier(JsonNode object, String field) {
        if (!object.has(field)) {
            return null;
        }
        return requireText(object, field, IDENTIFIER);
    }

    private static void requirePositiveInt(JsonNode object, String field) {
        positiveInt(object.get(field), field);
    }

    private static int positiveInt(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw invalid(field + " must be an integer in 1..2147483647");
        }
        return value.intValue();
    }

    private static boolean fixtureMarker(JsonNode object) {
        JsonNode marker = object.get("atlas_fixture");
        if (marker == null) {
            return false;
        }
        if (!marker.isBoolean() || !marker.booleanValue()) {
            throw invalid("atlas_fixture may only be the literal true");
        }
        return true;
    }

    private static void requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            throw invalid(field + " must be an object");
        }
    }

    private static void requireExactFields(JsonNode object, Set<String> allowed, Set<String> required) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = object.fieldNames();
        names.forEachRemaining(actual::add);
        if (!allowed.containsAll(actual) || !actual.containsAll(required)) {
            throw invalid("locator fields do not match the closed schema");
        }
    }

    private static InvalidLocatorException invalid(String message) {
        return new InvalidLocatorException(message);
    }

    private static InvalidLocatorException invalid(String message, Throwable cause) {
        return new InvalidLocatorException(message, cause);
    }

    public record ValidatedLocator(
            String providerProfile,
            JsonNode locator,
            boolean fixtureMarked,
            Optional<JsonNode> movedToLocator) {
        public ValidatedLocator {
            if (providerProfile == null || locator == null) {
                throw new IllegalArgumentException("provider and locator are required");
            }
            locator = locator.deepCopy();
            movedToLocator = movedToLocator == null
                    ? Optional.empty()
                    : movedToLocator.map(JsonNode::deepCopy);
        }

        @Override
        public JsonNode locator() {
            return locator.deepCopy();
        }

        @Override
        public Optional<JsonNode> movedToLocator() {
            return movedToLocator.map(JsonNode::deepCopy);
        }
    }

    public static final class InvalidLocatorException extends RuntimeException {
        public InvalidLocatorException(String message) {
            super(message);
        }

        public InvalidLocatorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
