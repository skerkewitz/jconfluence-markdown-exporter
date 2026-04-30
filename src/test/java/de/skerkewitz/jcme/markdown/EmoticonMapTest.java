package de.skerkewitz.jcme.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmoticonMapTest {

    @Test
    void atlassian_check_mark_maps_to_check() {
        assertThat(EmoticonMap.ATLASSIAN.get("atlassian-check_mark")).isEqualTo("✅");
        assertThat(EmoticonMap.ATLASSIAN.get("atlassian-warning")).isEqualTo("⚠️");
    }

    @Test
    void decode_emoji_id_handles_single_codepoint() {
        // U+1F60A SMILING FACE WITH SMILING EYES
        assertThat(EmoticonMap.decodeEmojiId("1f60a")).isEqualTo("😊");
    }

    @Test
    void decode_emoji_id_handles_compound_emoji() {
        // U+1F468 + U+200D + U+1F4BB → man technologist
        assertThat(EmoticonMap.decodeEmojiId("1f468-200d-1f4bb")).hasSize(5);
    }

    @Test
    void decode_emoji_id_returns_null_for_invalid_input() {
        assertThat(EmoticonMap.decodeEmojiId("")).isNull();
        assertThat(EmoticonMap.decodeEmojiId(null)).isNull();
        assertThat(EmoticonMap.decodeEmojiId("not-hex")).isNull();
    }

    @Test
    void decode_emoji_id_returns_null_for_out_of_range() {
        assertThat(EmoticonMap.decodeEmojiId("110000")).isNull();
    }
}
