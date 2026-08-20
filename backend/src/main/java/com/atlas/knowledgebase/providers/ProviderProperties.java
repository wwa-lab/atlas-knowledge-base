package com.atlas.knowledgebase.providers;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Least-privilege OAuth scopes. Concrete GitHub/Confluence minima remain OQ-02 /
 * Security; these lists are placeholders and must not be expanded at callback.
 */
@ConfigurationProperties(prefix = "atlas.providers")
public class ProviderProperties {

    /** Placeholder until Security/Connector Owners approve GitHub minima (OQ-02). */
    private List<String> githubRequestedScopes = new ArrayList<>(List.of("repo:read"));

    /** Placeholder until Security/Connector Owners approve Confluence minima (OQ-02). */
    private List<String> confluenceRequestedScopes =
            new ArrayList<>(List.of("read:confluence-content"));

    /**
     * Local stub callback may mark the row {@code connected}. Real provider APIs remain
     * spike-gated; deployed planes fail closed without OAuth client config.
     */
    private boolean stubCompletesAsConnected = true;

    public List<String> getGithubRequestedScopes() {
        return githubRequestedScopes;
    }

    public void setGithubRequestedScopes(List<String> githubRequestedScopes) {
        this.githubRequestedScopes = githubRequestedScopes;
    }

    public List<String> getConfluenceRequestedScopes() {
        return confluenceRequestedScopes;
    }

    public void setConfluenceRequestedScopes(List<String> confluenceRequestedScopes) {
        this.confluenceRequestedScopes = confluenceRequestedScopes;
    }

    public boolean isStubCompletesAsConnected() {
        return stubCompletesAsConnected;
    }

    public void setStubCompletesAsConnected(boolean stubCompletesAsConnected) {
        this.stubCompletesAsConnected = stubCompletesAsConnected;
    }

    public List<String> requestedScopes(String provider) {
        return switch (provider) {
            case "github" -> List.copyOf(githubRequestedScopes);
            case "confluence" -> List.copyOf(confluenceRequestedScopes);
            default -> throw new InvalidProviderException(provider);
        };
    }
}
