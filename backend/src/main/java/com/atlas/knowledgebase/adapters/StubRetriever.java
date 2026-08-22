package com.atlas.knowledgebase.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fixture retriever for Dify, Git Markdown, and Confluence. Does not call providers. Controlled by
 * {@code source_identity.retrieval_fixture}: {@code timeout}, {@code failed}, {@code security},
 * {@code item_omit}, typed {@code throw_failed}/{@code throw_security}, unknown
 * {@code throw_unknown}, or success (default).
 */
@Component
@Profile({"local", "non-prod"})
@ConditionalOnProperty(
        name = "atlas.retrieval.stub.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StubRetriever implements Retriever {

    static final int TOP_K = 5;

    private final ObjectMapper objectMapper;

    public StubRetriever(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<String> providerProfiles() {
        return Set.of("dify", "git_markdown", "confluence");
    }

    @Override
    public Result retrieve(Request request) {
        String fixture = fixture(request.sourceIdentityJson());
        return switch (fixture) {
            case "timeout" -> Result.timeout();
            case "failed" -> Result.failed();
            case "security" -> Result.security();
            case "throw_failed" -> throw RetrieverException.failed("fixture connector failure");
            case "throw_security" -> throw RetrieverException.security("fixture security failure");
            case "throw_unknown" -> throw new IllegalStateException("fixture unknown failure");
            case "item_omit" -> {
                Hit kept = hit(request, "kept", 1);
                Hit omitted = hit(request, "restricted", 2);
                yield Result.success(List.of(kept), List.of(omitted));
            }
            default -> Result.success(
                    List.of(hit(request, "primary", 1), hit(request, "secondary", 2)), List.of());
        };
    }

    private Hit hit(Request request, String suffix, int rank) {
        String documentId = request.bindingId() + ":" + suffix;
        String fingerprint = request.providerProfile() + ":" + documentId + ":v1";
        String locator =
                switch (request.providerProfile()) {
                    case "git_markdown" ->
                            "{\"repository\":\"org/runbooks\",\"commit_sha\":\"abc123def\",\"path\":\"docs/"
                                    + suffix
                                    + ".md\",\"line_range\":[1,20]}";
                    case "confluence" ->
                            "{\"space\":\"SUPPORT\",\"page_id\":\""
                                    + documentId
                                    + "\",\"version\":1}";
                    default ->
                            "{\"dataset_id\":\"ds_fixture\",\"document_id\":\"" + documentId + "\"}";
                };
        return new Hit(
                documentId,
                "Fixture " + suffix,
                "Local retrieval fixture; not a real internal excerpt (" + suffix + ").",
                "v1",
                locator,
                rank,
                fingerprint);
    }

    private String fixture(String sourceIdentityJson) {
        if (sourceIdentityJson == null || sourceIdentityJson.isBlank()) {
            return "success";
        }
        try {
            JsonNode node = objectMapper.readTree(sourceIdentityJson);
            String named = node.path("retrieval_fixture").asText("");
            return named.isBlank() ? "success" : named;
        } catch (JsonProcessingException e) {
            return "success";
        }
    }
}
