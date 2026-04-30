package de.skerkewitz.jcme.lockfile;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AttachmentEntry(
        @JsonProperty("version") int version,
        @JsonProperty("path") String path
) {}
