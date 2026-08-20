package com.atlas.knowledgebase.secrets;

/**
 * Writes secret material into the secret boundary and returns a {@code secret_ref}.
 * Implementations must not log the secret value.
 */
public interface SecretStore {

    /**
     * @param logicalName filesystem-safe name, not a path
     * @param secret caller should wipe after this method returns
     * @return opaque {@code secret_ref} such as {@code file:github-usr_abc}
     */
    String store(String logicalName, char[] secret);

    /**
     * Removes secret material for {@code secretRef} if it lives in this store.
     * Missing files are success. Must not log the secret value. Sentinel refs such as
     * {@code pending:oauth} must not be passed here.
     */
    void delete(String secretRef);
}
