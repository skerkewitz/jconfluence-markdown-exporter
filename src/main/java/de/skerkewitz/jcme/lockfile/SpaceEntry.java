package de.skerkewitz.jcme.lockfile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

public record SpaceEntry(
        @JsonProperty("pages") Map<String, PageEntry> pages
) {
    public SpaceEntry {
        pages = pages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pages);
    }
}
