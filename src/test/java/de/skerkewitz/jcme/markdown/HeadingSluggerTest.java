package de.skerkewitz.jcme.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeadingSluggerTest {

    @Test
    void lowercases_and_replaces_spaces() {
        assertThat(HeadingSlugger.slug("Hello World")).isEqualTo("hello-world");
    }

    @Test
    void strips_punctuation() {
        assertThat(HeadingSlugger.slug("What's up?")).isEqualTo("whats-up");
        assertThat(HeadingSlugger.slug("Hello, World!")).isEqualTo("hello-world");
    }

    @Test
    void collapses_multiple_hyphens() {
        assertThat(HeadingSlugger.slug("a -- b")).isEqualTo("a-b");
    }

    @Test
    void replaces_underscores_with_hyphens() {
        assertThat(HeadingSlugger.slug("snake_case_thing")).isEqualTo("snake-case-thing");
    }

    @Test
    void handles_empty_input() {
        assertThat(HeadingSlugger.slug("")).isEmpty();
        assertThat(HeadingSlugger.slug(null)).isEmpty();
    }

    @Test
    void preserves_unicode_word_chars() {
        assertThat(HeadingSlugger.slug("café au lait")).isEqualTo("café-au-lait");
    }
}
