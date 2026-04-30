package de.skerkewitz.jcme.markdown;

import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.export.PathTemplate;
import de.skerkewitz.jcme.export.TemplateVars;
import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.Descendant;
import de.skerkewitz.jcme.model.Page;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * State carried through a single page rendering pass.
 *
 * <p>Holds the page being rendered, the fetcher that resolves linked pages/users/Jira issues,
 * and a shared {@code pageProperties} accumulator used by the {@code details} macro to seed
 * YAML front matter.
 */
public final class RenderingContext {

    private final Page page;
    private final ExportConfig export;
    private final ConfluenceFetcher fetcher;
    private final TemplateVars templateVars;
    private final Map<String, Object> pageProperties = new LinkedHashMap<>();
    private final HrefResolver.Style pageHref;
    private final HrefResolver.Style attachmentHref;
    private final Path drawioFilesRoot; // optional; may be null when mermaid extraction is disabled

    public RenderingContext(Page page, ExportConfig export, ConfluenceFetcher fetcher,
                            TemplateVars templateVars, Path drawioFilesRoot) {
        this.page = page;
        this.export = export;
        this.fetcher = fetcher;
        this.templateVars = templateVars;
        this.pageHref = HrefResolver.parseStyle(export.pageHref());
        this.attachmentHref = HrefResolver.parseStyle(export.attachmentHref());
        this.drawioFilesRoot = drawioFilesRoot;
    }

    public Page page() { return page; }
    public ExportConfig export() { return export; }
    public ConfluenceFetcher fetcher() { return fetcher; }
    public TemplateVars templateVars() { return templateVars; }
    public Map<String, Object> pageProperties() { return pageProperties; }
    public HrefResolver.Style pageHref() { return pageHref; }
    public HrefResolver.Style attachmentHref() { return attachmentHref; }
    public Path drawioFilesRoot() { return drawioFilesRoot; }

    /** Compute the export-relative path for a Confluence page. */
    public Path pageExportPath(Page p) {
        return Paths.get(PathTemplate.render(export.pagePath(), templateVars.forPage(p, fetcher)));
    }

    /** Compute the export-relative path for a Confluence descendant page. */
    public Path descendantExportPath(Descendant d) {
        return Paths.get(PathTemplate.render(export.pagePath(), templateVars.forDescendant(d, fetcher)));
    }

    /** Compute the export-relative path for an attachment. */
    public Path attachmentExportPath(Attachment a) {
        return Paths.get(PathTemplate.render(export.attachmentPath(), templateVars.forAttachment(a, fetcher)));
    }
}
