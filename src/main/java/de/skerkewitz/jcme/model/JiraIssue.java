package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;

public record JiraIssue(IssueKey key, String summary, String description, String status) {

    public static JiraIssue fromJson(JsonNode data) {
        JsonNode fields = data == null ? null : data.get("fields");
        String status = fields == null ? "" : JsonHelpers.text(fields.get("status"), "name");
        String rawKey = JsonHelpers.text(data, "key");
        IssueKey key = IssueKey.tryParse(rawKey).orElse(null);
        return new JiraIssue(
                key,
                fields == null ? "" : JsonHelpers.text(fields, "summary"),
                fields == null ? "" : JsonHelpers.text(fields, "description"),
                status
        );
    }
}
