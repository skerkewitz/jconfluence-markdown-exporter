package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;

public record Version(int number, User by, String when, String friendlyWhen) {

    public static Version fromJson(JsonNode data) {
        if (data == null || data.isNull()) return empty();
        return new Version(
                JsonHelpers.intValue(data, "number", 0),
                User.fromJson(data.get("by")),
                JsonHelpers.text(data, "when"),
                JsonHelpers.text(data, "friendlyWhen")
        );
    }

    public static Version empty() {
        return new Version(0, User.empty(), "", "");
    }
}
