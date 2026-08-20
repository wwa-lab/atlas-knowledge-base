package com.atlas.knowledgebase.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Session cookie and TTL settings. Idle/absolute durations are placeholders until Security
 * approves production policy (OQ-01); they are not invented policy.
 */
@ConfigurationProperties(prefix = "atlas.session")
public class SessionProperties {

    /**
     * {@code __Host-} cookies require Secure and HTTPS. Local HTTP uses a non-prefixed name.
     */
    private String cookieName = "__Host-atlas-session";

    private boolean cookieSecure = true;

    private String cookieSameSite = "Strict";

    /** Placeholder, not Security-approved policy. */
    private Duration idleTtl = Duration.ofHours(8);

    /** Placeholder, not Security-approved policy. */
    private Duration absoluteTtl = Duration.ofHours(24);

    private String localSsoSubject = "local-dev";

    private String localSsoDisplayName = "Local Developer";

    private String localSsoEmail = "local-dev@localhost";

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    public Duration getIdleTtl() {
        return idleTtl;
    }

    public void setIdleTtl(Duration idleTtl) {
        this.idleTtl = idleTtl;
    }

    public Duration getAbsoluteTtl() {
        return absoluteTtl;
    }

    public void setAbsoluteTtl(Duration absoluteTtl) {
        this.absoluteTtl = absoluteTtl;
    }

    public String getLocalSsoSubject() {
        return localSsoSubject;
    }

    public void setLocalSsoSubject(String localSsoSubject) {
        this.localSsoSubject = localSsoSubject;
    }

    public String getLocalSsoDisplayName() {
        return localSsoDisplayName;
    }

    public void setLocalSsoDisplayName(String localSsoDisplayName) {
        this.localSsoDisplayName = localSsoDisplayName;
    }

    public String getLocalSsoEmail() {
        return localSsoEmail;
    }

    public void setLocalSsoEmail(String localSsoEmail) {
        this.localSsoEmail = localSsoEmail;
    }

    public String ssoStateCookieName() {
        return cookieName + "-sso-state";
    }
}
