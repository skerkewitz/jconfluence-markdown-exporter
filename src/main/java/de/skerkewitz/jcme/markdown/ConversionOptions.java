package de.skerkewitz.jcme.markdown;

/** Options controlling the HTML→Markdown conversion. Mirrors markdownify's option set. */
public record ConversionOptions(
        char bullet,
        HeadingStyle headingStyle,
        boolean wrap
) {
    public static ConversionOptions defaults() {
        return new ConversionOptions('-', HeadingStyle.ATX, false);
    }

    public enum HeadingStyle { ATX, SETEXT, UNDERLINED }
}
