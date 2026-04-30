package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiDetails(
        @JsonProperty("username") String username,
        @JsonProperty("api_token") String apiToken,
        @JsonProperty("pat") String pat,
        @JsonProperty("cloud_id") String cloudId
) {
    public static ApiDetails empty() {
        return new ApiDetails("", "", "", "");
    }

    public ApiDetails {
        username = stripNewlines(username);
        apiToken = stripNewlines(apiToken);
        pat = stripNewlines(pat);
        cloudId = cloudId == null ? "" : cloudId;
    }

    private static String stripNewlines(String value) {
        if (value == null) return "";
        return value.replace("\r", "").replace("\n", "");
    }
}
