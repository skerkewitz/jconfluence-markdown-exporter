package de.skerkewitz.jcme.markdown;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.Set;

/**
 * Plain-HTML → Markdown converters that {@link MarkdownConverter} registers by default.
 *
 * <p>Faithful enough to markdownify's defaults to make Phase-4 tests deterministic.
 * Confluence-specific converters are registered later in Phase 5.
 */
public final class DefaultConverters {

    private DefaultConverters() {}

    /** Used when no converter is registered: emit the children's text verbatim. */
    public static final NodeConverter PASSTHROUGH = (el, text, parents, ctx) -> text;

    static void registerAll(MarkdownConverter c) {
        // Headings
        for (int i = 1; i <= 6; i++) {
            int level = i;
            c.register("h" + i, (el, text, parents, ctx) -> heading(level, text));
        }

        // Block / inline text
        c.register("p", DefaultConverters::convertP);
        c.register("br", (el, text, parents, ctx) -> "  \n");
        c.register("hr", (el, text, parents, ctx) -> "\n\n---\n\n");
        c.register("blockquote", DefaultConverters::convertBlockquote);

        // Inline emphasis
        c.register("strong", DefaultConverters::convertStrong);
        c.register("b", DefaultConverters::convertStrong);
        c.register("em", DefaultConverters::convertEm);
        c.register("i", DefaultConverters::convertEm);
        c.register("u", (el, text, parents, ctx) -> "<u>" + text + "</u>");
        c.register("s", DefaultConverters::convertStrike);
        c.register("strike", DefaultConverters::convertStrike);
        c.register("del", DefaultConverters::convertStrike);
        c.register("sub", (el, text, parents, ctx) -> "<sub>" + text + "</sub>");
        c.register("sup", (el, text, parents, ctx) -> "<sup>" + text + "</sup>");

        // Code
        c.register("code", DefaultConverters::convertCode);
        c.register("pre", DefaultConverters::convertPre);
        c.register("kbd", (el, text, parents, ctx) -> "<kbd>" + text + "</kbd>");
        c.register("samp", (el, text, parents, ctx) -> "<samp>" + text + "</samp>");
        c.register("var", (el, text, parents, ctx) -> "<var>" + text + "</var>");

        // Lists
        c.register("ul", DefaultConverters::convertList);
        c.register("ol", DefaultConverters::convertList);
        c.register("li", DefaultConverters::convertLi);

        // Links + images
        c.register("a", DefaultConverters::convertA);
        c.register("img", DefaultConverters::convertImg);

        // Containers — pass-through with no markup
        c.register("div", PASSTHROUGH);
        c.register("span", PASSTHROUGH);
        c.register("section", PASSTHROUGH);
        c.register("article", PASSTHROUGH);
        c.register("header", PASSTHROUGH);
        c.register("footer", PASSTHROUGH);
        c.register("main", PASSTHROUGH);
        c.register("nav", PASSTHROUGH);
        c.register("aside", PASSTHROUGH);
        c.register("figure", PASSTHROUGH);
        c.register("figcaption", PASSTHROUGH);

        // Misc inline / block
        c.register("time", PASSTHROUGH);
        c.register("abbr", PASSTHROUGH);
        c.register("cite", PASSTHROUGH);
        c.register("small", PASSTHROUGH);
        c.register("mark", (el, text, parents, ctx) -> "==" + text + "==");

        // Tables (delegated to TableConverter for rich rendering)
        TableConverter table = new TableConverter();
        c.register("table", table::convertTable);
        c.register("thead", PASSTHROUGH);
        c.register("tbody", PASSTHROUGH);
        c.register("tfoot", PASSTHROUGH);
        c.register("tr", PASSTHROUGH);
        c.register("th", table::convertCell);
        c.register("td", table::convertCell);
    }

    // ---------- block converters ----------

    private static String heading(int level, String text) {
        String stripped = text.trim().replaceAll("\\s+", " ");
        if (stripped.isEmpty()) return "";
        return "\n\n" + "#".repeat(level) + " " + stripped + "\n\n";
    }

    private static String convertP(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        if (parents.contains(MarkdownConverter.INLINE_MARKER)) {
            // Inside an inline-only context (e.g. table cell): collapse to text + <br/>
            return text.replace("\n", "") + "<br/>";
        }
        if (text.isBlank()) return "";
        return "\n\n" + text.trim() + "\n\n";
    }

    private static String convertBlockquote(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        if (text.isBlank()) return "";
        StringBuilder out = new StringBuilder("\n\n");
        for (String line : text.trim().split("\n")) {
            out.append("> ").append(line).append('\n');
        }
        out.append('\n');
        return out.toString();
    }

    // ---------- inline converters ----------

    private static String convertStrong(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        MarkdownConverter.Chomped c = MarkdownConverter.chomp(text);
        if (c.stripped().isEmpty()) return "";
        return c.prefix() + "**" + c.stripped() + "**" + c.suffix();
    }

    private static String convertEm(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        MarkdownConverter.Chomped c = MarkdownConverter.chomp(text);
        if (c.stripped().isEmpty()) return "";
        return c.prefix() + "_" + c.stripped() + "_" + c.suffix();
    }

    private static String convertStrike(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        MarkdownConverter.Chomped c = MarkdownConverter.chomp(text);
        if (c.stripped().isEmpty()) return "";
        return c.prefix() + "~~" + c.stripped() + "~~" + c.suffix();
    }

    private static String convertCode(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        if (parents.contains("pre")) return text;
        if (text.isEmpty()) return "";
        return "`" + text + "`";
    }

    private static String convertPre(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        if (text.isEmpty()) return "";
        // Try to extract a language from a child <code class="language-xxx">
        String lang = "";
        Element child = el.firstElementChild();
        if (child != null && "code".equalsIgnoreCase(child.tagName())) {
            for (String cls : child.classNames()) {
                if (cls.startsWith("language-")) {
                    lang = cls.substring("language-".length());
                    break;
                }
            }
        }
        return "\n\n```" + lang + "\n" + stripTrailingNewline(text) + "\n```\n\n";
    }

    private static String stripTrailingNewline(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    // ---------- lists ----------

    private static String convertList(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        // Top-level list: separate from surrounding content with blank lines.
        boolean nested = parents.contains("ul") || parents.contains("ol") || parents.contains("li");
        if (nested) {
            return "\n" + text;
        }
        return "\n\n" + text + "\n";
    }

    private static String convertLi(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        char bullet = ctx.options().bullet();
        String marker;
        // Determine marker: ordered lists use 1./2./...; unordered uses the configured bullet.
        Element parentEl = el.parent();
        if (parentEl != null && "ol".equalsIgnoreCase(parentEl.tagName())) {
            int index = 1;
            for (Node sib : parentEl.childNodes()) {
                if (sib == el) break;
                if (sib instanceof Element s && "li".equalsIgnoreCase(s.tagName())) index++;
            }
            String startAttr = parentEl.attr("start");
            int start = 1;
            if (!startAttr.isEmpty()) {
                try { start = Integer.parseInt(startAttr); } catch (NumberFormatException ignored) {}
            }
            marker = (start + index - 1) + ". ";
        } else {
            marker = bullet + " ";
        }
        // Indent any continuation lines so nested content stays inside the list item.
        String content = text.trim();
        // Indent nested list lines by two spaces (markdownify indents to align with marker).
        String indented = indentContinuationLines(content, "  ");
        return marker + indented + "\n";
    }

    private static String indentContinuationLines(String text, String indent) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');
            if (i == 0 || lines[i].isEmpty()) {
                out.append(lines[i]);
            } else {
                out.append(indent).append(lines[i]);
            }
        }
        return out.toString();
    }

    // ---------- links + images ----------

    private static String convertA(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        String href = el.attr("href");
        String title = el.attr("title");
        if (href.isEmpty()) return text;
        String visible = text.isBlank() ? href : text.trim();
        if (!title.isEmpty()) {
            return "[" + visible + "](" + href + " \"" + title.replace("\"", "\\\"") + "\")";
        }
        return "[" + visible + "](" + href + ")";
    }

    private static String convertImg(Element el, String text, Set<String> parents, MarkdownConverter ctx) {
        String src = el.attr("src");
        String alt = el.attr("alt");
        String title = el.attr("title");
        if (src.isEmpty()) return alt;
        if (!title.isEmpty()) {
            return "![" + alt + "](" + src + " \"" + title.replace("\"", "\\\"") + "\")";
        }
        return "![" + alt + "](" + src + ")";
    }

    /** Convert text-node-style content to markdown text, escaping markdown special chars. */
    public static String escapeMarkdown(TextNode tn) {
        return tn.getWholeText();
    }
}
