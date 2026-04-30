package de.skerkewitz.jcme.model;

import java.util.Map;

/**
 * Tiny MIME-type → file-extension table covering the types Confluence attaches in practice.
 * Equivalent to Python's {@code mimetypes.guess_extension} but lookup-only and deterministic.
 */
public final class MimeTypes {

    private static final Map<String, String> TYPE_TO_EXT = Map.ofEntries(
            // Images
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/jpg", ".jpg"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/svg+xml", ".svg"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/bmp", ".bmp"),
            Map.entry("image/tiff", ".tif"),
            // Documents
            Map.entry("application/pdf", ".pdf"),
            Map.entry("application/msword", ".doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/vnd.ms-excel", ".xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.ms-powerpoint", ".ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
            Map.entry("application/rtf", ".rtf"),
            // Archives
            Map.entry("application/zip", ".zip"),
            Map.entry("application/x-zip-compressed", ".zip"),
            Map.entry("application/x-tar", ".tar"),
            Map.entry("application/gzip", ".gz"),
            Map.entry("application/x-7z-compressed", ".7z"),
            // Text
            Map.entry("text/plain", ".txt"),
            Map.entry("text/csv", ".csv"),
            Map.entry("text/html", ".html"),
            Map.entry("text/markdown", ".md"),
            Map.entry("text/xml", ".xml"),
            Map.entry("application/xml", ".xml"),
            Map.entry("application/json", ".json"),
            // Audio / video
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/quicktime", ".mov"),
            // Confluence-specific
            Map.entry("application/vnd.jgraph.mxfile", ".drawio")
    );

    private MimeTypes() {}

    public static String extensionFor(String mediaType) {
        if (mediaType == null) return "";
        String key = mediaType.toLowerCase().trim();
        int semi = key.indexOf(';');
        if (semi >= 0) key = key.substring(0, semi).trim();
        return TYPE_TO_EXT.getOrDefault(key, "");
    }
}
