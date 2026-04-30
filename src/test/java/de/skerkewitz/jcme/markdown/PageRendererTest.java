package de.skerkewitz.jcme.markdown;

import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.export.FilenameSanitizer;
import de.skerkewitz.jcme.export.TemplateVars;
import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.model.Ancestor;
import de.skerkewitz.jcme.model.Label;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.Space;
import de.skerkewitz.jcme.model.Version;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PageRendererTest {

    private static final String BASE_URL = "https://x.atlassian.net";

    private static class TestFetcher extends ConfluenceFetcher {
        final Map<Long, Page> pages = new HashMap<>();
        TestFetcher() { super(null, null); }
        @Override public Page getPage(long pageId, String baseUrl) {
            Page p = pages.get(pageId);
            return p != null ? p : Page.inaccessible(pageId, baseUrl);
        }
    }

    private PageRenderer renderer(ExportConfig export, ConfluenceFetcher fetcher) {
        return new PageRenderer(fetcher, export, new TemplateVars(new FilenameSanitizer(export)), null);
    }

    private static Space space(String key, String name) {
        return new Space(BASE_URL, key, name, "", null);
    }

    @Test
    void title_is_prepended_when_include_document_title_is_true() {
        Space s = space("K", "Space");
        Page p = new Page(BASE_URL, 1, "Hello World", s, List.of(), Version.empty(),
                "<p>body</p>", "", "", List.of(), List.of());

        String md = renderer(ExportConfig.defaults(), new TestFetcher()).render(p);

        assertThat(md).startsWith("# Hello World");
        assertThat(md).contains("body");
    }

    @Test
    void title_is_omitted_when_include_document_title_is_false() {
        Space s = space("K", "Space");
        Page p = new Page(BASE_URL, 1, "Hello World", s, List.of(), Version.empty(),
                "<p>body</p>", "", "", List.of(), List.of());

        ExportConfig disabled = withIncludeTitle(false);
        String md = renderer(disabled, new TestFetcher()).render(p);

        assertThat(md).doesNotContain("# Hello World");
        assertThat(md).contains("body");
    }

    @Test
    void labels_become_tags_in_front_matter() {
        Space s = space("K", "Space");
        Page p = new Page(BASE_URL, 1, "T", s, List.of(), Version.empty(),
                "<p>x</p>", "", "",
                List.of(new Label("1", "alpha", "g"), new Label("2", "beta", "g")),
                List.of());

        String md = renderer(ExportConfig.defaults(), new TestFetcher()).render(p);

        assertThat(md).startsWith("---\n");
        assertThat(md).contains("tags:");
        assertThat(md).contains("#alpha");
        assertThat(md).contains("#beta");
    }

    @Test
    void breadcrumbs_link_each_ancestor() {
        Space s = space("K", "Space");
        Page top = new Page(BASE_URL, 10, "Top", s, List.of(), Version.empty(), "", "", "", List.of(), List.of());
        Page mid = new Page(BASE_URL, 20, "Mid", s, List.of(), Version.empty(), "", "", "", List.of(), List.of());
        TestFetcher fetcher = new TestFetcher();
        fetcher.pages.put(10L, top);
        fetcher.pages.put(20L, mid);

        Ancestor a1 = new Ancestor(BASE_URL, 10, "Top", s, List.of(), Version.empty());
        Ancestor a2 = new Ancestor(BASE_URL, 20, "Mid", s, List.of(), Version.empty());
        Page page = new Page(BASE_URL, 30, "Leaf", s, List.of(a1, a2), Version.empty(),
                "<p>body</p>", "", "", List.of(), List.of());

        String md = renderer(ExportConfig.defaults(), fetcher).render(page);

        assertThat(md).contains("[Top]");
        assertThat(md).contains(" > ");
        assertThat(md).contains("[Mid]");
    }

    @Test
    void breadcrumbs_disabled_when_setting_off() {
        Space s = space("K", "Space");
        Ancestor a = new Ancestor(BASE_URL, 10, "Top", s, List.of(), Version.empty());
        Page page = new Page(BASE_URL, 30, "Leaf", s, List.of(a), Version.empty(),
                "<p>body</p>", "", "", List.of(), List.of());
        TestFetcher fetcher = new TestFetcher();
        fetcher.pages.put(10L, new Page(BASE_URL, 10, "Top", s, List.of(), Version.empty(),
                "", "", "", List.of(), List.of()));

        ExportConfig noBreadcrumbs = withBreadcrumbs(false);
        String md = renderer(noBreadcrumbs, fetcher).render(page);

        assertThat(md).doesNotContain(" > ");
    }

    @Test
    void placeholder_escaping_is_applied_after_conversion() {
        Space s = space("K", "Space");
        Page p = new Page(BASE_URL, 1, "T", s, List.of(), Version.empty(),
                "<p>fill in &lt;your-name&gt;</p>", "", "", List.of(), List.of());

        String md = renderer(ExportConfig.defaults(), new TestFetcher()).render(p);

        assertThat(md).contains("\\<your-name\\>");
    }

    private static ExportConfig withIncludeTitle(boolean include) {
        ExportConfig d = ExportConfig.defaults();
        return new ExportConfig(d.logLevel(), d.outputPath(), d.pageHref(), d.pagePath(),
                d.attachmentHref(), d.attachmentPath(), d.attachmentExportAll(),
                d.pageBreadcrumbs(), d.pagePropertiesAsFrontMatter(), d.filenameEncoding(),
                d.filenameLength(), d.filenameLowercase(), include, d.enableJiraEnrichment(),
                d.skipUnchanged(), d.cleanupStale(), d.lockfileName(), d.existenceCheckBatchSize());
    }

    private static ExportConfig withBreadcrumbs(boolean enabled) {
        ExportConfig d = ExportConfig.defaults();
        return new ExportConfig(d.logLevel(), d.outputPath(), d.pageHref(), d.pagePath(),
                d.attachmentHref(), d.attachmentPath(), d.attachmentExportAll(),
                enabled, d.pagePropertiesAsFrontMatter(), d.filenameEncoding(),
                d.filenameLength(), d.filenameLowercase(), d.includeDocumentTitle(),
                d.enableJiraEnrichment(), d.skipUnchanged(), d.cleanupStale(),
                d.lockfileName(), d.existenceCheckBatchSize());
    }
}
