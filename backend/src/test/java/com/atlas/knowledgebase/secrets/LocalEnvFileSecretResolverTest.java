package com.atlas.knowledgebase.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class LocalEnvFileSecretResolverTest {

    @Autowired
    private SecretResolver secretResolver;

    @Test
    void localContextExposesEnvFileStub() {
        assertThat(secretResolver).isInstanceOf(LocalEnvFileSecretResolver.class);
    }

    @Test
    void fileRefReadsFromConfiguredRoot(@TempDir Path tempDir) throws Exception {
        Path secretFile = tempDir.resolve("github-token");
        Files.writeString(secretFile, "ghp_test_not_a_real_token\n", StandardCharsets.UTF_8);
        LocalEnvFileSecretResolver resolver =
                new LocalEnvFileSecretResolver(tempDir.toString());

        char[] secret = resolver.resolve("file:github-token");
        try {
            assertThat(new String(secret)).isEqualTo("ghp_test_not_a_real_token");
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    @Test
    void fileRefRejectsPathEscape(@TempDir Path tempDir) {
        LocalEnvFileSecretResolver resolver =
                new LocalEnvFileSecretResolver(tempDir.toString());

        assertThatThrownBy(() -> resolver.resolve("file:../outside"))
                .isInstanceOf(SecretResolutionException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void deployedPlaneResolverRefusesUntilProductNamed() {
        UnconfiguredProductionSecretResolver resolver = new UnconfiguredProductionSecretResolver();
        assertThatThrownBy(() -> resolver.resolve("env:ANY"))
                .isInstanceOf(SecretResolutionException.class)
                .hasMessageContaining("ADR-0006");
    }
}
