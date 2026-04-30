package de.skerkewitz.jcme.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderEscaperTest {

    @Test
    void escapes_unknown_placeholder() {
        assertThat(PlaceholderEscaper.escape("Hello <name>"))
                .isEqualTo("Hello \\<name\\>");
    }

    @Test
    void preserves_known_html_tags() {
        // <br/> and <em> are HTML — leave untouched
        assertThat(PlaceholderEscaper.escape("a<br/>b<em>c</em>"))
                .isEqualTo("a<br/>b<em>c</em>");
    }

    @Test
    void preserves_html_comments() {
        assertThat(PlaceholderEscaper.escape("<!-- comment -->"))
                .isEqualTo("<!-- comment -->");
    }

    @Test
    void leaves_inline_code_untouched() {
        assertThat(PlaceholderEscaper.escape("Use `<placeholder>` in code."))
                .isEqualTo("Use `<placeholder>` in code.");
    }

    @Test
    void leaves_fenced_code_untouched() {
        String input = "before\n```\n<placeholder>\n```\nafter <name>";
        String expected = "before\n```\n<placeholder>\n```\nafter \\<name\\>";
        assertThat(PlaceholderEscaper.escape(input)).isEqualTo(expected);
    }

    @Test
    void escapes_closing_placeholder() {
        assertThat(PlaceholderEscaper.escape("</fakeclose>"))
                .isEqualTo("\\</fakeclose\\>");
    }

    @Test
    void empty_input_returns_empty() {
        assertThat(PlaceholderEscaper.escape("")).isEmpty();
        assertThat(PlaceholderEscaper.escape(null)).isEmpty();
    }
}
