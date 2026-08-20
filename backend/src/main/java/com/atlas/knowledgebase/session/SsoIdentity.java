package com.atlas.knowledgebase.session;

/** Identity claims consumed from SSO. Must not include provider or model tokens. */
public record SsoIdentity(String subject, String displayName, String email) {}
