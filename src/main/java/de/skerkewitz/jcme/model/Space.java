package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.api.BaseUrl;

import java.util.Optional;

public record Space(
        BaseUrl baseUrl,
        SpaceKey key,
        String name,
        String description,
        Long homepage
) {
    public static Space fromJson(JsonNode data, BaseUrl baseUrl) {
        if (data == null || data.isNull()) return empty(baseUrl);
        JsonNode descNode = JsonHelpers.walk(data, "description", "plain");
        String description = descNode == null ? "" : JsonHelpers.text(descNode, "value");
        Long homepage = JsonHelpers.longOrNull(data.get("homepage"), "id");
        String rawKey = JsonHelpers.text(data, "key");
        SpaceKey key = rawKey == null || rawKey.isEmpty() ? null : SpaceKey.of(rawKey);
        return new Space(
                baseUrl,
                key,
                JsonHelpers.text(data, "name"),
                description,
                homepage
        );
    }

    public static Space empty(BaseUrl baseUrl) {
        return new Space(baseUrl, null, "", "", null);
    }

    public Optional<Long> homepageOpt() {
        return Optional.ofNullable(homepage);
    }
}
