package com.atlas.knowledgebase.secrets;

/**
 * Resolves a {@code secret_ref} to secret material. Implementations must not
 * log the resolved value. Production product name is ADR-0006 / Security.
 */
public interface SecretResolver {

    /**
     * @param secretRef opaque reference such as {@code env:ATLAS_GITHUB_TOKEN}
     *     or {@code file:github-token}
     * @return secret characters; caller should wipe the array after use
     */
    char[] resolve(String secretRef);
}
