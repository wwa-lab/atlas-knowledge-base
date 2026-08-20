package com.atlas.knowledgebase.providers;

import java.util.List;

interface ProviderAuthorizationClient {

    String authorizationUrl(String provider, String state, List<String> requestedScopes);

    char[] redeemAccessToken(String provider, String authorizationCode);
}
