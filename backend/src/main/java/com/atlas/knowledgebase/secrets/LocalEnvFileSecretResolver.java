package com.atlas.knowledgebase.secrets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Local-only stub (ADR-0006): {@code env:NAME} and {@code file:relative-name}
 * under a configured directory. Must not be used to read production secrets.
 */
@Component
@Profile("local")
public class LocalEnvFileSecretResolver implements SecretResolver {

    static final String ENV_PREFIX = "env:";
    static final String FILE_PREFIX = "file:";

    private final Path fileRoot;

    public LocalEnvFileSecretResolver(
            @Value("${atlas.secrets.file-root:${user.dir}/.local-data/secrets}") String fileRoot) {
        this.fileRoot = Path.of(fileRoot).toAbsolutePath().normalize();
    }

    @Override
    public char[] resolve(String secretRef) {
        if (!StringUtils.hasText(secretRef)) {
            throw new SecretResolutionException("secret_ref is required");
        }
        String ref = secretRef.trim();
        if (ref.startsWith(ENV_PREFIX)) {
            return fromEnv(ref.substring(ENV_PREFIX.length()));
        }
        if (ref.startsWith(FILE_PREFIX)) {
            return fromFile(ref.substring(FILE_PREFIX.length()));
        }
        throw new SecretResolutionException("Unsupported secret_ref scheme for local stub");
    }

    private static char[] fromEnv(String name) {
        if (!StringUtils.hasText(name)) {
            throw new SecretResolutionException("env secret_ref is missing a variable name");
        }
        String value = System.getenv(name.trim());
        if (value == null) {
            throw new SecretResolutionException("Environment variable for secret_ref is not set");
        }
        return value.toCharArray();
    }

    private char[] fromFile(String relativeName) {
        if (!StringUtils.hasText(relativeName)) {
            throw new SecretResolutionException("file secret_ref is missing a path");
        }
        Path resolved = fileRoot.resolve(relativeName.trim()).normalize();
        if (!resolved.startsWith(fileRoot)) {
            throw new SecretResolutionException("file secret_ref escapes the local secrets directory");
        }
        try {
            if (!Files.isRegularFile(resolved)) {
                throw new SecretResolutionException("file secret_ref does not exist");
            }
            String value = Files.readString(resolved, StandardCharsets.UTF_8).strip();
            return value.toCharArray();
        } catch (IOException ex) {
            throw new SecretResolutionException("file secret_ref could not be read", ex);
        }
    }
}
