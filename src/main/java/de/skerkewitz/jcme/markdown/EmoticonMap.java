package de.skerkewitz.jcme.markdown;

import java.util.Map;

/** Atlassian emoticon-id → unicode emoji map. Mirrors the Python {@code _ATLASSIAN_EMOTICONS} dict. */
public final class EmoticonMap {

    public static final Map<String, String> ATLASSIAN = Map.ofEntries(
            Map.entry("atlassian-check_mark", "✅"),
            Map.entry("atlassian-cross_mark", "❌"),
            Map.entry("atlassian-yes", "👍"),
            Map.entry("atlassian-no", "👎"),
            Map.entry("atlassian-information", "ℹ️"),
            Map.entry("atlassian-warning", "⚠️"),
            Map.entry("atlassian-forbidden", "🚫"),
            Map.entry("atlassian-plus", "➕"),
            Map.entry("atlassian-minus", "➖"),
            Map.entry("atlassian-question", "❓"),
            Map.entry("atlassian-exclamation", "❗"),
            Map.entry("atlassian-light_on", "💡"),
            Map.entry("atlassian-light_off", "💡"),
            Map.entry("atlassian-star_yellow", "⭐"),
            Map.entry("atlassian-blue_star", "🔵"),
            Map.entry("atlassian-smile", "😊"),
            Map.entry("atlassian-sad", "😞"),
            Map.entry("atlassian-tongue", "😛"),
            Map.entry("atlassian-biggrin", "😁"),
            Map.entry("atlassian-wink", "😉")
    );

    private static final int MAX_UNICODE_CODEPOINT = 0x10FFFF;

    private EmoticonMap() {}

    /**
     * Decode a {@code data-emoji-id} attribute (a hyphen-separated list of hex codepoints)
     * to its emoji string. Returns {@code null} when the id is empty or contains an
     * out-of-range value.
     */
    public static String decodeEmojiId(String emojiId) {
        if (emojiId == null || emojiId.isEmpty()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            for (String part : emojiId.split("-")) {
                int cp = Integer.parseInt(part, 16);
                if (cp < 0 || cp > MAX_UNICODE_CODEPOINT) return null;
                sb.appendCodePoint(cp);
            }
            return sb.toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
