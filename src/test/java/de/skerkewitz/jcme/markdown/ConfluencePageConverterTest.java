package de.skerkewitz.jcme.markdown;

import de.skerkewitz.jcme.api.BaseUrl;
import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.export.FilenameSanitizer;
import de.skerkewitz.jcme.export.TemplateVars;
import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.model.Ancestor;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.JiraIssue;
import de.skerkewitz.jcme.model.Label;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.PageId;
import de.skerkewitz.jcme.model.Space;
import de.skerkewitz.jcme.model.User;
import de.skerkewitz.jcme.model.Version;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluencePageConverterTest {

    private static final BaseUrl BASE_URL = BaseUrl.of("https://x.atlassian.net");

    private static class TestFetcher extends ConfluenceFetcher {
        final Map<PageId, Page> pages = new HashMap<>();
        final Map<String, User> users = new HashMap<>();
        final Map<String, JiraIssue> jiraIssues = new HashMap<>();

        TestFetcher() { super(null, null); }

        @Override public Page getPage(PageId pageId, BaseUrl baseUrl) {
            Page p = pages.get(pageId);
            return p != null ? p : Page.inaccessible(pageId, baseUrl);
        }

        @Override public User getUser(de.skerkewitz.jcme.model.UserIdentifier id, BaseUrl baseUrl) {
            String key = id.value();
            User u = users.get(key);
            if (u == null) throw new de.skerkewitz.jcme.api.exceptions.ApiException(
                    "User not found", 404, baseUrl.value());
            return u;
        }

        @Override public Optional<JiraIssue> getJiraIssue(de.skerkewitz.jcme.model.IssueKey issueKey, BaseUrl jiraUrl) {
            return Optional.ofNullable(jiraIssues.get(issueKey.value()));
        }
    }

    private RenderingContext rc(Page page, TestFetcher fetcher) {
        return rc(page, fetcher, ExportConfig.defaults());
    }

    private RenderingContext rc(Page page, TestFetcher fetcher, ExportConfig export) {
        return new RenderingContext(page, export, fetcher,
                new TemplateVars(new FilenameSanitizer(export)), null);
    }

    private static Space space(String key, String name, Long homepage) {
        return new Space(BASE_URL, de.skerkewitz.jcme.model.SpaceKey.of(key), name, "", homepage);
    }

    private static Page makePage(long id, String title, String body, Space space) {
        return new Page(BASE_URL, PageId.of(id), title, space, List.of(), Version.empty(),
                body, "", "", List.of(), List.of());
    }

    private String convert(Page page, String html) {
        return convert(page, html, new TestFetcher());
    }

    private String convert(Page page, String html, TestFetcher fetcher) {
        ConfluencePageConverter c = new ConfluencePageConverter(rc(page, fetcher));
        return c.convert(html);
    }

    // ---------- Alerts ----------

    @Test
    void info_macro_renders_as_important_alert() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<div data-macro-name=\"info\"><p>be careful</p></div>");
        assertThat(md).contains("> [!IMPORTANT]");
        assertThat(md).contains("> be careful");
    }

    @Test
    void warning_macro_renders_as_caution() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<div data-macro-name=\"warning\">danger</div>");
        assertThat(md).contains("> [!CAUTION]");
    }

    @Test
    void note_macro_renders_as_warning_alert() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<div data-macro-name=\"note\"><p>heads-up</p></div>");
        assertThat(md).contains("> [!WARNING]");
    }

    // ---------- Page properties → front matter ----------

    @Test
    void details_macro_collects_properties_and_emits_no_inline_content() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        TestFetcher fetcher = new TestFetcher();
        ConfluencePageConverter c = new ConfluencePageConverter(rc(p, fetcher));
        String md = c.convert("""
                <div data-macro-name="details">
                  <table><tbody>
                    <tr><th>Status</th><td>Active</td></tr>
                    <tr><th>Owner</th><td>Alice</td></tr>
                  </tbody></table>
                </div>
                """);
        assertThat(md.trim()).isEmpty();
        assertThat(c.context().pageProperties())
                .containsEntry("status", "Active")
                .containsEntry("owner", "Alice");
    }

    @Test
    void details_macro_disabled_renders_inline() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        ExportConfig disabled = withFrontMatter(false);
        TestFetcher fetcher = new TestFetcher();
        ConfluencePageConverter c = new ConfluencePageConverter(rc(p, fetcher, disabled));
        String md = c.convert("""
                <div data-macro-name="details">
                  <table><tr><th>Status</th><td>Active</td></tr></table>
                </div>
                """);
        assertThat(md).contains("Status");
        assertThat(c.context().pageProperties()).isEmpty();
    }

    // ---------- Pre with brush ----------

    @Test
    void pre_with_syntax_brush_emits_language() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<pre data-syntaxhighlighter-params=\"brush: java;\"><code>int x;</code></pre>");
        assertThat(md).contains("```java");
        assertThat(md).contains("int x;");
    }

    // ---------- Page link ----------

    @Test
    void page_link_resolves_via_fetcher() {
        Space s = space("K", "K", null);
        Page parent = makePage(100, "Parent", "", s);
        Page target = makePage(200, "Child Page", "", s);
        TestFetcher fetcher = new TestFetcher();
        fetcher.pages.put(target.id(), target);

        String md = convert(parent,
                "<a href=\"/wiki/spaces/K/pages/200/Child\" data-linked-resource-type=\"page\" "
                        + "data-linked-resource-id=\"200\">Child</a>",
                fetcher);

        assertThat(md).contains("[Child Page]");
        assertThat(md).contains(".md");
    }

    @Test
    void page_link_to_inaccessible_page_falls_back() {
        Space s = space("K", "K", null);
        Page parent = makePage(100, "Parent", "", s);
        TestFetcher fetcher = new TestFetcher();

        String md = convert(parent,
                "<a href=\"x\" data-linked-resource-type=\"page\" data-linked-resource-id=\"999\">Missing</a>",
                fetcher);

        assertThat(md).contains("[Page not accessible (ID: 999)]");
    }

    @Test
    void wiki_style_page_link_uses_double_brackets() {
        Space s = space("K", "K", null);
        Page parent = makePage(100, "Parent", "", s);
        Page target = makePage(200, "Other", "", s);
        TestFetcher fetcher = new TestFetcher();
        fetcher.pages.put(PageId.of(200L), target);

        ExportConfig wiki = withPageHref("wiki");
        ConfluencePageConverter c = new ConfluencePageConverter(rc(parent, fetcher, wiki));
        String md = c.convert(
                "<a data-linked-resource-type=\"page\" data-linked-resource-id=\"200\">x</a>");

        assertThat(md).contains("[[Other]]");
    }

    @Test
    void anchor_link_uses_github_slug() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<a href=\"#Some Heading\">Jump</a>");
        assertThat(md).isEqualTo("[Jump](#some-heading)");
    }

    // ---------- Attachment image / link ----------

    @Test
    void attachment_image_uses_file_id_lookup() {
        Space s = space("K", "K", null);
        Attachment a = new Attachment(BASE_URL, "att1", "diag.png", s, List.of(),
                Version.empty(), 0, "image/png", "", "guid-1", "", "/dl", "");
        Page p = new Page(BASE_URL, PageId.of(1), "P", s, List.of(), Version.empty(),
                "", "", "", List.of(), List.of(a));

        String md = convert(p, "<img data-media-id=\"guid-1\" alt=\"diagram\" src=\"x.png\">");

        assertThat(md).contains("![diagram]");
        assertThat(md).contains("guid-1.png");
    }

    @Test
    void unresolved_attachment_link_falls_back_to_href() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p,
                "<a href=\"http://example.com/file.pdf\" data-linked-resource-type=\"attachment\" "
                        + "data-linked-resource-id=\"unknown\">file</a>");
        assertThat(md).contains("[file](http://example.com/file.pdf)");
    }

    // ---------- Emoticon ----------

    @Test
    void emoticon_with_data_emoji_id_decodes_to_unicode() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<img class=\"emoticon\" data-emoji-id=\"1f60a\">");
        assertThat(md).isEqualTo("😊");
    }

    @Test
    void emoticon_falls_back_to_atlassian_map() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p,
                "<img class=\"emoticon\" data-emoji-id=\"atlassian-check_mark\">");
        assertThat(md).isEqualTo("✅");
    }

    // ---------- Task list ----------

    @Test
    void inline_task_renders_as_checkbox() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p,
                "<ul><li data-inline-task-id=\"1\">Open task</li>"
                        + "<li data-inline-task-id=\"2\" class=\"checked\">Done</li></ul>");
        assertThat(md).contains("- [ ] Open task");
        assertThat(md).contains("- [x] Done");
    }

    // ---------- Time ----------

    @Test
    void time_uses_datetime_attribute() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<time datetime=\"2024-01-15\">January 15</time>");
        assertThat(md).isEqualTo("2024-01-15");
    }

    // ---------- Hidden content ----------

    @Test
    void scroll_ignore_macro_emits_html_comment() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<div data-macro-name=\"scroll-ignore\"><p>secret</p></div>");
        assertThat(md).contains("<!--");
        assertThat(md).contains("secret");
        assertThat(md).contains("-->");
    }

    @Test
    void macros_to_ignore_produce_empty_output() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p,
                "<div data-macro-name=\"qc-read-and-understood-signature-box\">x</div>");
        assertThat(md.trim()).isEmpty();
    }

    // ---------- Expand container ----------

    @Test
    void expand_container_renders_as_details_summary() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p,
                "<div class=\"expand-container\">"
                        + "<span class=\"expand-control-text\">Click to expand</span>"
                        + "<div class=\"expand-content\"><p>hidden body</p></div>"
                        + "</div>");
        assertThat(md).contains("<details>");
        assertThat(md).contains("<summary>Click to expand</summary>");
        assertThat(md).contains("hidden body");
        assertThat(md).contains("</details>");
    }

    // ---------- Jira issue (span) ----------

    @Test
    void jira_issue_span_uses_summary_when_available() {
        Space s = space("K", "K", null);
        Page p = makePage(1, "P", "", s);
        TestFetcher fetcher = new TestFetcher();
        fetcher.jiraIssues.put("PROJ-1", new JiraIssue(de.skerkewitz.jcme.model.IssueKey.of("PROJ-1"), "Fix bug", "", "Open"));

        String md = convert(p,
                "<span data-macro-name=\"jira\" data-jira-key=\"PROJ-1\">"
                        + "<a class=\"jira-issue-key\" href=\"https://j.com/browse/PROJ-1\">PROJ-1</a></span>",
                fetcher);

        assertThat(md).isEqualTo("[[PROJ-1] Fix bug](https://j.com/browse/PROJ-1)");
    }

    @Test
    void jira_issue_falls_back_when_lookup_returns_empty() {
        Space s = space("K", "K", null);
        Page p = makePage(1, "P", "", s);
        TestFetcher fetcher = new TestFetcher();

        String md = convert(p,
                "<span data-macro-name=\"jira\" data-jira-key=\"PROJ-2\">"
                        + "<a class=\"jira-issue-key\" href=\"https://j.com/browse/PROJ-2\">PROJ-2</a></span>",
                fetcher);

        assertThat(md).contains("PROJ-2");
        assertThat(md).contains("https://j.com/browse/PROJ-2");
    }

    // ---------- User mention ----------

    @Test
    void user_mention_resolves_via_account_id() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        TestFetcher fetcher = new TestFetcher();
        fetcher.users.put("acc-1", new User("acc-1", "alice", "Alice Smith", "alice", ""));

        String md = convert(p,
                "<a class=\"user-mention\" data-account-id=\"acc-1\">@alice</a>", fetcher);

        assertThat(md).isEqualTo("Alice Smith");
    }

    @Test
    void user_mention_strips_unlicensed_suffix_on_fallback() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        TestFetcher fetcher = new TestFetcher();

        String md = convert(p,
                "<a class=\"user-mention\">Bob (Unlicensed)</a>", fetcher);

        assertThat(md.trim()).isEqualTo("Bob");
    }

    // ---------- Sup as footnote ----------

    @Test
    void sup_first_in_paragraph_is_footnote_definition() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<p><sup>1</sup> footnote text</p>");
        assertThat(md).contains("[^1]:");
    }

    @Test
    void sup_after_text_is_footnote_reference() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<p>see this<sup>2</sup></p>");
        assertThat(md).contains("[^2]");
        assertThat(md).doesNotContain("[^2]:");
    }

    // ---------- Plain HTML still works ----------

    @Test
    void unknown_div_falls_through_to_default() {
        Page p = makePage(1, "P", "", space("K", "K", null));
        String md = convert(p, "<div><p>simple</p></div>");
        assertThat(md).isEqualTo("simple");
    }

    // ---------- Helpers ----------

    private static ExportConfig withFrontMatter(boolean enabled) {
        ExportConfig d = ExportConfig.defaults();
        return new ExportConfig(d.logLevel(), d.outputPath(), d.pageHref(), d.pagePath(),
                d.attachmentHref(), d.attachmentPath(), d.attachmentExportAll(),
                d.pageBreadcrumbs(), enabled, d.filenameEncoding(), d.filenameLength(),
                d.filenameLowercase(), d.includeDocumentTitle(), d.enableJiraEnrichment(),
                d.skipUnchanged(), d.cleanupStale(), d.lockfileName(), d.existenceCheckBatchSize());
    }

    private static ExportConfig withPageHref(String href) {
        ExportConfig d = ExportConfig.defaults();
        return new ExportConfig(d.logLevel(), d.outputPath(), href, d.pagePath(),
                d.attachmentHref(), d.attachmentPath(), d.attachmentExportAll(),
                d.pageBreadcrumbs(), d.pagePropertiesAsFrontMatter(), d.filenameEncoding(),
                d.filenameLength(), d.filenameLowercase(), d.includeDocumentTitle(),
                d.enableJiraEnrichment(), d.skipUnchanged(), d.cleanupStale(),
                d.lockfileName(), d.existenceCheckBatchSize());
    }

    @SuppressWarnings("unused")
    private static Ancestor ancestor(long id, String title) {
        return new Ancestor(BASE_URL, PageId.of(id), title, Space.empty(BASE_URL), List.of(), Version.empty());
    }

    @SuppressWarnings("unused")
    private static Label label(String name) {
        return new Label("1", name, "global");
    }
}
