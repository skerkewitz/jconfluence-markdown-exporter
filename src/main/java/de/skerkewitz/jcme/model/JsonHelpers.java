package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Small helpers for safely reading values out of Jackson {@link JsonNode}s. */
public final class JsonHelpers {

    private JsonHelpers() {}

    public static String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    public static String text(JsonNode node, String field, String fallback) {
        if (node == null) return fallback;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asText(fallback);
    }

    public static int intValue(JsonNode node, String field, int fallback) {
        if (node == null) return fallback;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asInt(fallback);
    }

    public static long longValue(JsonNode node, String field, long fallback) {
        if (node == null) return fallback;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asLong(fallback);
    }

    public static Long longOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asLong();
    }

    /** Walk a path of fields (returns missing node if any step is absent). */
    public static JsonNode walk(JsonNode node, String... fields) {
        JsonNode current = node;
        for (String f : fields) {
            if (current == null) return null;
            current = current.path(f);
        }
        return current;
    }

    /**
     * Extract the trailing space key from a Confluence {@code _expandable.space} reference
     * such as {@code "/rest/api/space/KEY"}. Returns an empty string when no key is present.
     */
    public static String extractSpaceKey(JsonNode data) {
        if (data == null) return "";
        String ref = JsonHelpers.text(data.get("_expandable"), "space", "");
        if (ref == null || ref.isEmpty()) return "";
        int slash = ref.lastIndexOf('/');
        return slash < 0 ? ref : ref.substring(slash + 1);
    }
}
