package com.atlas.knowledgebase.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.knowledgebase.adapters.EvidenceResolver;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvidenceResolverRegistryTest {

    @Test
    void findsRegisteredProviderAndReturnsEmptyForUnknown() {
        EvidenceResolver resolver = resolver("git_markdown");
        EvidenceResolverRegistry registry = new EvidenceResolverRegistry(List.of(resolver));

        assertThat(registry.find("git_markdown")).containsSame(resolver);
        assertThat(registry.find("dify")).isEmpty();
        assertThat(registry.providers()).containsExactly("git_markdown");
    }

    @Test
    void rejectsDuplicateAndBlankProviderRegistrations() {
        assertThatThrownBy(() -> new EvidenceResolverRegistry(List.of(resolver("dify"), resolver("dify"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple active evidence resolvers");
        assertThatThrownBy(() -> new EvidenceResolverRegistry(List.of(resolver(" "))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void resolverResultRejectsImpossibleVerificationCombinations() {
        assertThatThrownBy(() -> new EvidenceResolver.Result(
                        EvidenceResolver.Status.OK,
                        EvidenceResolver.VerificationMode.NONE,
                        false,
                        null,
                        null,
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceResolver.Result(
                        EvidenceResolver.Status.UNAVAILABLE,
                        EvidenceResolver.VerificationMode.PROVIDER,
                        false,
                        null,
                        "https://github.example",
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvidenceResolver.Result.fixtureMoved(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EvidenceResolver.Result(
                        EvidenceResolver.Status.OK,
                        EvidenceResolver.VerificationMode.PROVIDER,
                        true,
                        null,
                        null,
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted origin");
        assertThatThrownBy(() -> new EvidenceResolver.Result(
                        EvidenceResolver.Status.OK,
                        EvidenceResolver.VerificationMode.FIXTURE,
                        false,
                        "https://evidence-fixture.invalid/git_markdown/a",
                        "https://caller-controlled.invalid",
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed fixture origin");

        assertThat(new EvidenceResolver.Result(
                        EvidenceResolver.Status.MOVED,
                        EvidenceResolver.VerificationMode.PROVIDER,
                        true,
                        null,
                        null,
                        Optional.of(JsonNodeFactory.instance.objectNode())))
                .extracting(EvidenceResolver.Result::providerVerified)
                .isEqualTo(true);
    }

    private static EvidenceResolver resolver(String provider) {
        return new EvidenceResolver() {
            @Override
            public Set<String> providerProfiles() {
                return Set.of(provider);
            }

            @Override
            public AuthorizationResult authorize(AuthorizationRequest request) {
                return AuthorizationResult.unknown();
            }

            @Override
            public Result resolve(Request request) {
                return Result.unknown(VerificationMode.NONE);
            }
        };
    }
}
