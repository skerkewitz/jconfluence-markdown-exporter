package de.skerkewitz.jcme.model;

import de.skerkewitz.jcme.api.BaseUrl;

import java.util.List;
import java.util.Optional;

public record Page(
        BaseUrl baseUrl,
        PageId id,
        String title,
        Space space,
        List<Ancestor> ancestors,
        Version version,
        String body,
        String bodyExport,
        String editor2,
        List<Label> labels,
        List<Attachment> attachments
) implements ExportablePage {

    public static final String INACCESSIBLE_TITLE = "Page not accessible";

    public Page {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
        labels = labels == null ? List.of() : List.copyOf(labels);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public boolean isInaccessible() {
        return INACCESSIBLE_TITLE.equals(title);
    }

    public Optional<Attachment> attachmentById(String attachmentId) {
        if (attachmentId == null || attachmentId.isEmpty()) return Optional.empty();
        for (Attachment a : attachments) {
            if (a.id() != null && a.id().contains(attachmentId)) return Optional.of(a);
            if (a.fileId() != null && !a.fileId().isEmpty() && a.fileId().contains(attachmentId)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    public Optional<Attachment> attachmentByFileId(String fileId) {
        if (fileId == null || fileId.isEmpty()) return Optional.empty();
        for (Attachment a : attachments) {
            if (a.fileId() != null && !a.fileId().isEmpty() && a.fileId().contains(fileId)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    public List<Attachment> attachmentsByTitle(String title) {
        return attachments.stream().filter(a -> a.title().equals(title)).toList();
    }

    /** Build a sentinel "Page not accessible" page for fallbacks (mirrors the Python behavior). */
    public static Page inaccessible(PageId pageId, BaseUrl baseUrl) {
        return new Page(
                baseUrl,
                pageId,
                INACCESSIBLE_TITLE,
                Space.empty(baseUrl),
                List.of(),
                Version.empty(),
                "",
                "",
                "",
                List.of(),
                List.of()
        );
    }
}
