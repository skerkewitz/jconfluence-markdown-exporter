package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public record Space(
        String baseUrl,
        String key,
        String name,
        String description,
        Long homepage
) {
    public static Space fromJson(JsonNode data, String baseUrl) {
        if (data == null || data.isNull()) return empty(baseUrl);
        JsonNode descNode = JsonHelpers.walk(data, "description", "plain");
        String description = descNode == null ? "" : JsonHelpers.text(descNode, "value");
        Long homepage = JsonHelpers.longOrNull(data.get("homepage"), "id");
        return new Space(
                baseUrl,
                JsonHelpers.text(data, "key"),
                JsonHelpers.text(data, "name"),
                description,
                homepage
        );
    }

    public static Space empty(String baseUrl) {
        return new Space(baseUrl, "", "", "", null);
    }

    public Optional<Long> homepageOpt() {
        return Optional.ofNullable(homepage);
    }
}
