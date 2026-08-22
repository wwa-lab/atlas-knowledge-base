package com.atlas.knowledgebase.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StubRetrieverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StubRetriever retriever = new StubRetriever(objectMapper);

    @Test
    void emitsCanonicalFixtureLocatorsForEveryProvider() throws Exception {
        JsonNode git = locator("git_markdown", "{\"repo\":\"org/runbooks\"}");
        assertThat(git.path("repository").asText()).isEqualTo("org/runbooks");
        assertThat(git.path("commit_sha").asText()).matches("[a-f0-9]{7,64}");
        assertThat(git.path("line_range").size()).isEqualTo(2);
        assertThat(git.path("atlas_fixture").asBoolean()).isTrue();

        JsonNode confluence =
                locator(
                        "confluence",
                        "{\"instance\":\"corp-confluence\",\"space_id\":\"SUPPORT\"}");
        assertThat(confluence.path("instance").asText()).isEqualTo("corp-confluence");
        assertThat(confluence.path("page_version").asInt()).isOne();
        assertThat(confluence.path("atlas_fixture").asBoolean()).isTrue();

        JsonNode dify = locator("dify", "{\"dataset_id\":\"ds_fixture\"}");
        assertThat(dify.path("dataset_id").asText()).isEqualTo("ds_fixture");
        assertThat(dify.path("chunk_id").asText()).isNotBlank();
        assertThat(dify.path("original_version").path("source_id").asText()).isNotBlank();
        assertThat(dify.path("original_version").path("version").asText()).isEqualTo("v1");
        assertThat(dify.path("atlas_fixture").asBoolean()).isTrue();
    }

    private JsonNode locator(String provider, String sourceIdentity) throws Exception {
        Retriever.Request request =
                new Retriever.Request(
                        "req_fixture",
                        "question",
                        "usr_fixture",
                        "lkb_fixture",
                        "bnd_fixture",
                        provider,
                        sourceIdentity,
                        Duration.ofSeconds(1),
                        null);
        Retriever.Hit hit = retriever.retrieve(request).hits().getFirst();
        return objectMapper.readTree(hit.locatorJson());
    }
}
