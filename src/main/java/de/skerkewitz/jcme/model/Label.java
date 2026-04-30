package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;

public record Label(String id, String name, String prefix) {

    public static Label fromJson(JsonNode data) {
        return new Label(
                JsonHelpers.text(data, "id"),
                JsonHelpers.text(data, "name"),
                JsonHelpers.text(data, "prefix")
        );
    }
}
