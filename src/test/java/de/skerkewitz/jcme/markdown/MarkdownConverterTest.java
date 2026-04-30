package de.skerkewitz.jcme.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownConverterTest {

    private final MarkdownConverter md = new MarkdownConverter();

    private String convert(String html) {
        return md.convert(html);
    }

    // -------------------- Headings --------------------

    @Test
    void heading_levels() {
        assertThat(convert("<h1>One</h1>")).isEqualTo("# One");
        assertThat(convert("<h2>Two</h2>")).isEqualTo("## Two");
        assertThat(convert("<h3>Three</h3>")).isEqualTo("### Three");
        assertThat(convert("<h6>Six</h6>")).isEqualTo("###### Six");
    }

    @Test
    void heading_collapses_internal_whitespace() {
        assertThat(convert("<h1>  Hello   World  </h1>")).isEqualTo("# Hello World");
    }

    // -------------------- Paragraphs --------------------

    @Test
    void single_paragraph() {
        assertThat(convert("<p>Hello world.</p>")).isEqualTo("Hello world.");
    }

    @Test
    void multiple_paragraphs_separated_by_blank_line() {
        assertThat(convert("<p>One.</p><p>Two.</p>"))
                .isEqualTo("One.\n\nTwo.");
    }

    // -------------------- Inline emphasis --------------------

    @Test
    void bold_and_italic() {
        assertThat(convert("<p><strong>bold</strong></p>")).isEqualTo("**bold**");
        assertThat(convert("<p><em>italic</em></p>")).isEqualTo("_italic_");
        assertThat(convert("<p><b>bold</b> and <i>italic</i></p>"))
                .isEqualTo("**bold** and _italic_");
    }

    @Test
    void chomp_preserves_surrounding_whitespace() {
        // The Python project's _normalize_unicode_whitespace fix lives in Phase 5;
        // here we just test plain whitespace preservation.
        assertThat(convert("<p>before <em>word</em> after</p>"))
                .isEqualTo("before _word_ after");
    }

    @Test
    void strikethrough() {
        assertThat(convert("<p><del>gone</del></p>")).isEqualTo("~~gone~~");
        assertThat(convert("<p><s>gone</s></p>")).isEqualTo("~~gone~~");
    }

    // -------------------- Code --------------------

    @Test
    void inline_code() {
        assertThat(convert("<p>Run <code>npm test</code>.</p>"))
                .isEqualTo("Run `npm test`.");
    }

    @Test
    void pre_code_block() {
        assertThat(convert("<pre><code>line1\nline2</code></pre>"))
                .isEqualTo("```\nline1\nline2\n```");
    }

    @Test
    void pre_with_language_class() {
        assertThat(convert("<pre><code class=\"language-java\">int x = 1;</code></pre>"))
                .isEqualTo("```java\nint x = 1;\n```");
    }

    // -------------------- Lists --------------------

    @Test
    void simple_unordered_list() {
        String md = convert("<ul><li>One</li><li>Two</li><li>Three</li></ul>");
        assertThat(md).isEqualTo("- One\n- Two\n- Three");
    }

    @Test
    void simple_ordered_list() {
        String md = convert("<ol><li>One</li><li>Two</li></ol>");
        assertThat(md).isEqualTo("1. One\n2. Two");
    }

    @Test
    void ordered_list_with_start_attribute() {
        String md = convert("<ol start=\"5\"><li>Five</li><li>Six</li></ol>");
        assertThat(md).isEqualTo("5. Five\n6. Six");
    }

    @Test
    void nested_unordered_list() {
        String html = "<ul><li>Top<ul><li>Nested</li></ul></li><li>Sibling</li></ul>";
        String result = convert(html);
        assertThat(result).contains("- Top");
        assertThat(result).contains("- Nested");
        assertThat(result).contains("- Sibling");
    }

    // -------------------- Links + images --------------------

    @Test
    void link_with_text() {
        assertThat(convert("<a href=\"http://x\">click</a>"))
                .isEqualTo("[click](http://x)");
    }

    @Test
    void link_with_title() {
        assertThat(convert("<a href=\"http://x\" title=\"hover\">click</a>"))
                .isEqualTo("[click](http://x \"hover\")");
    }

    @Test
    void image_with_alt() {
        assertThat(convert("<img src=\"a.png\" alt=\"alt\">"))
                .isEqualTo("![alt](a.png)");
    }

    @Test
    void image_with_title() {
        assertThat(convert("<img src=\"a.png\" alt=\"alt\" title=\"t\">"))
                .isEqualTo("![alt](a.png \"t\")");
    }

    // -------------------- Block elements --------------------

    @Test
    void blockquote() {
        assertThat(convert("<blockquote>quoted</blockquote>"))
                .isEqualTo("> quoted");
    }

    @Test
    void hr_renders_thematic_break() {
        assertThat(convert("<p>before</p><hr><p>after</p>"))
                .isEqualTo("before\n\n---\n\nafter");
    }

    @Test
    void br_emits_two_space_then_newline() {
        // <br> appears in the middle of inline text.
        String result = convert("<p>line1<br>line2</p>");
        assertThat(result).contains("line1  \nline2");
    }

    // -------------------- Skipped elements --------------------

    @Test
    void scripts_and_styles_are_dropped() {
        assertThat(convert("<p>visible</p><script>alert(1)</script>"))
                .isEqualTo("visible");
        assertThat(convert("<style>.x{}</style><p>visible</p>"))
                .isEqualTo("visible");
    }

    // -------------------- Tables --------------------

    @Test
    void simple_table_with_header() {
        String html = """
                <table>
                  <thead><tr><th>A</th><th>B</th></tr></thead>
                  <tbody><tr><td>1</td><td>2</td></tr></tbody>
                </table>
                """;
        String md = convert(html);
        assertThat(md).contains("| A | B |");
        assertThat(md).contains("| --- | --- |");
        assertThat(md).contains("| 1 | 2 |");
    }

    @Test
    void table_pipes_in_cells_are_escaped() {
        String html = "<table><tr><th>X</th></tr><tr><td>a|b</td></tr></table>";
        String md = convert(html);
        assertThat(md).contains("a\\|b");
    }

    @Test
    void table_with_colspan_pads_columns() {
        String html = "<table>"
                + "<tr><th>A</th><th>B</th><th>C</th></tr>"
                + "<tr><td colspan=\"2\">merged</td><td>3</td></tr>"
                + "</table>";
        String md = convert(html);
        assertThat(md).contains("| A | B | C |");
        // After padding: "merged" + "" (colspan filler) + "3"
        assertThat(md).contains("| merged |  | 3 |");
    }

    @Test
    void table_with_rowspan_propagates_blank_cells() {
        String html = "<table>"
                + "<tr><th>A</th><th>B</th></tr>"
                + "<tr><td rowspan=\"2\">stuck</td><td>x</td></tr>"
                + "<tr><td>y</td></tr>"
                + "</table>";
        String md = convert(html);
        assertThat(md).contains("| stuck | x |");
        // Second row's first column is occupied by the rowspan above
        assertThat(md).contains("|  | y |");
    }

    @Test
    void empty_input_returns_empty() {
        assertThat(convert("")).isEmpty();
        assertThat(convert("<p></p>")).isEmpty();
    }

    // -------------------- Whitespace --------------------

    @Test
    void collapses_runs_of_whitespace_in_text() {
        // HTML default: any run of whitespace is one space
        assertThat(convert("<p>a   b\n\n c</p>")).isEqualTo("a b c");
    }

    @Test
    void preserves_whitespace_inside_pre() {
        String html = "<pre><code>a    b\n  c</code></pre>";
        assertThat(convert(html)).isEqualTo("```\na    b\n  c\n```");
    }

    // -------------------- Subclassing / extension --------------------

    @Test
    void register_overrides_existing_converter() {
        MarkdownConverter custom = new MarkdownConverter();
        custom.register("strong", (el, text, parents, ctx) -> "[!" + text + "!]");
        assertThat(custom.convert("<p><strong>x</strong></p>")).isEqualTo("[!x!]");
    }
}
