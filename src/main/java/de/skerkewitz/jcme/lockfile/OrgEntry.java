package de.skerkewitz.jcme.lockfile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

public record OrgEntry(
        @JsonProperty("spaces") Map<String, SpaceEntry> spaces
) {
    public OrgEntry {
        spaces = spaces == null ? new LinkedHashMap<>() : new LinkedHashMap<>(spaces);
    }
}
