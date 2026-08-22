package com.atlas.knowledgebase.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Spike-gated Git Browse stub. Does not call GitHub. Detecting {@code manifest.json} here must not
 * change knowledge-base capability.
 */
@Component
public class StubGitBrowse implements GitBrowse {

    static final List<Entry> DEFAULT_ENTRIES =
            List.of(
                    new Entry("docs/", "dir"),
                    new Entry("docs/runbook.md", "file"),
                    new Entry("manifest.json", "file"));

    private final ObjectMapper objectMapper;

    public StubGitBrowse(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Tree tree(Request request) {
        JsonNode identity = identity(request);
        return new Tree(request.bindingId(), entries(identity), originalUrl(identity));
    }

    @Override
    public Preview preview(Request request, String path) {
        JsonNode identity = identity(request);
        String markdown =
                "manifest.json".equals(path)
                        ? "{ \"name\": \"stub-manifest\" }\n"
                        : "# Stub preview\n\nAuthorized Markdown for `" + path + "`.\n";
        return new Preview(request.bindingId(), path, markdown, originalUrl(identity));
    }

    private List<Entry> entries(JsonNode identity) {
        JsonNode listed = identity.get("browse_tree");
        if (listed == null || !listed.isArray() || listed.isEmpty()) {
            return DEFAULT_ENTRIES;
        }
        List<Entry> out = new ArrayList<>();
        for (JsonNode node : listed) {
            String path = node.path("path").asText("");
            String type = node.path("type").asText("file");
            if (!path.isBlank()) {
                out.add(new Entry(path, type));
            }
        }
        return out.isEmpty() ? DEFAULT_ENTRIES : List.copyOf(out);
    }

    private static String originalUrl(JsonNode identity) {
        String explicit = identity.path("original_url").asText("");
        if (!explicit.isBlank()) {
            return explicit;
        }
        String repo = identity.path("repo").asText("");
        return repo.isBlank() ? "https://github.example/org/repo" : "https://github.example/" + repo;
    }

    private JsonNode identity(Request request) {
        if (request.sourceIdentityJson() == null || request.sourceIdentityJson().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(request.sourceIdentityJson());
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}
