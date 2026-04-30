package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;

public record JiraIssue(String key, String summary, String description, String status) {

    public static JiraIssue fromJson(JsonNode data) {
        JsonNode fields = data == null ? null : data.get("fields");
        String status = fields == null ? "" : JsonHelpers.text(fields.get("status"), "name");
        return new JiraIssue(
                JsonHelpers.text(data, "key"),
                fields == null ? "" : JsonHelpers.text(fields, "summary"),
                fields == null ? "" : JsonHelpers.text(fields, "description"),
                status
        );
    }
}
