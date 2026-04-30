package de.skerkewitz.jcme.markdown;

import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.export.TemplateVars;
import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.model.Ancestor;
import de.skerkewitz.jcme.model.Label;
import de.skerkewitz.jcme.model.Page;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level orchestrator: turn a {@link Page} into the final markdown string.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Wrap the page body with an {@code <h1>} title prefix when configured.</li>
 *   <li>Run {@link ConfluencePageConverter} over the HTML.</li>
 *   <li>Escape Confluence template-style placeholders.</li>
 *   <li>Prepend YAML front matter (page-properties macro accumulator + page labels).</li>
 *   <li>Optionally prepend breadcrumb links from the ancestor chain.</li>
 * </ol>
 */
public final class PageRenderer {

    private final ConfluenceFetcher fetcher;
    private final ExportConfig export;
    private final TemplateVars templateVars;
    private final Path drawioFilesRoot;

    public PageRenderer(ConfluenceFetcher fetcher, ExportConfig export,
                        TemplateVars templateVars, Path drawioFilesRoot) {
        this.fetcher = fetcher;
        this.export = export;
        this.templateVars = templateVars;
        this.drawioFilesRoot = drawioFilesRoot;
    }

    public String render(Page page) {
        RenderingContext rc = new RenderingContext(page, export, fetcher, templateVars, drawioFilesRoot);
        ConfluencePageConverter converter = new ConfluencePageConverter(rc);

        String html = export.includeDocumentTitle()
                ? "<h1>" + page.title() + "</h1>" + page.body()
                : page.body();
        String body = converter.convert(html);
        body = PlaceholderEscaper.escape(body);

        List<String> tags = new ArrayList<>();
        for (Label l : page.labels()) tags.add("#" + l.name());
        String frontMatter = FrontMatterRenderer.render(rc.pageProperties(), tags);

        StringBuilder out = new StringBuilder();
        if (!frontMatter.isEmpty()) out.append(frontMatter).append('\n');
        if (export.pageBreadcrumbs() && !page.ancestors().isEmpty()) {
            out.append(renderBreadcrumbs(rc, page.ancestors(), converter)).append('\n');
        }
        out.append(body).append('\n');
        return out.toString();
    }

    private String renderBreadcrumbs(RenderingContext rc, List<Ancestor> ancestors,
                                     ConfluencePageConverter converter) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ancestors.size(); i++) {
            if (i > 0) out.append(" > ");
            Ancestor a = ancestors.get(i);
            out.append(renderAncestorLink(rc, a));
        }
        return out.toString();
    }

    private String renderAncestorLink(RenderingContext rc, Ancestor ancestor) {
        // Resolve the ancestor as a full Page for the export-path template.
        try {
            Page page = fetcher.getPage(ancestor.id(), ancestor.baseUrl());
            if (page.isInaccessible()) return "[Page not accessible (ID: " + ancestor.id() + ")]";
            if (rc.pageHref() == HrefResolver.Style.WIKI) {
                return "[[" + page.title() + "]]";
            }
            Path target = rc.pageExportPath(page);
            Path current = rc.pageExportPath(rc.page());
            String href = HrefResolver.encodeSpaces(HrefResolver.resolve(target, current, rc.pageHref()));
            return "[" + page.title() + "](" + href + ")";
        } catch (Exception e) {
            return "[Page not accessible (ID: " + ancestor.id() + ")]";
        }
    }
}
