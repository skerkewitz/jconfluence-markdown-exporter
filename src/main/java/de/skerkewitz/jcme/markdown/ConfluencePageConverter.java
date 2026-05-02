package de.skerkewitz.jcme.markdown;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.api.UrlParsing;
import de.skerkewitz.jcme.api.exceptions.ApiException;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.JiraIssue;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.PageId;
import de.skerkewitz.jcme.model.User;
import de.skerkewitz.jcme.utils.DrawioMermaid;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown converter that adds Confluence-specific behaviour on top of the plain
 * {@link MarkdownConverter}: macro handling (panels, drawio, plantuml, attachments,
 * jira, toc, markdown), page/attachment link resolution, user mentions, emoticons,
 * task lists, code-with-brush, and the page-properties → front-matter pipeline.
 */
public class ConfluencePageConverter extends MarkdownConverter {

    private static final Logger LOG = LoggerFactory.getLogger(ConfluencePageConverter.class);

    private static final Pattern BRUSH_PATTERN = Pattern.compile("brush:\\s*([^;]+)");
    private static final Pattern DIAGRAM_NAME_PATTERN = Pattern.compile("\\|diagramName=(.+?)\\|");

    private static final Set<String> ALERT_MACROS =
            Set.of("panel", "info", "note", "tip", "warning");

    private static final Map<String, String> ALERT_TYPE_MAP = Map.ofEntries(
            Map.entry("info", "IMPORTANT"),
            Map.entry("panel", "NOTE"),
            Map.entry("tip", "TIP"),
            Map.entry("note", "WARNING"),
            Map.entry("warning", "CAUTION")
    );

    private static final Set<String> MACROS_TO_IGNORE = Set.of("qc-read-and-understood-signature-box");

    private final RenderingContext ctx;

    public ConfluencePageConverter(RenderingContext ctx) {
        super();
        this.ctx = ctx;
        registerOverrides();
    }

    private void registerOverrides() {
        register("div", this::convertDiv);
        register("span", this::convertSpan);
        register("a", this::convertA);
        register("img", this::convertImg);
        register("li", this::convertLi);
        register("pre", this::convertPre);
        register("sub", (el, text, parents, c) -> "<sub>" + text + "</sub>");
        register("sup", this::convertSup);
        register("time", this::convertTime);
        register("table", this::convertTable);
    }

    public RenderingContext context() { return ctx; }

    // ---------- div ----------

    private String convertDiv(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        String macro = el.attr("data-macro-name").toLowerCase(Locale.ROOT);
        if (!macro.isEmpty()) {
            if (MACROS_TO_IGNORE.contains(macro)) return "";
            if (ALERT_MACROS.contains(macro)) return convertAlert(el, text, parents, macro);
            switch (macro) {
                case "details": return convertPageProperties(el, text);
                case "drawio": return convertDrawio(el);
                case "plantuml": return convertPlantuml(el);
                case "scroll-ignore": return convertHidden(text);
                case "toc": return convertToc(el, parents);
                case "jira": return convertJiraTable(parents);
                case "attachments": return convertAttachmentsList(el, text, parents);
                case "markdown":
                case "mohamicorp-markdown": return convertMarkdownMacro(el);
                default: break;
            }
        }
        String classes = el.attr("class");
        if (classes.contains("expand-container")) return convertExpandContainer(el, parents);
        if (classes.contains("columnLayout")) return convertColumnLayout(el, text, parents);
        return text;
    }

    // ---------- span ----------

    private String convertSpan(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        if ("jira".equalsIgnoreCase(el.attr("data-macro-name"))) {
            return convertJiraIssue(el, text);
        }
        return text;
    }

    // ---------- a (links) ----------

    private String convertA(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        String classes = el.attr("class");
        String href = el.attr("href");
        if (classes.contains("user-mention")) {
            return convertUserMention(el, text);
        }
        if (href.contains("createpage.action") || classes.contains("createlink")) {
            LOG.warn("Broken link detected: '{}' on page '{}' (ID: {})", text, ctx.page().title(), ctx.page().id());
            return "[[" + text + "]]";
        }
        if ("page".equalsIgnoreCase(el.attr("data-linked-resource-type"))) {
            String pid = el.attr("data-linked-resource-id");
            if (!pid.isEmpty() && !"null".equals(pid)) {
                PageId parsed = parsePageId(pid);
                if (parsed != null) return convertPageLink(parsed);
            }
        }
        if ("attachment".equalsIgnoreCase(el.attr("data-linked-resource-type"))) {
            String link = convertAttachmentLink(el, text);
            if (link != null) return link;
        }
        Optional<Attachment> byHref = findAttachmentByUrl(href);
        if (byHref.isPresent()) {
            return renderAttachmentLink(byHref.get(), text);
        }
        Optional<de.skerkewitz.jcme.api.ConfluenceRef> ref = UrlParsing.parseConfluencePath(href);
        if (ref.isPresent() && ref.get().pageId() != null) {
            return convertPageLink(ref.get().pageId());
        }
        if (href.startsWith("#")) {
            String anchor = decode(href.substring(1));
            if (ctx.pageHref() == HrefResolver.Style.WIKI) {
                return "[[#" + text + "]]";
            }
            return "[" + text + "](#" + HeadingSlugger.slug(anchor) + ")";
        }
        // Fallback to default link rendering
        return defaultConvertA(href, text, el.attr("title"));
    }

    private String defaultConvertA(String href, String text, String title) {
        if (href.isEmpty()) return text;
        String visible = text.isBlank() ? href : text.trim();
        if (!title.isEmpty()) {
            return "[" + visible + "](" + href + " \"" + title.replace("\"", "\\\"") + "\")";
        }
        return "[" + visible + "](" + href + ")";
    }

    private String convertPageLink(PageId pageId) {
        Page target;
        try {
            target = ctx.fetcher().getPage(pageId, ctx.page().baseUrl());
        } catch (ApiException e) {
            return "[Page not accessible (ID: " + pageId + ")]";
        }
        if (target.isInaccessible()) {
            LOG.warn("Confluence page link (ID: {}) is not accessible, referenced from '{}' (ID: {})",
                    pageId, ctx.page().title(), ctx.page().id());
            return "[Page not accessible (ID: " + pageId + ")]";
        }
        if (ctx.pageHref() == HrefResolver.Style.WIKI) {
            return "[[" + target.title() + "]]";
        }
        Path targetPath = ctx.pageExportPath(target);
        Path currentPath = ctx.pageExportPath(ctx.page());
        String href = HrefResolver.encodeSpaces(
                HrefResolver.resolve(targetPath, currentPath, ctx.pageHref()));
        return "[" + target.title() + "](" + href + ")";
    }

    private String convertAttachmentLink(Element el, String text) {
        Optional<Attachment> attachment = findReferencedAttachment(el);
        if (attachment.isEmpty()) {
            attachment = findAttachmentByUrl(el.attr("href"));
        }
        if (attachment.isEmpty()) return null;
        return renderAttachmentLink(attachment.get(), text);
    }

    private String renderAttachmentLink(Attachment a, String text) {
        String title = text == null || text.isBlank() ? a.title() : text;
        if (ctx.attachmentHref() == HrefResolver.Style.WIKI) {
            return "[[" + ctx.attachmentExportPath(a).getFileName() + "|" + title + "]]";
        }
        Path target = ctx.attachmentExportPath(a);
        Path current = ctx.pageExportPath(ctx.page());
        String href = HrefResolver.encodeSpaces(HrefResolver.resolve(target, current, ctx.attachmentHref()));
        return "[" + title + "](" + href + ")";
    }

    private Optional<Attachment> findReferencedAttachment(Element el) {
        for (String attr : List.of("data-linked-resource-file-id", "data-media-id")) {
            String fid = el.attr(attr);
            if (!fid.isEmpty()) {
                Optional<Attachment> a = ctx.page().attachmentByFileId(fid);
                if (a.isPresent()) return a;
            }
        }
        String aid = el.attr("data-linked-resource-id");
        if (!aid.isEmpty()) return ctx.page().attachmentById(aid);
        return Optional.empty();
    }

    // ---------- img ----------

    private String convertImg(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        String emoticon = convertEmoticon(el);
        if (emoticon != null) return emoticon;

        Optional<Attachment> attachment = findReferencedAttachment(el);
        String src = el.attr("src");
        if (attachment.isEmpty()) {
            attachment = findAttachmentByUrl(src);
        }

        if (src.contains(".drawio.png")) {
            String filename = decode(src.substring(src.lastIndexOf('/') + 1));
            String mermaid = readDrawioMermaid(filename);
            if (mermaid != null) return mermaid;
            if (attachment.isEmpty()) {
                List<Attachment> drawioImages = ctx.page().attachmentsByTitle(filename);
                if (!drawioImages.isEmpty()) attachment = Optional.of(drawioImages.get(0));
            }
        }

        if (attachment.isEmpty()) {
            String href = el.hasAttr("href") ? el.attr("href") : "";
            String alt = el.attr("alt");
            if (!href.isEmpty()) return "![" + alt + "](" + href + ")";
            if (!src.isEmpty()) return "![" + alt + "](" + src + ")";
            return alt;
        }

        Attachment a = attachment.get();
        String alt = el.attr("alt").isEmpty() ? text : el.attr("alt");
        if (ctx.attachmentHref() == HrefResolver.Style.WIKI) {
            String suffix = alt == null || alt.isBlank() ? "" : "|" + alt.strip();
            return "![[" + ctx.attachmentExportPath(a).getFileName() + suffix + "]]";
        }
        Path target = ctx.attachmentExportPath(a);
        Path current = ctx.pageExportPath(ctx.page());
        String href = HrefResolver.encodeSpaces(HrefResolver.resolve(target, current, ctx.attachmentHref()));
        return "![" + (alt == null ? "" : alt) + "](" + href + ")";
    }

    private Optional<Attachment> findAttachmentByUrl(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        String decoded = decode(url);
        String normalizedUrl = stripQueryAndFragment(decoded);
        for (Attachment a : ctx.page().attachments()) {
            String dl = stripQueryAndFragment(decode(a.downloadLink()));
            if (!dl.isEmpty() && normalizedUrl.contains(dl)) {
                return Optional.of(a);
            }
            int slash = normalizedUrl.lastIndexOf('/');
            String lastSegment = slash >= 0 ? normalizedUrl.substring(slash + 1) : normalizedUrl;
            if (!a.title().isEmpty() && decode(lastSegment).equals(a.title())) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    private static String stripQueryAndFragment(String s) {
        if (s == null) return "";
        int q = s.indexOf('?');
        String base = q >= 0 ? s.substring(0, q) : s;
        int hash = base.indexOf('#');
        return hash >= 0 ? base.substring(0, hash) : base;
    }

    private String convertEmoticon(Element el) {
        if (!el.classNames().contains("emoticon")) return null;
        String emojiId = el.attr("data-emoji-id");
        String fallback = el.attr("data-emoji-fallback");
        if (!fallback.isEmpty() && !fallback.startsWith(":")) return fallback;
        if (!emojiId.isEmpty()) {
            String decoded = EmoticonMap.decodeEmojiId(emojiId);
            if (decoded != null && !decoded.isEmpty()) return decoded;
            String mapped = EmoticonMap.ATLASSIAN.get(emojiId);
            if (mapped != null) return mapped;
        }
        String shortname = el.attr("data-emoji-shortname");
        if (!shortname.isEmpty()) return shortname;
        if (!fallback.isEmpty()) return fallback;
        String alt = el.attr("alt");
        return alt.isEmpty() ? null : alt;
    }

    // ---------- li (task list) ----------

    private String convertLi(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        String md = defaultLi(el, text);
        if (el.hasAttr("data-inline-task-id")) {
            boolean checked = el.classNames().contains("checked");
            String box = checked ? "[x] " : "[ ] ";
            return md.replaceFirst("- ", "- " + box);
        }
        return md;
    }

    private String defaultLi(Element el, String text) {
        Element parent = el.parent();
        String marker;
        if (parent != null && "ol".equalsIgnoreCase(parent.tagName())) {
            int index = 1;
            for (Element sib : parent.children()) {
                if (sib == el) break;
                if ("li".equalsIgnoreCase(sib.tagName())) index++;
            }
            int start = 1;
            if (parent.hasAttr("start")) {
                try { start = Integer.parseInt(parent.attr("start")); } catch (NumberFormatException ignored) {}
            }
            marker = (start + index - 1) + ". ";
        } else {
            marker = options().bullet() + " ";
        }
        String content = text.trim();
        return marker + indentContinuation(content) + "\n";
    }

    private static String indentContinuation(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');
            if (i == 0 || lines[i].isEmpty()) out.append(lines[i]);
            else out.append("  ").append(lines[i]);
        }
        return out.toString();
    }

    // ---------- pre / sup / time / table ----------

    private String convertPre(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        if (text.isEmpty()) return "";
        String lang = "";
        if (el.hasAttr("data-syntaxhighlighter-params")) {
            Matcher m = BRUSH_PATTERN.matcher(el.attr("data-syntaxhighlighter-params"));
            if (m.find()) lang = m.group(1).trim();
        }
        if (lang.isEmpty()) {
            Element child = el.firstElementChild();
            if (child != null && "code".equalsIgnoreCase(child.tagName())) {
                for (String cls : child.classNames()) {
                    if (cls.startsWith("language-")) {
                        lang = cls.substring("language-".length());
                        break;
                    }
                }
            }
        }
        return "\n\n```" + lang + "\n" + stripTrailingNewline(text) + "\n```\n\n";
    }

    private String convertSup(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        if (el.previousSibling() == null) return "[^" + text + "]:";
        return "[^" + text + "]";
    }

    private String convertTime(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        if (el.hasAttr("datetime")) return el.attr("datetime");
        return text;
    }

    private String convertTable(Element el, String text, Set<String> parents, MarkdownConverter mc) {
        if (el.classNames().contains("metadata-summary-macro")) {
            return convertPagePropertiesReport(el, parents);
        }
        return new TableConverter().convertTable(el, text, parents, this);
    }

    // ---------- macro handlers ----------

    private String convertAlert(Element el, String text, Set<String> parents, String macro) {
        String type = ALERT_TYPE_MAP.getOrDefault(macro, "NOTE");
        // Render the inner content as a blockquote then re-wrap with the GH-alert tag on the first line.
        String inner = processChildren(el, parents).strip();
        if (inner.isEmpty()) return "";
        StringBuilder out = new StringBuilder("\n> [!").append(type).append("]\n");
        for (String line : inner.split("\n")) {
            out.append("> ").append(line).append('\n');
        }
        return out.toString();
    }

    private String convertPageProperties(Element el, String text) {
        if (!ctx.export().pagePropertiesAsFrontMatter()) return text;
        var rows = el.select("tr");
        Map<String, String> props = new LinkedHashMap<>();
        for (Element tr : rows) {
            var cells = tr.select("th, td");
            if (cells.size() == 2) {
                String key = cells.get(0).text().trim();
                String value = convertElement(cells.get(1)).trim();
                props.put(key, value);
            }
        }
        if (props.isEmpty()) return "";
        for (Map.Entry<String, String> e : props.entrySet()) {
            ctx.pageProperties().put(sanitizeKey(e.getKey()), e.getValue());
        }
        return "";
    }

    static String sanitizeKey(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9_]", "_");
        s = s.replaceAll("_+", "_");
        s = stripChar(s, '_');
        if (s.isEmpty()) return "key";
        char first = s.charAt(0);
        if (first < 'a' || first > 'z') s = "key_" + s;
        return s;
    }

    private static String stripChar(String s, char c) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == c) start++;
        while (end > start && s.charAt(end - 1) == c) end--;
        return s.substring(start, end);
    }

    private String convertHidden(String text) {
        // markdownify-style convert_p produced text without the surrounding <p>.
        return "\n<!--" + text + "-->\n";
    }

    private String convertExpandContainer(Element el, Set<String> parents) {
        Element summaryEl = el.selectFirst("span.expand-control-text");
        String summary = summaryEl != null ? summaryEl.text().strip() : "Click here to expand...";
        Element content = el.selectFirst("div.expand-content");
        String body = content == null ? "" : processChildren(content, parents).strip();
        return "\n<details>\n<summary>" + summary + "</summary>\n\n" + body + "\n\n</details>\n\n";
    }

    private String convertColumnLayout(Element el, String text, Set<String> parents) {
        var cells = el.select("div.cell");
        if (cells.size() < 2) return text;
        StringBuilder html = new StringBuilder("<table><tr>");
        for (Element cell : cells) {
            html.append("<td>").append(cell.html()).append("</td>");
        }
        html.append("</tr></table>");
        Element table = Jsoup.parse(html.toString(), "", Parser.htmlParser()).selectFirst("table");
        return new TableConverter().convertTable(table, "", parents, this);
    }

    private String convertJiraIssue(Element el, String text) {
        String rawKey = el.attr("data-jira-key");
        Element link = el.selectFirst("a.jira-issue-key");
        if (link == null) return text;
        String href = link.attr("href");
        Optional<de.skerkewitz.jcme.model.IssueKey> issueKeyOpt =
                de.skerkewitz.jcme.model.IssueKey.tryParse(rawKey);
        if (issueKeyOpt.isEmpty()) {
            return processChildren(link, Set.of());
        }
        de.skerkewitz.jcme.model.IssueKey issueKey = issueKeyOpt.get();
        try {
            String jiraBase = UrlParsing.extractJiraBaseUrl(href);
            de.skerkewitz.jcme.api.BaseUrl jiraBaseUrl = jiraBase == null
                    ? ctx.page().baseUrl()
                    : de.skerkewitz.jcme.api.BaseUrl.of(jiraBase);
            Optional<JiraIssue> issue = ctx.fetcher().getJiraIssue(issueKey, jiraBaseUrl);
            if (issue.isEmpty()) return "[[" + issueKey + "](" + href + ")";
            return "[[" + issue.get().key() + "] " + issue.get().summary() + "](" + href + ")";
        } catch (ApiException e) {
            return "[[" + issueKey + "](" + href + ")";
        }
    }

    private String convertJiraTable(Set<String> parents) {
        return findInBodyExport("div.jira-table", parents).orElse("");
    }

    private String convertToc(Element el, Set<String> parents) {
        return findInBodyExport("div.toc-macro", parents).orElse("");
    }

    private Optional<String> findInBodyExport(String selector, Set<String> parents) {
        if (ctx.page().bodyExport() == null || ctx.page().bodyExport().isEmpty()) return Optional.empty();
        var doc = Jsoup.parse(ctx.page().bodyExport(), "", Parser.htmlParser());
        var elements = doc.select(selector);
        if (elements.isEmpty()) return Optional.empty();
        if (elements.size() > 1) {
            LOG.warn("Multiple {} matches in body_export — using the first.", selector);
        }
        return Optional.of(processChildren(elements.first(), parents));
    }

    private String convertAttachmentsList(Element el, String text, Set<String> parents) {
        Element fileHeader = el.selectFirst("th.filename-column");
        Element modifiedHeader = el.selectFirst("th.modified-column");
        String fileHdr = fileHeader != null ? fileHeader.text().strip() : "File";
        String modifiedHdr = modifiedHeader != null ? modifiedHeader.text().strip() : "Modified";

        StringBuilder html = new StringBuilder("<table>");
        html.append("<tr><th>").append(fileHdr).append("</th><th>").append(modifiedHdr).append("</th></tr>");
        for (Attachment a : ctx.page().attachments()) {
            html.append("<tr><td>").append(attachmentRow(a)).append("</td>")
                .append("<td>").append(a.version().friendlyWhen()).append(" by ")
                .append(displayUser(a.version().by())).append("</td></tr>");
        }
        html.append("</table>");
        Element table = Jsoup.parse(html.toString(), "", Parser.htmlParser()).selectFirst("table");
        return "\n\n" + new TableConverter().convertTable(table, "", parents, this) + "\n";
    }

    private String attachmentRow(Attachment a) {
        if (ctx.attachmentHref() == HrefResolver.Style.WIKI) {
            return "[[" + ctx.attachmentExportPath(a).getFileName() + "|" + a.title() + "]]";
        }
        Path target = ctx.attachmentExportPath(a);
        Path current = ctx.pageExportPath(ctx.page());
        String href = HrefResolver.encodeSpaces(HrefResolver.resolve(target, current, ctx.attachmentHref()));
        return "[" + a.title() + "](" + href + ")";
    }

    private String displayUser(User user) {
        return user.displayName().replaceAll("\\s*\\(Unlicensed\\)\\s*$", "")
                .replaceAll("\\s*\\(Deactivated\\)\\s*$", "").strip();
    }

    private String convertUserMention(Element el, String text) {
        String aid = el.attr("data-account-id");
        if (!aid.isEmpty()) {
            try {
                User u = ctx.fetcher().getUser(
                        new de.skerkewitz.jcme.model.UserIdentifier.AccountId(aid),
                        ctx.page().baseUrl());
                return displayUser(u);
            } catch (ApiException e) {
                LOG.warn("User {} not found. Using text instead.", aid);
            }
        }
        return text.replaceAll("\\s*\\(Unlicensed\\)\\s*$", "")
                .replaceAll("\\s*\\(Deactivated\\)\\s*$", "").strip();
    }

    private String convertDrawio(Element el) {
        Matcher m = DIAGRAM_NAME_PATTERN.matcher(el.outerHtml());
        if (!m.find()) return "";
        String name = m.group(1);
        String previewName = name + ".png";
        List<Attachment> diagram = ctx.page().attachmentsByTitle(name);
        List<Attachment> preview = ctx.page().attachmentsByTitle(previewName);
        if (diagram.isEmpty() || preview.isEmpty()) {
            return "\n<!-- Drawio diagram `" + name + "` not found -->\n\n";
        }
        if (ctx.attachmentHref() == HrefResolver.Style.WIKI) {
            String previewFile = ctx.attachmentExportPath(preview.get(0)).getFileName().toString();
            String diagramFile = ctx.attachmentExportPath(diagram.get(0)).getFileName().toString();
            String img = "![[" + previewFile + "|" + name + "]]";
            return "\n[[" + diagramFile + "|" + img + "]]\n\n";
        }
        Path current = ctx.pageExportPath(ctx.page());
        String diagramHref = HrefResolver.encodeSpaces(
                HrefResolver.resolve(ctx.attachmentExportPath(diagram.get(0)), current, ctx.attachmentHref()));
        String previewHref = HrefResolver.encodeSpaces(
                HrefResolver.resolve(ctx.attachmentExportPath(preview.get(0)), current, ctx.attachmentHref()));
        String img = "![" + name + "](" + previewHref + ")";
        return "\n[" + img + "](" + diagramHref + ")\n\n";
    }

    private String convertPlantuml(Element el) {
        if (ctx.page().editor2() == null || ctx.page().editor2().isEmpty()) {
            return "\n<!-- PlantUML diagram (editor2 unavailable) -->\n\n";
        }
        String macroId = el.attr("data-macro-id");
        if (macroId.isEmpty()) {
            return "\n<!-- PlantUML diagram (no macro-id found) -->\n\n";
        }
        var doc = Jsoup.parse("<root>" + ctx.page().editor2() + "</root>", "", Parser.xmlParser());
        for (Element macro : doc.select("structured-macro")) {
            if (!"plantuml".equalsIgnoreCase(macro.attr("name"))) continue;
            if (!macroId.equals(macro.attr("macro-id"))) continue;
            Element body = macro.selectFirst("plain-text-body");
            if (body == null) {
                return "\n<!-- PlantUML diagram (no content found) -->\n\n";
            }
            String json = body.text().trim();
            if (json.isEmpty()) {
                return "\n<!-- PlantUML diagram (empty content) -->\n\n";
            }
            try {
                JsonNode parsed = de.skerkewitz.jcme.api.RestClient.jsonMapper().readTree(json);
                String uml = parsed.path("umlDefinition").asText("");
                if (uml.isEmpty()) {
                    return "\n<!-- PlantUML diagram (no UML definition) -->\n\n";
                }
                return "\n```plantuml\n" + uml + "\n```\n\n";
            } catch (Exception e) {
                LOG.warn("Failed to parse PlantUML JSON for macro {}: {}", macroId, e.getMessage());
                return "\n<!-- PlantUML diagram (invalid JSON) -->\n\n";
            }
        }
        return "\n<!-- PlantUML diagram (not found in editor2) -->\n\n";
    }

    private String convertMarkdownMacro(Element el) {
        String name = el.attr("data-macro-name");
        String inline = extractMarkdownContent(el);
        if (inline == null && el.hasAttr("data-macro-id")) {
            inline = extractMarkdownFromEditor2(el.attr("data-macro-id"));
        }
        if (inline == null) {
            LOG.warn("Markdown macro ({}) found but no content could be extracted", name);
            return "\n<!-- Markdown macro (" + name + ") content not found -->\n\n";
        }
        return "\n" + inline + "\n\n";
    }

    private String extractMarkdownContent(Element el) {
        // Try plain-text-body first
        Element body = el.selectFirst("plain-text-body, [data-macro-body]");
        if (body != null) {
            String text = body.text();
            if (!text.isEmpty()) return text;
        }
        Element param = el.selectFirst("parameter[name=markdown], ac\\:parameter");
        if (param != null) {
            String text = param.text();
            if (!text.isEmpty()) return text;
        }
        return null;
    }

    private String extractMarkdownFromEditor2(String macroId) {
        if (ctx.page().editor2() == null || ctx.page().editor2().isEmpty()) return null;
        var doc = Jsoup.parse("<root>" + ctx.page().editor2() + "</root>", "", Parser.xmlParser());
        for (Element macro : doc.select("structured-macro")) {
            String name = macro.attr("name");
            if (!"markdown".equalsIgnoreCase(name) && !"mohamicorp-markdown".equalsIgnoreCase(name)) continue;
            if (!macroId.equals(macro.attr("macro-id"))) continue;
            Element ptb = macro.selectFirst("plain-text-body");
            if (ptb != null && !ptb.text().isEmpty()) return ptb.text().strip();
            Element param = macro.selectFirst("parameter[name=markdown]");
            if (param != null && !param.text().isEmpty()) return param.text().strip();
        }
        return null;
    }

    private String convertPagePropertiesReport(Element el, Set<String> parents) {
        String dataCql = el.attr("data-cql");
        if (dataCql.isEmpty()) return "";
        if (ctx.page().bodyExport() == null || ctx.page().bodyExport().isEmpty()) return "";
        var doc = Jsoup.parse(ctx.page().bodyExport(), "", Parser.htmlParser());
        Element table = doc.selectFirst("table[data-cql=\"" + dataCql + "\"]");
        if (table == null) return "";
        return new TableConverter().convertTable(table, "", parents, this);
    }

    // ---------- helpers ----------

    private String readDrawioMermaid(String filename) {
        if (ctx.drawioFilesRoot() == null) return null;
        for (Attachment a : ctx.page().attachmentsByTitle(filename.replaceFirst("\\.png$", ""))) {
            Path p = ctx.drawioFilesRoot().resolve(ctx.attachmentExportPath(a));
            if (Files.exists(p)) {
                Optional<String> mermaid = DrawioMermaid.extractMermaid(p);
                if (mermaid.isPresent()) return mermaid.get();
            }
        }
        return null;
    }

    private static String stripTrailingNewline(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    private static PageId parsePageId(String s) {
        try { return PageId.parse(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static String decode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); } catch (Exception e) { return s; }
    }

    /**
     * Internal use by macros that need to render a Confluence-flavored HTML fragment
     * with the same converter registry as the page itself.
     */
    String renderFragment(String html, Set<String> parents) {
        Element el = Jsoup.parse(html, "", Parser.htmlParser()).body();
        return processChildren(el, parents);
    }

    /** Re-fetch lazy parts of the macro that may need to look at other macros. */
    @SuppressWarnings("unused")
    private String dropdownContent(Element el, Set<String> parents) {
        return processChildren(el, parents);
    }

    /** Limit linter scope of unused-variable warnings on the imports. */
    @SuppressWarnings("unused")
    private void unusedImportShim() {
        new ArrayList<>();
    }
}
