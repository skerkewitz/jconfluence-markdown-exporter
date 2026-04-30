package de.skerkewitz.jcme.markdown;

import org.jsoup.nodes.Element;

import java.util.Set;

/**
 * Convert a single HTML element to its Markdown form. The converter receives the element,
 * the already-converted text of its children, and the set of ancestor tag names (plus the
 * special {@code "_inline"} marker when inline-only conversion is required).
 */
@FunctionalInterface
public interface NodeConverter {

    String convert(Element el, String childText, Set<String> parentTags, MarkdownConverter ctx);
}
