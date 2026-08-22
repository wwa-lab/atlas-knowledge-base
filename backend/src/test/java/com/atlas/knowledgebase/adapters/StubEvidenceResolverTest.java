package com.atlas.knowledgebase.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlas.knowledgebase.evidence.EvidenceLocatorValidator;
import com.atlas.knowledgebase.evidence.EvidenceNavigationPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class StubEvidenceResolverTest {

    private ObjectMapper mapper;
    private EvidenceLocatorValidator validator;
    private StubEvidenceResolver resolver;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        validator = new EvidenceLocatorValidator(mapper);
        resolver = new StubEvidenceResolver(new EvidenceNavigationPolicy());
    }

    @Test
    void componentIsRestrictedToLocalAndTestProfiles() {
        Profile profile = StubEvidenceResolver.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactlyInAnyOrder("local", "test");
    }

    @Test
    void separatelyAuthorizesFromExplicitCurrentBindingFixtureSetting() throws Exception {
        var locator = fixtureGit(false);

        assertThat(resolver.authorize(auth(locator, "authorized")))
                .isEqualTo(EvidenceResolver.AuthorizationResult.authorized());
        assertThat(resolver.authorize(auth(locator, "denied")))
                .isEqualTo(EvidenceResolver.AuthorizationResult.accessDenied());
        assertThat(resolver.authorize(auth(locator, "unknown")))
                .isEqualTo(EvidenceResolver.AuthorizationResult.unknown());
    }

    @Test
    void defaultsAuthorizationToAuthorizedOnlyForDefensivelyValidDoubleMarkers() throws Exception {
        var locator = fixtureGit(false);
        var identity = mapper.readTree("{\"repo\":\"org/repo\",\"atlas_fixture\":true}");

        assertThat(resolver.authorize(new EvidenceResolver.AuthorizationRequest(
                                "git_markdown", locator, identity, context())))
                .isEqualTo(EvidenceResolver.AuthorizationResult.authorized());
        assertThat(resolver.authorize(new EvidenceResolver.AuthorizationRequest(
                                "git_markdown",
                                locator,
                                mapper.readTree("{\"repo\":\"org/repo\"}"),
                                context())))
                .isEqualTo(EvidenceResolver.AuthorizationResult.unknown());
    }

    @Test
    void resolvesExplicitFixtureOutcomesWithFrozenVerificationSemantics() throws Exception {
        var ok = resolver.resolve(request(fixtureGit(false), "ok", EvidenceResolver.Operation.OPEN));
        assertThat(ok.status()).isEqualTo(EvidenceResolver.Status.OK);
        assertThat(ok.verificationMode()).isEqualTo(EvidenceResolver.VerificationMode.FIXTURE);
        assertThat(ok.providerVerified()).isFalse();
        assertThat(ok.navigationUrl()).startsWith("https://evidence-fixture.invalid/");
        assertThat(ok.trustedOrigin()).isEqualTo(EvidenceNavigationPolicy.FIXTURE_ORIGIN);

        var inspected = resolver.resolve(request(fixtureGit(false), "ok", EvidenceResolver.Operation.INSPECT));
        assertThat(inspected.navigationUrl()).isNull();

        var unavailable = resolver.resolve(request(fixtureGit(false), "unavailable", EvidenceResolver.Operation.OPEN));
        assertThat(unavailable.status()).isEqualTo(EvidenceResolver.Status.UNAVAILABLE);
        assertThat(unavailable.navigationUrl()).isNull();

        var unknown = resolver.resolve(request(fixtureGit(false), "unknown", EvidenceResolver.Operation.OPEN));
        assertThat(unknown).isEqualTo(EvidenceResolver.Result.unknown(EvidenceResolver.VerificationMode.FIXTURE));
    }

    @Test
    void fixtureMovedRequiresValidatedSameIdentityMoveMappingAndNeverNavigates() throws Exception {
        var moved = resolver.resolve(request(fixtureGit(true), "moved", EvidenceResolver.Operation.OPEN));
        assertThat(moved.status()).isEqualTo(EvidenceResolver.Status.MOVED);
        assertThat(moved.verificationMode()).isEqualTo(EvidenceResolver.VerificationMode.FIXTURE);
        assertThat(moved.providerVerified()).isFalse();
        assertThat(moved.navigationUrl()).isNull();
        assertThat(moved.movedToLocator()).isPresent();

        var noMapping = resolver.resolve(request(fixtureGit(false), "moved", EvidenceResolver.Operation.OPEN));
        assertThat(noMapping).isEqualTo(EvidenceResolver.Result.unknown(EvidenceResolver.VerificationMode.FIXTURE));
    }

    @Test
    void failsClosedWithoutBothMarkersOrExplicitResolutionSetting() throws Exception {
        var liveLocator = validator.validate(
                "git_markdown",
                "{\"repository\":\"org/repo\",\"commit_sha\":\"abc1234\",\"path\":\"a.md\",\"line_range\":[1,2]}");
        var markedIdentity = mapper.readTree("{\"repo\":\"org/repo\",\"atlas_fixture\":true,\"evidence_resolution_fixture\":\"ok\"}");

        assertThat(resolver.resolve(new EvidenceResolver.Request(
                                "git_markdown",
                                liveLocator,
                                markedIdentity,
                                context(),
                                EvidenceResolver.Operation.OPEN)))
                .isEqualTo(EvidenceResolver.Result.unknown(EvidenceResolver.VerificationMode.NONE));

        var locator = fixtureGit(false);
        var unmarkedIdentity = mapper.readTree("{\"repo\":\"org/repo\",\"evidence_resolution_fixture\":\"ok\"}");
        assertThat(resolver.resolve(new EvidenceResolver.Request(
                                "git_markdown",
                                locator,
                                unmarkedIdentity,
                                context(),
                                EvidenceResolver.Operation.OPEN)))
                .isEqualTo(EvidenceResolver.Result.unknown(EvidenceResolver.VerificationMode.NONE));

        var noSetting = mapper.readTree("{\"repo\":\"org/repo\",\"atlas_fixture\":true}");
        assertThat(resolver.resolve(new EvidenceResolver.Request(
                                "git_markdown",
                                locator,
                                noSetting,
                                context(),
                                EvidenceResolver.Operation.OPEN)))
                .isEqualTo(EvidenceResolver.Result.unknown(EvidenceResolver.VerificationMode.FIXTURE));
    }

    private EvidenceResolver.AuthorizationRequest auth(
            EvidenceLocatorValidator.ValidatedLocator locator, String outcome) throws Exception {
        return new EvidenceResolver.AuthorizationRequest(
                "git_markdown",
                locator,
                mapper.readTree("{\"repo\":\"org/repo\",\"atlas_fixture\":true,"
                        + "\"evidence_authorization_fixture\":\"" + outcome + "\"}"),
                context());
    }

    private EvidenceResolver.Request request(
            EvidenceLocatorValidator.ValidatedLocator locator,
            String outcome,
            EvidenceResolver.Operation operation) throws Exception {
        return new EvidenceResolver.Request(
                "git_markdown",
                locator,
                mapper.readTree("{\"repo\":\"org/repo\",\"atlas_fixture\":true,"
                        + "\"evidence_resolution_fixture\":\"" + outcome + "\"}"),
                context(),
                operation);
    }

    private static EvidenceResolver.AuthorizationContext context() {
        return new EvidenceResolver.AuthorizationContext("usr_1", "bnd_1", "app");
    }

    private EvidenceLocatorValidator.ValidatedLocator fixtureGit(boolean moved) {
        String mapping = moved
                ? ",\"stable_source_id\":\"source_1\",\"move_mapping\":{\"moved_to_locator\":{"
                        + "\"repository\":\"org/repo\",\"commit_sha\":\"def5678\",\"path\":\"b.md\","
                        + "\"line_range\":[1,2],\"stable_source_id\":\"source_1\",\"atlas_fixture\":true}}"
                : "";
        return validator.validate(
                "git_markdown",
                "{\"repository\":\"org/repo\",\"commit_sha\":\"abc1234\",\"path\":\"a.md\","
                        + "\"line_range\":[1,2],\"atlas_fixture\":true" + mapping + "}");
    }
}
