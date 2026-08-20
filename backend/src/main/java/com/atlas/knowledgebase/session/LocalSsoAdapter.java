package com.atlas.knowledgebase.session;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-plane SSO stand-in. Not a production IdP. The callback code {@code local-dev} is
 * accepted only on the {@code local} profile.
 */
@Component
@Profile("local")
public class LocalSsoAdapter implements SsoAdapter {

    static final String LOCAL_CODE = "local-dev";

    private final SessionProperties properties;

    public LocalSsoAdapter(SessionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String authorizationUrl(String state) {
        return "/api/v1/auth/sso/callback?code=" + LOCAL_CODE + "&state=" + state;
    }

    @Override
    public SsoIdentity redeem(String authorizationCode) {
        if (!LOCAL_CODE.equals(authorizationCode)) {
            throw new UnauthenticatedException("Unknown local SSO authorization code.");
        }
        return new SsoIdentity(
                properties.getLocalSsoSubject(),
                properties.getLocalSsoDisplayName(),
                properties.getLocalSsoEmail());
    }
}
