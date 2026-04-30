package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;

public record User(
        String accountId,
        String username,
        String displayName,
        String publicName,
        String email
) {
    public static User fromJson(JsonNode data) {
        if (data == null || data.isNull()) return empty();
        return new User(
                JsonHelpers.text(data, "accountId"),
                JsonHelpers.text(data, "username"),
                JsonHelpers.text(data, "displayName"),
                JsonHelpers.text(data, "publicName"),
                JsonHelpers.text(data, "email")
        );
    }

    public static User empty() {
        return new User("", "", "", "", "");
    }
}
