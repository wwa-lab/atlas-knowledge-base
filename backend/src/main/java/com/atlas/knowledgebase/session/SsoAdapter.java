package com.atlas.knowledgebase.session;

/** Corporate SSO adapter. Production product/IdP remains ADR-gated. */
public interface SsoAdapter {

    String authorizationUrl(String state);

    SsoIdentity redeem(String authorizationCode);
}
