package com.atlas.knowledgebase.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class EvidenceNavigationPolicyTest {

    private final EvidenceNavigationPolicy policy = new EvidenceNavigationPolicy();
    private final EvidenceLocatorValidator validator = new EvidenceLocatorValidator(new ObjectMapper());

    @Test
    void permitsOnlyExactTrustedHttpsOriginWithoutUserInfo() {
        URI accepted = policy.requireTrustedProviderNavigation(
                "https://github.example/org/repo/blob/abc1234/docs/a.md#L1-L2",
                "https://github.example");
        assertThat(accepted.getHost()).isEqualTo("github.example");

        assertRejected("http://github.example/org/repo", "https://github.example");
        assertRejected("https://user@github.example/org/repo", "https://github.example");
        assertRejected("https://evil.example/org/repo", "https://github.example");
        assertRejected("https://github.example.evil.test/org/repo", "https://github.example");
    }

    @Test
    void rejectsUntrustedConfiguredOriginShape() {
        assertRejected("https://github.example/a", "https://user@github.example");
        assertRejected("https://github.example/a", "https://github.example/base");
        assertRejected("https://github.example/a", "http://github.example");
    }

    @Test
    void buildsEncodedFixtureNavigationUnderReservedOriginOnly() {
        var locator = validator.validate(
                "git_markdown",
                """
                {"repository":"org/repo","commit_sha":"abc1234","path":"docs/运营 runbook.md",
                 "line_range":[10,40],"atlas_fixture":true}
                """);

        URI navigation = policy.fixtureNavigation(locator);

        assertThat(navigation.getScheme()).isEqualTo("https");
        assertThat(navigation.getHost()).isEqualTo("evidence-fixture.invalid");
        assertThat(navigation.getRawPath()).doesNotContain(" ").contains("%E8%BF%90%E8%90%A5%20runbook.md");
        assertThat(policy.requireFixtureNavigation(navigation.toString())).isEqualTo(navigation);
        assertThatThrownBy(() -> policy.requireFixtureNavigation("https://evil.example/a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRejected(String navigation, String origin) {
        assertThatThrownBy(() -> policy.requireTrustedProviderNavigation(navigation, origin))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
