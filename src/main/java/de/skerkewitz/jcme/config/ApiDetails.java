package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-instance authentication credentials.
 *
 * <p>{@code apiUrl} is an optional override used when the Confluence/Jira REST API is
 * served from a different host than the HTML frontend (common in corporate setups behind
 * a reverse proxy). When set, the REST client connects to {@code apiUrl}; the auth map
 * key remains the page-URL host so URL parsing continues to work as expected.
 */
public record ApiDetails(
        @JsonProperty("username") String username,
        @JsonProperty("api_token") Secret apiToken,
        @JsonProperty("pat") Secret pat,
        @JsonProperty("cloud_id") String cloudId,
        @JsonProperty("api_url") String apiUrl
) {
    public static ApiDetails empty() {
        return new ApiDetails("", Secret.EMPTY, Secret.EMPTY, "", "");
    }

    public ApiDetails(String username, Secret apiToken, Secret pat, String cloudId, String apiUrl) {
        this.username = stripNewlines(username);
        this.apiToken = stripSecret(apiToken);
        this.pat = stripSecret(pat);
        this.cloudId = cloudId == null ? "" : cloudId;
        this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
    }

    /** Convenience constructor for callers (and tests) that hold raw credential strings. */
    public ApiDetails(String username, String apiToken, String pat, String cloudId, String apiUrl) {
        this(username, Secret.of(apiToken), Secret.of(pat), cloudId, apiUrl);
    }

    /** Compact 4-arg constructor kept for source compatibility with older call-sites and tests. */
    public ApiDetails(String username, String apiToken, String pat, String cloudId) {
        this(username, apiToken, pat, cloudId, "");
    }

    private static String stripNewlines(String value) {
        if (value == null) return "";
        return value.replace("\r", "").replace("\n", "");
    }

    private static Secret stripSecret(Secret value) {
        if (value == null) return Secret.EMPTY;
        String stripped = stripNewlines(value.reveal());
        return Secret.of(stripped);
    }
}
