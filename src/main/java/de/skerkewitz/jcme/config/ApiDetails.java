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
        @JsonProperty("api_token") String apiToken,
        @JsonProperty("pat") String pat,
        @JsonProperty("cloud_id") String cloudId,
        @JsonProperty("api_url") String apiUrl
) {
    public static ApiDetails empty() {
        return new ApiDetails("", "", "", "", "");
    }

    public ApiDetails {
        username = stripNewlines(username);
        apiToken = stripNewlines(apiToken);
        pat = stripNewlines(pat);
        cloudId = cloudId == null ? "" : cloudId;
        apiUrl = apiUrl == null ? "" : apiUrl.trim();
    }

    /** Compact 4-arg constructor kept for source compatibility with older call-sites and tests. */
    public ApiDetails(String username, String apiToken, String pat, String cloudId) {
        this(username, apiToken, pat, cloudId, "");
    }

    private static String stripNewlines(String value) {
        if (value == null) return "";
        return value.replace("\r", "").replace("\n", "");
    }
}
