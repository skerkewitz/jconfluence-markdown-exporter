package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.model.Ancestor;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.Descendant;
import de.skerkewitz.jcme.model.Document;
import de.skerkewitz.jcme.model.Page;
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
        vars.put("page_id", String.valueOf(page.id()));
        vars.put("page_title", sanitizer.sanitize(page.title()));
        return vars;
    }

    public Map<String, String> forDescendant(Descendant descendant, ConfluenceFetcher fetcher) {
        Map<String, String> vars = base(descendant, fetcher);
        vars.put("page_id", String.valueOf(descendant.id()));
        vars.put("page_title", sanitizer.sanitize(descendant.title()));
        return vars;
    }

    public Map<String, String> forAttachment(Attachment attachment, ConfluenceFetcher fetcher) {
        Map<String, String> vars = base(attachment, fetcher);
        vars.put("attachment_id", attachment.id());
        vars.put("attachment_title", sanitizer.sanitize(attachment.titleWithoutExtension()));
        // file_id is a GUID — no sanitization needed.
        vars.put("attachment_file_id", attachment.fileId());
        vars.put("attachment_extension", attachment.extension());
        return vars;
    }

    private Map<String, String> base(Document doc, ConfluenceFetcher fetcher) {
        Space space = doc.space();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("space_key", sanitizer.sanitize(space == null ? "" : space.key()));
        vars.put("space_name", sanitizer.sanitize(space == null ? "" : space.name()));
        vars.put("homepage_id", "");
        vars.put("homepage_title", "");
        if (space != null && space.homepage() != null) {
            vars.put("homepage_id", String.valueOf(space.homepage()));
            try {
                Page homepage = fetcher.getPage(space.homepage(), doc.baseUrl());
                vars.put("homepage_title", sanitizer.sanitize(homepage.title()));
            } catch (Exception ignored) {
                // Leave homepage_title blank on lookup failure.
            }
        }
        List<Ancestor> ancestors = doc.ancestors();
        vars.put("ancestor_ids",
                ancestors.stream().map(a -> String.valueOf(a.id())).collect(Collectors.joining("/")));
        vars.put("ancestor_titles",
                ancestors.stream().map(a -> sanitizer.sanitize(a.title())).collect(Collectors.joining("/")));
        return vars;
    }
}
