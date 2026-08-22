package com.atlas.knowledgebase.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvidenceSourceContinuityTest {

    private EvidenceLocatorValidator validator;
    private EvidenceSourceContinuity continuity;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        validator = new EvidenceLocatorValidator(mapper);
        continuity = new EvidenceSourceContinuity(mapper);
    }

    @Test
    void verifiesProviderSpecificCurrentSourceBoundary() {
        assertThat(continuity.check(
                                git(false), "{\"repo\":\"org/repo\"}"))
                .isEqualTo(EvidenceSourceContinuity.Check.continuous(false));
        assertThat(continuity.check(
                                dify(false), "{\"dataset_id\":\"dataset_123\"}"))
                .isEqualTo(EvidenceSourceContinuity.Check.continuous(false));
        assertThat(continuity.check(
                                confluence(false),
                                "{\"instance\":\"corp-confluence\",\"space_id\":\"SPACE\"}"))
                .isEqualTo(EvidenceSourceContinuity.Check.continuous(false));
    }

    @Test
    void failsClosedForMismatchMissingOrMalformedIdentity() {
        assertThat(continuity.check(git(false), "{\"repo\":\"other/repo\"}").continuous()).isFalse();
        assertThat(continuity.check(dify(false), "{}").continuous()).isFalse();
        assertThat(continuity.check(confluence(false), "{\"instance\":\"corp-confluence\"}").continuous())
                .isFalse();
        assertThat(continuity.check(git(false), "not-json").continuous()).isFalse();
    }

    @Test
    void failsClosedForTrailingTextAndAdditionalJsonValues() {
        String valid = "{\"repo\":\"org/repo\"}";

        assertThat(continuity.check(git(false), valid + " trailing").failureReason())
                .isEqualTo("source_identity_invalid");
        assertThat(continuity.check(git(false), valid + "{}").failureReason())
                .isEqualTo("source_identity_invalid");
    }

    @Test
    void requiresMatchingLiteralFixtureMarkers() {
        assertThat(continuity.check(git(true), "{\"repo\":\"org/repo\",\"atlas_fixture\":true}"))
                .isEqualTo(EvidenceSourceContinuity.Check.continuous(true));
        assertThat(continuity.check(git(true), "{\"repo\":\"org/repo\"}").continuous()).isFalse();
        assertThat(continuity.check(git(false), "{\"repo\":\"org/repo\",\"atlas_fixture\":true}").continuous())
                .isFalse();
        assertThat(continuity.check(git(true), "{\"repo\":\"org/repo\",\"atlas_fixture\":false}").continuous())
                .isFalse();
    }

    private EvidenceLocatorValidator.ValidatedLocator git(boolean fixture) {
        return validator.validate(
                "git_markdown",
                "{\"repository\":\"org/repo\",\"commit_sha\":\"abc1234\",\"path\":\"a.md\","
                        + "\"line_range\":[1,2]"
                        + (fixture ? ",\"atlas_fixture\":true" : "")
                        + "}");
    }

    private EvidenceLocatorValidator.ValidatedLocator dify(boolean fixture) {
        return validator.validate(
                "dify",
                "{\"dataset_id\":\"dataset_123\",\"document_id\":\"doc\",\"chunk_id\":\"chunk\","
                        + "\"original_version\":{\"source_id\":\"source\",\"version\":\"v1\"}"
                        + (fixture ? ",\"atlas_fixture\":true" : "")
                        + "}");
    }

    private EvidenceLocatorValidator.ValidatedLocator confluence(boolean fixture) {
        return validator.validate(
                "confluence",
                "{\"instance\":\"corp-confluence\",\"page_id\":\"123\",\"page_version\":1"
                        + (fixture ? ",\"atlas_fixture\":true" : "")
                        + "}");
    }
}
