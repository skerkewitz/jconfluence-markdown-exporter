package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication configuration for Confluence and Jira.
 * Credentials are stored in maps keyed by the instance base URL
 * (e.g. {@code "https://company.atlassian.net"}). No "active" pointer is kept —
 * the right instance is selected by matching the URL of the page or space being exported.
 */
public record AuthConfig(
        @JsonProperty("confluence") Map<String, ApiDetails> confluence,
        @JsonProperty("jira") Map<String, ApiDetails> jira
) {
    public static AuthConfig empty() {
        return new AuthConfig(new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    public AuthConfig {
        confluence = confluence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(confluence);
        jira = jira == null ? new LinkedHashMap<>() : new LinkedHashMap<>(jira);
    }
}
