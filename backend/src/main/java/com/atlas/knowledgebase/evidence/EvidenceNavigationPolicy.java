package com.atlas.knowledgebase.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Central allow-list policy for provider and synthetic fixture navigation. */
@Component
public final class EvidenceNavigationPolicy {

    public static final String FIXTURE_ORIGIN = "https://evidence-fixture.invalid";

    public URI requireTrustedProviderNavigation(String navigationUrl, String configuredOrigin) {
        URI trusted = parse(configuredOrigin, "trusted origin");
        if (!isHttpsOrigin(trusted)) {
            throw new IllegalArgumentException("trusted provider origin must be an HTTPS origin");
        }
        URI navigation = parse(navigationUrl, "navigation URL");
        if (!"https".equalsIgnoreCase(navigation.getScheme())
                || navigation.getUserInfo() != null
                || navigation.getHost() == null
                || !sameOrigin(navigation, trusted)) {
            throw new IllegalArgumentException("navigation URL is outside the trusted HTTPS origin");
        }
        return navigation;
    }

    public URI requireFixtureNavigation(String navigationUrl) {
        return requireTrustedProviderNavigation(navigationUrl, FIXTURE_ORIGIN);
    }

    public URI fixtureNavigation(EvidenceLocatorValidator.ValidatedLocator locator) {
        if (locator == null || !locator.fixtureMarked()) {
            throw new IllegalArgumentException("fixture navigation requires a validated fixture locator");
        }
        JsonNode value = locator.locator();
        List<String> parts = new ArrayList<>();
        parts.add(locator.providerProfile());
        switch (locator.providerProfile()) {
            case "git_markdown" -> {
                parts.add(value.path("repository").asText());
                parts.add(value.path("commit_sha").asText());
                for (String segment : value.path("path").asText().split("/")) {
                    parts.add(segment);
                }
                parts.add("L" + value.path("line_range").get(0).asInt()
                        + "-L" + value.path("line_range").get(1).asInt());
            }
            case "confluence" -> {
                parts.add(value.path("instance").asText());
                parts.add(value.path("page_id").asText());
                parts.add(Integer.toString(value.path("page_version").asInt()));
                if (value.has("attachment_id")) {
                    parts.add(value.path("attachment_id").asText());
                    parts.add(Integer.toString(value.path("attachment_version").asInt()));
                }
            }
            case "dify" -> {
                parts.add(value.path("dataset_id").asText());
                parts.add(value.path("document_id").asText());
                parts.add(value.path("chunk_id").asText());
                parts.add(value.path("original_version").path("source_id").asText());
                parts.add(value.path("original_version").path("version").asText());
            }
            default -> throw new IllegalArgumentException("unknown evidence provider");
        }
        String path = parts.stream().map(EvidenceNavigationPolicy::encodeComponent).reduce("", (a, b) -> a + "/" + b);
        return requireFixtureNavigation(FIXTURE_ORIGIN + path);
    }

    private static URI parse(String value, String label) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(label + " is required");
            }
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " is invalid", exception);
        }
    }

    private static boolean isHttpsOrigin(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getHost().equalsIgnoreCase(right.getHost()) && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }

    private static String encodeComponent(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte raw : bytes) {
            int valueByte = raw & 0xff;
            if ((valueByte >= 'a' && valueByte <= 'z')
                    || (valueByte >= 'A' && valueByte <= 'Z')
                    || (valueByte >= '0' && valueByte <= '9')
                    || valueByte == '-'
                    || valueByte == '.'
                    || valueByte == '_'
                    || valueByte == '~') {
                encoded.append((char) valueByte);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(valueByte >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(valueByte & 0x0f, 16)));
            }
        }
        return encoded.toString();
    }
}
