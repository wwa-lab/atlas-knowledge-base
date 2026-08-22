package com.atlas.knowledgebase.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvidenceLocatorValidatorTest {

    private EvidenceLocatorValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EvidenceLocatorValidator(new ObjectMapper());
    }

    @Test
    void acceptsCanonicalGitLocatorAndMoveMapping() {
        var validated = validator.validate(
                "git_markdown",
                """
                {
                  "repository":"org/repo",
                  "commit_sha":"abc1234",
                  "path":"docs/runbook.md",
                  "line_range":[10,40],
                  "stable_source_id":"source_123",
                  "atlas_fixture":true,
                  "move_mapping":{"moved_to_locator":{
                    "repository":"org/repo",
                    "commit_sha":"def5678",
                    "path":"docs/archive/runbook.md",
                    "line_range":[12,42],
                    "stable_source_id":"source_123",
                    "atlas_fixture":true
                  }}
                }
                """);

        assertThat(validated.providerProfile()).isEqualTo("git_markdown");
        assertThat(validated.fixtureMarked()).isTrue();
        assertThat(validated.locator().path("path").asText()).isEqualTo("docs/runbook.md");
        assertThat(validated.movedToLocator()).isPresent();

        JsonNode exposedLocator = validated.locator();
        ((ObjectNode) exposedLocator).put("path", "mutated.md");
        JsonNode exposedTarget = validated.movedToLocator().orElseThrow();
        ((ObjectNode) exposedTarget).put("path", "mutated-target.md");
        assertThat(validated.locator().path("path").asText()).isEqualTo("docs/runbook.md");
        assertThat(validated.movedToLocator().orElseThrow().path("path").asText())
                .isEqualTo("docs/archive/runbook.md");
    }

    @Test
    void acceptsCanonicalConfluenceAndDifyLocators() {
        assertThat(validator.validate(
                                "confluence",
                                """
                                {"instance":"corp-confluence","page_id":"123456",
                                 "page_version":17,"attachment_id":"att_42","attachment_version":3}
                                """)
                        .fixtureMarked())
                .isFalse();

        assertThat(validator.validate(
                                "dify",
                                """
                                {"dataset_id":"dataset_123","document_id":"document_456",
                                 "chunk_id":"chunk_789",
                                 "original_version":{"source_id":"source_abc","version":"v17"}}
                                """)
                        .locator()
                        .path("original_version")
                        .path("version")
                        .asText())
                .isEqualTo("v17");
    }

    @Test
    void rejectsDuplicateUnknownAndMissingFields() {
        assertInvalid("git_markdown", """
                {"repository":"org/repo","repository":"evil/repo","commit_sha":"abc1234",
                 "path":"a.md","line_range":[1,2]}
                """);
        assertInvalid("confluence", """
                {"instance":"corp","page_id":"1","page_version":1,"unexpected":"x"}
                """);
        assertInvalid("dify", """
                {"dataset_id":"d","document_id":"doc","chunk_id":"chunk"}
                """);
    }

    @Test
    void rejectsInvalidGitIdentityPathRangeAndMoveMapping() {
        assertInvalid("git_markdown", git("../repo", "abc1234", "a.md", "[1,2]"));
        assertInvalid("git_markdown", git("org/repo", "not-sha", "a.md", "[1,2]"));
        assertInvalid("git_markdown", git("org/repo", "abc1234", "../secret.md", "[1,2]"));
        assertInvalid("git_markdown", git("org/repo", "abc1234", "https:evil", "[1,2]"));
        assertInvalid("git_markdown", git("org/repo", "abc1234", "a\\b.md", "[1,2]"));
        assertInvalid("git_markdown", git("org/repo", "abc1234", "a.md", "[2,1]"));
        assertInvalid("git_markdown", git("org/repo", "abc1234", "a.md", "[0,1]"));
        assertInvalid("git_markdown", git("org/repo", "abc1234", "a.md", "[1,2147483648]"));

        assertInvalid(
                "git_markdown",
                """
                {"repository":"org/repo","commit_sha":"abc1234","path":"a.md","line_range":[1,2],
                 "stable_source_id":"source_a","move_mapping":{"moved_to_locator":{
                   "repository":"org/repo","commit_sha":"def5678","path":"b.md","line_range":[1,2],
                   "stable_source_id":"source_b"}}}
                """);
    }

    @Test
    void rejectsNonNfcAndOversizedGitPath() {
        assertInvalid("git_markdown", git("org/repo", "abc1234", "cafe\u0301.md", "[1,2]"));
        String path = "a".repeat(2049);
        assertThat(path.getBytes(StandardCharsets.UTF_8)).hasSize(2049);
        assertInvalid("git_markdown", git("org/repo", "abc1234", path, "[1,2]"));
    }

    @Test
    void rejectsAttachmentHalfPairAndInvalidDifyOriginalVersion() {
        assertInvalid(
                "confluence",
                """
                {"instance":"corp","page_id":"1","page_version":1,"attachment_id":"att"}
                """);
        assertInvalid(
                "dify",
                """
                {"dataset_id":"d","document_id":"doc","chunk_id":"chunk",
                 "original_version":{"source_id":"source","version":"v1","extra":"x"}}
                """);
    }

    @Test
    void rejectsFalseFixtureMarkerAndMarkerMismatchInMoveTarget() {
        assertInvalid(
                "dify",
                """
                {"dataset_id":"d","document_id":"doc","chunk_id":"chunk",
                 "original_version":{"source_id":"source","version":"v1"},"atlas_fixture":false}
                """);
        assertInvalid(
                "git_markdown",
                """
                {"repository":"org/repo","commit_sha":"abc1234","path":"a.md","line_range":[1,2],
                 "stable_source_id":"source","atlas_fixture":true,
                 "move_mapping":{"moved_to_locator":{"repository":"org/repo","commit_sha":"def5678",
                 "path":"b.md","line_range":[1,2],"stable_source_id":"source"}}}
                """);
    }

    @Test
    void rejectsUnknownProviderTooLargeJsonAndExcessiveDepth() {
        assertInvalid("sharepoint", "{}");
        String oversized = "{\"x\":\"" + "a".repeat(16_384) + "\"}";
        assertThatThrownBy(() -> validator.validate("dify", oversized))
                .isInstanceOf(EvidenceLocatorValidator.InvalidLocatorException.class);
        assertInvalid(
                "dify",
                """
                {"dataset_id":"d","document_id":"doc","chunk_id":"chunk",
                 "original_version":{"source_id":"source","version":{"too":{"deep":"v1"}}}}
                """);
    }

    @Test
    void validatesAdapterMoveTargetsAgainstFrozenSchemaAndIdentity() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var source =
                validator.validate(
                        "git_markdown",
                        """
                        {"repository":"org/repo","commit_sha":"abc1234","path":"a.md",
                         "line_range":[1,2],"stable_source_id":"source_1"}
                        """);
        var accepted =
                validator.validateMoveTarget(
                        source,
                        mapper.readTree(
                                """
                                {"repository":"org/repo","commit_sha":"def5678","path":"b.md",
                                 "line_range":[1,2],"stable_source_id":"source_1"}
                                """));

        assertThat(accepted.locator().path("path").asText()).isEqualTo("b.md");
        assertThatThrownBy(
                        () ->
                                validator.validateMoveTarget(
                                        source,
                                        mapper.readTree(
                                                """
                                                {"repository":"org/repo","commit_sha":"def5678","path":"b.md",
                                                 "line_range":[1,2],"stable_source_id":"other"}
                                                """)))
                .isInstanceOf(EvidenceLocatorValidator.InvalidLocatorException.class);
        assertThatThrownBy(
                        () ->
                                validator.validateMoveTarget(
                                        source,
                                        mapper.readTree(
                                                """
                                                {"repository":"org/repo","commit_sha":"def5678","path":"b.md",
                                                 "line_range":[1,2],"stable_source_id":"source_1",
                                                 "atlas_fixture":true}
                                                """)))
                .isInstanceOf(EvidenceLocatorValidator.InvalidLocatorException.class);
        assertThatThrownBy(
                        () -> validator.validateMoveTarget(source, mapper.readTree("\"not-an-object\"")))
                .isInstanceOf(EvidenceLocatorValidator.InvalidLocatorException.class);
    }

    @Test
    void rejectsNestedAdapterMoveMappings() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var source =
                validator.validate(
                        "git_markdown",
                        """
                        {"repository":"org/repo","commit_sha":"abc1234","path":"a.md",
                         "line_range":[1,2],"stable_source_id":"source_1"}
                        """);

        assertThatThrownBy(
                        () ->
                                validator.validateMoveTarget(
                                        source,
                                        mapper.readTree(
                                                """
                                                {"repository":"org/repo","commit_sha":"def5678","path":"b.md",
                                                 "line_range":[1,2],"stable_source_id":"source_1",
                                                 "move_mapping":{"moved_to_locator":{
                                                   "repository":"org/repo","commit_sha":"fedcba9","path":"c.md",
                                                   "line_range":[1,2],"stable_source_id":"source_1"}}}
                                                """)))
                .isInstanceOf(EvidenceLocatorValidator.InvalidLocatorException.class);
    }

    private void assertInvalid(String provider, String locator) {
        assertThatThrownBy(() -> validator.validate(provider, locator))
                .isInstanceOf(EvidenceLocatorValidator.InvalidLocatorException.class);
    }

    private static String git(String repository, String commit, String path, String lineRange) {
        return """
                {"repository":"%s","commit_sha":"%s","path":"%s","line_range":%s}
                """.formatted(repository, commit, path.replace("\\", "\\\\"), lineRange);
    }
}
