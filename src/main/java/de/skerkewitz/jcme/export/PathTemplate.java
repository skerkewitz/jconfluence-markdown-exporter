package de.skerkewitz.jcme.export;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders Confluence-export path templates such as
 * {@code "{space_name}/{page_title}.md"}.
 *
 * <p>Mirrors Python's {@code string.Template.safe_substitute}: unknown variables are
 * left literal so missing data doesn't blow up the export.
 */
public final class PathTemplate {

    private static final Pattern VAR = Pattern.compile("\\{(\\w+)}");

    private PathTemplate() {}

    public static String render(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) return "";
        Matcher m = VAR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = vars.get(key);
            // Leave the placeholder literal when the value is missing.
            String replacement = value != null ? value : m.group(0);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
