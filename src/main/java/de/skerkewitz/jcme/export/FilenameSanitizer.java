package de.skerkewitz.jcme.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.skerkewitz.jcme.config.ExportConfig;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitize filenames for cross-platform compatibility. Mirrors the Python
 * {@code sanitize_filename} (control-char strip, configurable character mapping,
 * Windows reserved names, length cap, optional lowercase).
 */
public final class FilenameSanitizer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u001f\\u007f]");
    private static final Set<String> WINDOWS_RESERVED = buildWindowsReservedNames();

    private final Map<String, String> charMap;
    private final Pattern charPattern;
    private final int maxLength;
    private final boolean lowercase;

    public FilenameSanitizer(ExportConfig export) {
        this.charMap = parseEncoding(export.filenameEncoding());
        this.charPattern = buildPattern(charMap);
        this.maxLength = export.filenameLength() > 0 ? export.filenameLength() : Integer.MAX_VALUE;
        this.lowercase = export.filenameLowercase();
    }

    public String sanitize(String filename) {
        if (filename == null) return "";
        String result = CONTROL_CHARS.matcher(filename).replaceAll("");
        if (charPattern != null) {
            Matcher m = charPattern.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(charMap.get(m.group())));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        // Trim trailing spaces and dots
        int end = result.length();
        while (end > 0) {
            char c = result.charAt(end - 1);
            if (c != ' ' && c != '.') break;
            end--;
        }
        result = result.substring(0, end);

        // Windows reserved names (case-insensitive on the filename stem)
        String stem = stem(result).toUpperCase(Locale.ROOT);
        if (WINDOWS_RESERVED.contains(stem)) {
            result = result + "_";
        }
        if (lowercase) {
            result = result.toLowerCase(Locale.ROOT);
        }
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength);
        }
        return result;
    }

    private static String stem(String filename) {
        Path path = Path.of(filename.isEmpty() ? "_" : filename);
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /**
     * Parse the encoding string used by the Python {@code filename_encoding} setting.
     * Format: {@code "char1":"replacement1","char2":"replacement2"} (without surrounding braces).
     */
    static Map<String, String> parseEncoding(String setting) {
        if (setting == null || setting.isEmpty()) return Map.of();
        try {
            JsonNode parsed = JSON.readTree("{" + setting + "}");
            if (!parsed.isObject()) return Map.of();
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = parsed.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                result.put(e.getKey(), e.getValue().asText());
            }
            return result;
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private static Pattern buildPattern(Map<String, String> charMap) {
        if (charMap.isEmpty()) return null;
        // Use alternation of Pattern.quote'd literals — works for all chars including
        // backslash, brackets, NUL, and Unicode.
        StringBuilder sb = new StringBuilder();
        for (String key : charMap.keySet()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(Pattern.quote(key));
        }
        return Pattern.compile(sb.toString());
    }

    private static Set<String> buildWindowsReservedNames() {
        Set<String> reserved = new HashSet<>();
        reserved.add("CON");
        reserved.add("PRN");
        reserved.add("AUX");
        reserved.add("NUL");
        for (int i = 1; i <= 9; i++) {
            reserved.add("COM" + i);
            reserved.add("LPT" + i);
        }
        return Set.copyOf(reserved);
    }
}
