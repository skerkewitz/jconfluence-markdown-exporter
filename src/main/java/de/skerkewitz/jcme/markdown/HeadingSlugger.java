package de.skerkewitz.jcme.markdown;

/**
 * Generate GitHub-compatible heading anchor slugs. Mirrors the
 * <a href="https://github.com/Flet/github-slugger">github-slugger</a> algorithm
 * so generated TOC links resolve correctly in GitHub-rendered Markdown.
 */
public final class HeadingSlugger {

    private HeadingSlugger() {}

    public static String slug(String text) {
        if (text == null) return "";
        String s = text.toLowerCase().strip();
        // drop punctuation; keep Unicode letters, digits, underscores, spaces, hyphens
        // Python's re.\w is Unicode-aware by default; emulate with the (?U) flag in Java.
        s = s.replaceAll("(?U)[^\\w\\s-]", "");
        // whitespace/underscores → hyphens
        s = s.replaceAll("[\\s_]+", "-");
        // collapse runs of hyphens
        s = s.replaceAll("-{2,}", "-");
        return s;
    }
}
