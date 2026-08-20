package com.atlas.knowledgebase.secrets;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed on deployed planes until Security names the approved secret
 * product (ADR-0006). Does not invent Vault vs cloud SM.
 */
@Component
@Profile({"non-prod", "prod"})
public class UnconfiguredProductionSecretResolver implements SecretResolver, SecretStore {

    static final String NOT_CONFIGURED =
            "Production secret manager product is not named (ADR-0006). "
                    + "Refusing to resolve secret_ref on non-prod/prod.";

    @Override
    public char[] resolve(String secretRef) {
        throw new SecretResolutionException(NOT_CONFIGURED);
    }

    @Override
    public String store(String logicalName, char[] secret) {
        throw new SecretResolutionException(NOT_CONFIGURED);
    }
}
