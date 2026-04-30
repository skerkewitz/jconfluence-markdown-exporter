package de.skerkewitz.jcme.model;

import java.util.List;

public record Attachment(
        String baseUrl,
        String id,
        String title,
        Space space,
        List<Ancestor> ancestors,
        Version version,
        long fileSize,
        String mediaType,
        String mediaTypeDescription,
        String fileId,
        String collectionName,
        String downloadLink,
        String comment
) implements Document {

    public Attachment {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }

    public String extension() {
        if ("draw.io diagram".equals(comment) && "application/vnd.jgraph.mxfile".equals(mediaType)) {
            return ".drawio";
        }
        if ("draw.io preview".equals(comment) && "image/png".equals(mediaType)) {
            return ".drawio.png";
        }
        return MimeTypes.extensionFor(mediaType);
    }

    /** {@code "{file_id}{extension}"} — used as the canonical local filename. */
    public String filename() {
        return fileId + extension();
    }

    /** Title with the trailing extension stripped (matches Python's stem-style behavior). */
    public String titleWithoutExtension() {
        String ext = extension();
        if (!ext.isEmpty() && title.endsWith(ext)) {
            return title.substring(0, title.length() - ext.length());
        }
        int dot = title.lastIndexOf('.');
        return dot < 0 ? title : title.substring(0, dot);
    }
}
