package de.skerkewitz.jcme.markdown;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Walks a jsoup DOM and produces Markdown via a registry of {@link NodeConverter}s.
 *
 * <p>This is the core of the conversion pipeline. Phase-4 default converters handle plain
 * HTML; Phase-5 will register Confluence-specific converters (macros, page links,
 * attachments, emoticons, ...). Subclass or use {@link #register} to extend.
 */
public class MarkdownConverter {

    public static final String INLINE_MARKER = "_inline";

    /** Element names whose children should not be processed (output blank). */
    private static final Set<String> SKIP_TAGS = Set.of("script", "style", "head", "noscript");

    private final Map<String, NodeConverter> converters = new HashMap<>();
    private final ConversionOptions options;

    public MarkdownConverter(ConversionOptions options) {
        this.options = options;
        DefaultConverters.registerAll(this);
    }

    public MarkdownConverter() {
        this(ConversionOptions.defaults());
    }

    public ConversionOptions options() {
        return options;
    }

    /** Register (or replace) the converter for a given lowercase tag name. */
    public final void register(String tagName, NodeConverter converter) {
        converters.put(tagName.toLowerCase(), converter);
    }

    /** Look up a converter by tag name, or return the pass-through default. */
    public NodeConverter converterFor(String tagName) {
        NodeConverter c = converters.get(tagName.toLowerCase());
        return c != null ? c : DefaultConverters.PASSTHROUGH;
    }

    /** Convert the given HTML string to Markdown. */
    public String convert(String html) {
        Document doc = Jsoup.parse(html, "", Parser.htmlParser());
        // jsoup wraps content in <html><head></head><body>...</body></html>;
        // process the body's children to avoid emitting head/body tags.
        Element body = doc.body();
        return tidy(processChildren(body, Set.of()));
    }

    /** Convert an Element subtree to Markdown. */
    public String convertElement(Element root) {
        return tidy(processChildren(root, Set.of()));
    }

    /**
     * Collapse runs of three or more consecutive newlines into the standard double-newline
     * paragraph separator, then trim leading/trailing whitespace.
     *
     * <p>Block converters emit {@code \n\n}-padded output on both sides; concatenating
     * sibling blocks naively produces {@code \n\n\n\n}, which renders as a wide gap.
     */
    private static String tidy(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        return markdown.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** Process a single node (Element or TextNode), recursing as needed. */
    public String processNode(Node node, Set<String> parentTags) {
        if (node instanceof TextNode tn) {
            return processText(tn, parentTags);
        }
        if (node instanceof Element el) {
            return processElement(el, parentTags);
        }
        return "";
    }

    /** Process an element: recurse into children, then apply this element's converter. */
    public String processElement(Element el, Set<String> parentTags) {
        String tag = el.tagName().toLowerCase();
        if (SKIP_TAGS.contains(tag)) return "";
        Set<String> nextParents = withTag(parentTags, tag);
        String childText = processChildren(el, nextParents);
        NodeConverter converter = converterFor(tag);
        return converter.convert(el, childText, parentTags, this);
    }

    /** Concatenate the converted output of every child node. */
    public String processChildren(Element parent, Set<String> parentTags) {
        StringBuilder sb = new StringBuilder();
        for (Node child : parent.childNodes()) {
            sb.append(processNode(child, parentTags));
        }
        return sb.toString();
    }

    private String processText(TextNode tn, Set<String> parentTags) {
        // Inside <pre> and <code>, preserve whitespace verbatim.
        if (parentTags.contains("pre") || parentTags.contains("code")) {
            return tn.getWholeText();
        }
        // Otherwise collapse runs of whitespace to a single space (HTML default).
        String text = tn.getWholeText();
        return text.replaceAll("[\\s\\u00a0]+", " ");
    }

    private static Set<String> withTag(Set<String> parents, String tag) {
        Set<String> next = new LinkedHashSet<>(parents);
        next.add(tag);
        return Collections.unmodifiableSet(next);
    }

    /** Add the inline marker to a parent-tag set (used by table-cell content). */
    public static Set<String> markInline(Set<String> parentTags) {
        Set<String> next = new HashSet<>(parentTags);
        next.add(INLINE_MARKER);
        return next;
    }

    /** Drop the inline marker from a parent-tag set. */
    public static Set<String> dropInline(Set<String> parentTags) {
        if (!parentTags.contains(INLINE_MARKER)) return parentTags;
        Set<String> next = new HashSet<>(parentTags);
        next.remove(INLINE_MARKER);
        return next;
    }

    /** Trim leading and trailing whitespace, returning the prefix, stripped text, and suffix. */
    public static Chomped chomp(String text) {
        if (text == null || text.isEmpty()) return new Chomped("", "", "");
        String prefix = !text.isEmpty() && Character.isWhitespace(text.charAt(0)) ? " " : "";
        String suffix = !text.isEmpty() && Character.isWhitespace(text.charAt(text.length() - 1)) ? " " : "";
        return new Chomped(prefix, suffix, text.trim());
    }

    public record Chomped(String prefix, String suffix, String stripped) {}
}
