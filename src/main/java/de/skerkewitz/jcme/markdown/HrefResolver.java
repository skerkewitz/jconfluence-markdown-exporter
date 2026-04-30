package de.skerkewitz.jcme.markdown;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Compute the href string used in markdown links/images for a target file path.
 * Supports the same three styles the Python tool exposes via {@code page_href} /
 * {@code attachment_href}: {@code absolute}, {@code relative}, {@code wiki}.
 */
public final class HrefResolver {

    public enum Style { ABSOLUTE, RELATIVE, WIKI }

    private HrefResolver() {}

    public static Style parseStyle(String value) {
        if (value == null) return Style.RELATIVE;
        return switch (value.toLowerCase()) {
            case "absolute" -> Style.ABSOLUTE;
            case "wiki" -> Style.WIKI;
            default -> Style.RELATIVE;
        };
    }

    /** Produce a markdown href for {@code targetPath}, resolved against {@code currentPagePath}. */
    public static String resolve(Path targetPath, Path currentPagePath, Style style) {
        if (targetPath == null) return "";
        return switch (style) {
            case ABSOLUTE -> "/" + stripLeadingSlash(targetPath.toString().replace('\\', '/'));
            case WIKI -> targetPath.getFileName().toString();
            case RELATIVE -> relative(targetPath, currentPagePath);
        };
    }

    private static String relative(Path target, Path source) {
        Path sourceDir = source != null && source.getParent() != null
                ? source.getParent()
                : Paths.get("");
        try {
            // Path.relativize requires both to be absolute or both relative; we keep them relative.
            Path rel = sourceDir.relativize(target);
            return rel.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return target.toString().replace('\\', '/');
        }
    }

    private static String stripLeadingSlash(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '/') i++;
        return s.substring(i);
    }

    /** Replace literal spaces with {@code %20} in an href (Markdown link path encoding). */
    public static String encodeSpaces(String href) {
        return href == null ? "" : href.replace(" ", "%20");
    }
}
