package com.atlas.knowledgebase.providers;

import com.atlas.knowledgebase.session.UnauthenticatedException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-only OAuth stand-in. Does not call GitHub or Confluence. Spike note: real
 * provider APIs remain unproven.
 */
@Component
@Profile("local")
class LocalProviderAuthorizationClient implements ProviderAuthorizationClient {

    static final String LOCAL_CODE = "local-dev";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String authorizationUrl(String provider, String state, List<String> requestedScopes) {
        return "/api/v1/providers/"
                + provider
                + "/callback?code="
                + LOCAL_CODE
                + "&state="
                + state
                + "&scope="
                + String.join(" ", requestedScopes);
    }

    @Override
    public char[] redeemAccessToken(String provider, String authorizationCode) {
        if (!LOCAL_CODE.equals(authorizationCode)) {
            throw new UnauthenticatedException("Unknown local provider authorization code.");
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toCharArray();
    }
}
