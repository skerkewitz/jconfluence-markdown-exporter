package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Strongly-typed wrapper around a Confluence page identifier.
 *
 * <p>The Confluence REST API exposes page IDs as numeric strings; the domain layer
 * uses {@code long} via {@link #value()}, while persistence (lockfile, REST URLs)
 * goes through {@link #toString()} so the on-disk JSON keys remain unchanged.
 */
public record PageId(long value) {

    public PageId {
        if (value <= 0) {
            throw new IllegalArgumentException("PageId must be positive, got " + value);
        }
    }

    @JsonCreator
    public static PageId of(long value) {
        return new PageId(value);
    }

    /** Parse a string-encoded id (REST responses, URL path components). */
    public static PageId parse(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("PageId string must be non-empty");
        }
        return new PageId(Long.parseLong(value));
    }

    @JsonValue
    public long jsonValue() {
        return value;
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
