package de.skerkewitz.jcme.lockfile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

public record PageEntry(
        @JsonProperty("title") String title,
        @JsonProperty("version") int version,
        @JsonProperty("export_path") String exportPath,
        @JsonProperty("attachments") Map<String, AttachmentEntry> attachments
) {
    public PageEntry {
        attachments = attachments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attachments);
    }
}
