package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * Holder for a credential string (PAT, API token, password) whose {@link #toString()}
 * never reveals the underlying value. Use {@link #reveal()} only when the value is about
 * to leave the JVM (HTTP header construction, etc.).
 *
 * <p>Not a {@code record}: records auto-generate a {@code toString()} that prints every
 * component, defeating the redaction. JSON serialization round-trips via {@link #reveal()}
 * so on-disk format is unchanged.
 */
public final class Secret {

    public static final Secret EMPTY = new Secret("");

    private final String value;

    @JsonCreator
    public static Secret of(String value) {
        if (value == null || value.isEmpty()) return EMPTY;
        return new Secret(value);
    }

    private Secret(String value) {
        this.value = value;
    }

    /** The raw value. Only call when the secret is about to leave the JVM. */
    @JsonValue
    public String reveal() {
        return value;
    }

    public boolean isPresent() {
        return !value.isEmpty();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public String toString() {
        return value.isEmpty() ? "<empty>" : "***";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Secret s && Objects.equals(value, s.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
