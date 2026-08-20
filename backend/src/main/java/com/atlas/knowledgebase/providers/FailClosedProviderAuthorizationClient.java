package com.atlas.knowledgebase.providers;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
class FailClosedProviderAuthorizationClient implements ProviderAuthorizationClient {

    static final String MESSAGE =
            "GitHub/Confluence OAuth client is not configured on this plane (spike-gated).";

    @Override
    public String authorizationUrl(String provider, String state, List<String> requestedScopes) {
        throw new ProviderOauthNotConfiguredException(MESSAGE);
    }

    @Override
    public char[] redeemAccessToken(String provider, String authorizationCode) {
        throw new ProviderOauthNotConfiguredException(MESSAGE);
    }
}
