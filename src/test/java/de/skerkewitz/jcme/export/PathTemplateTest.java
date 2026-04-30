package de.skerkewitz.jcme.export;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathTemplateTest {

    @Test
    void substitutes_known_variables() {
        String result = PathTemplate.render(
                "{space_name}/{page_title}.md",
                Map.of("space_name", "My Space", "page_title", "Hello World"));
        assertThat(result).isEqualTo("My Space/Hello World.md");
    }

    @Test
    void leaves_missing_variables_literal() {
        String result = PathTemplate.render(
                "{space_name}/{homepage_title}/{page_title}.md",
                Map.of("space_name", "S", "page_title", "P"));
        assertThat(result).isEqualTo("S/{homepage_title}/P.md");
    }

    @Test
    void empty_template_returns_empty() {
        assertThat(PathTemplate.render("", Map.of())).isEmpty();
        assertThat(PathTemplate.render(null, Map.of())).isEmpty();
    }

    @Test
    void special_chars_in_replacement_are_escaped() {
        // $ and \ are special in Matcher#appendReplacement
        String result = PathTemplate.render("{x}", Map.of("x", "a$b\\c"));
        assertThat(result).isEqualTo("a$b\\c");
    }

    @Test
    void single_char_replacement_works() {
        assertThat(PathTemplate.render("{a}", Map.of("a", "1"))).isEqualTo("1");
    }
}
