package com.atlas.knowledgebase.session;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fail-closed SSO until Security names the corporate IdP for deployed planes. */
@Component
@Profile("!local")
public class UnconfiguredSsoAdapter implements SsoAdapter {

    static final String MESSAGE =
            "Corporate SSO IdP is not configured on this plane; session login is unavailable.";

    @Override
    public String authorizationUrl(String state) {
        throw new SsoNotConfiguredException(MESSAGE);
    }

    @Override
    public SsoIdentity redeem(String authorizationCode) {
        throw new SsoNotConfiguredException(MESSAGE);
    }
}
