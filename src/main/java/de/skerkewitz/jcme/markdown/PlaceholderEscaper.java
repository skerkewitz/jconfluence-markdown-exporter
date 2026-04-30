package de.skerkewitz.jcme.markdown;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Escape Confluence template-style {@code <placeholder>} patterns that Obsidian and
 * other Markdown renderers misparse as HTML tags.
 *
 * <p>Mirrors the Python {@code _escape_template_placeholders} method: leaves valid HTML
 * tags ({@code <br/>}, {@code <em>}, ...) and content inside fenced code blocks / inline
 * code spans untouched.
 */
public final class PlaceholderEscaper {

    private static final Pattern ANGLE_BRACKET = Pattern.compile("<([^<>\\n]*)>");
    private static final Pattern CODE_FENCE = Pattern.compile("^(`{3,}|~{3,})");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");

    private static final Set<String> HTML_ELEMENTS = Set.of(
            "a", "abbr", "acronym", "address", "area", "article", "aside", "audio",
            "b", "base", "bdi", "bdo", "blockquote", "body", "br", "button",
            "canvas", "caption", "cite", "code", "col", "colgroup",
            "data", "datalist", "dd", "del", "details", "dfn", "dialog", "div", "dl", "dt",
            "em", "embed",
            "fieldset", "figcaption", "figure", "footer", "form",
            "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html",
            "i", "iframe", "img", "input", "ins",
            "kbd", "keygen",
            "label", "legend", "li", "link",
            "main", "map", "mark", "menu", "menuitem", "meta", "meter",
            "nav", "noscript",
            "object", "ol", "optgroup", "option", "output",
            "p", "picture", "pre", "progress",
            "q", "rp", "rt", "ruby",
            "s", "samp", "script", "section", "select", "small", "source", "span", "strong",
            "style", "sub", "summary", "sup",
            "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track",
            "u", "ul",
            "var", "video", "wbr"
    );

    private PlaceholderEscaper() {}

    public static String escape(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');
            String line = lines[i];
            if (CODE_FENCE.matcher(line).find()) {
                inFence = !inFence;
                out.append(line);
                continue;
            }
            if (inFence) {
                out.append(line);
                continue;
            }
            out.append(processLine(line));
        }
        return out.toString();
    }

    private static String processLine(String line) {
        // Split out inline code spans and process the surrounding parts only
        Matcher m = INLINE_CODE.matcher(line);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(escapePlaceholders(line.substring(last, m.start())));
            out.append(m.group()); // keep inline code untouched
            last = m.end();
        }
        out.append(escapePlaceholders(line.substring(last)));
        return out.toString();
    }

    private static String escapePlaceholders(String text) {
        Matcher m = ANGLE_BRACKET.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String inner = m.group(1);
            String tagName = firstTokenOf(inner.strip().replaceFirst("^/", ""));
            if (HTML_ELEMENTS.contains(tagName.toLowerCase()) || inner.startsWith("!")) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            } else {
                m.appendReplacement(out, Matcher.quoteReplacement("\\<" + inner + "\\>"));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String firstTokenOf(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '/') break;
            i++;
        }
        return s.substring(0, i);
    }
}
