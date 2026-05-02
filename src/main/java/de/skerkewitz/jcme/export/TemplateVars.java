package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.model.Ancestor;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.Descendant;
import de.skerkewitz.jcme.model.Document;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.PageId;
import de.skerkewitz.jcme.model.Space;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Build the variable map consumed by {@link PathTemplate#render(String, Map)}.
 *
 * <p>Variables are sanitized via {@link FilenameSanitizer} before substitution so they
 * are safe to use as path components on every supported OS.
 */
public final class TemplateVars {

    private final FilenameSanitizer sanitizer;

    public TemplateVars(FilenameSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public Map<String, String> forPage(Page page, ConfluenceFetcher fetcher) {
        Map<String, String> vars = base(page, fetcher);
        vars.put("page_id", page.id().toString());
        vars.put("page_title", sanitizer.sanitize(page.title()));
        return vars;
    }

    public Map<String, String> forDescendant(Descendant descendant, ConfluenceFetcher fetcher) {
        Map<String, String> vars = base(descendant, fetcher);
        vars.put("page_id", descendant.id().toString());
        vars.put("page_title", sanitizer.sanitize(descendant.title()));
        return vars;
    }

    public Map<String, String> forAttachment(Attachment attachment, ConfluenceFetcher fetcher) {
        Map<String, String> vars = base(attachment, fetcher);
        vars.put("attachment_id", attachment.id());
        vars.put("attachment_title", sanitizer.sanitize(attachment.titleWithoutExtension()));
        String fileKey = attachment.fileId();
        if (fileKey == null || fileKey.isBlank()) {
            fileKey = attachment.id();
        }
        if (fileKey == null || fileKey.isBlank()) {
            fileKey = sanitizer.sanitize(attachment.titleWithoutExtension());
        }
        vars.put("attachment_file_id", fileKey == null ? "" : fileKey);
        vars.put("attachment_extension", attachment.extension());
        return vars;
    }

    private Map<String, String> base(Document doc, ConfluenceFetcher fetcher) {
        Space space = doc.space();
        Map<String, String> vars = new LinkedHashMap<>();
        String spaceKeyStr = space == null || space.key() == null ? "" : space.key().value();
        vars.put("space_key", sanitizer.sanitize(spaceKeyStr));
        vars.put("space_name", sanitizer.sanitize(space == null ? "" : space.name()));
        vars.put("homepage_id", "");
        vars.put("homepage_title", "");
        if (space != null && space.homepage() != null) {
            PageId homepageId = PageId.of(space.homepage());
            vars.put("homepage_id", homepageId.toString());
            try {
                Page homepage = fetcher.getPage(homepageId, doc.baseUrl());
                vars.put("homepage_title", sanitizer.sanitize(homepage.title()));
            } catch (Exception ignored) {
                // Leave homepage_title blank on lookup failure.
            }
        }
        List<Ancestor> ancestors = doc.ancestors();
        vars.put("ancestor_ids",
                ancestors.stream().map(a -> a.id().toString()).collect(Collectors.joining("/")));
        vars.put("ancestor_titles",
                ancestors.stream().map(a -> sanitizer.sanitize(a.title())).collect(Collectors.joining("/")));
        return vars;
    }
}
