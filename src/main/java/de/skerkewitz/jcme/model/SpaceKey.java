package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.regex.Pattern;

/**
 * Confluence space-key identifier (e.g. {@code "ENG"}, {@code "MY_TEAM"}).
 *
 * <p>The validation regex is the union of the cloud and server forms — cloud allows
 * {@code [A-Za-z0-9_~-]}, server additionally allows {@code .} — so legacy keys from
 * either deployment style remain accepted.
 */
public record SpaceKey(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._~-]+");

    public SpaceKey {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("SpaceKey value must be non-empty");
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid space key: " + value);
        }
    }

    @JsonCreator
    public static SpaceKey of(String value) {
        return new SpaceKey(value);
    }

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
