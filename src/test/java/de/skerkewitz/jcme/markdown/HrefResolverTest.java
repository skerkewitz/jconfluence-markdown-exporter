package de.skerkewitz.jcme.markdown;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class HrefResolverTest {

    @Test
    void absolute_strips_leading_slashes_and_normalizes_separators() {
        Path target = Paths.get("foo/bar.md");
        assertThat(HrefResolver.resolve(target, Paths.get("ignored/x.md"), HrefResolver.Style.ABSOLUTE))
                .isEqualTo("/foo/bar.md");
    }

    @Test
    void wiki_returns_filename_only() {
        Path target = Paths.get("nested/dir/page.md");
        assertThat(HrefResolver.resolve(target, Paths.get("a/b/c.md"), HrefResolver.Style.WIKI))
                .isEqualTo("page.md");
    }

    @Test
    void relative_walks_up_from_current_page_dir() {
        Path target = Paths.get("space/attachments/file.png");
        Path current = Paths.get("space/folder/page.md");
        assertThat(HrefResolver.resolve(target, current, HrefResolver.Style.RELATIVE))
                .isEqualTo("../attachments/file.png");
    }

    @Test
    void parse_style_falls_back_to_relative() {
        assertThat(HrefResolver.parseStyle(null)).isEqualTo(HrefResolver.Style.RELATIVE);
        assertThat(HrefResolver.parseStyle("RELATIVE")).isEqualTo(HrefResolver.Style.RELATIVE);
        assertThat(HrefResolver.parseStyle("absolute")).isEqualTo(HrefResolver.Style.ABSOLUTE);
        assertThat(HrefResolver.parseStyle("WIKI")).isEqualTo(HrefResolver.Style.WIKI);
        assertThat(HrefResolver.parseStyle("garbage")).isEqualTo(HrefResolver.Style.RELATIVE);
    }

    @Test
    void encode_spaces_replaces_with_percent_20() {
        assertThat(HrefResolver.encodeSpaces("a b c.md")).isEqualTo("a%20b%20c.md");
        assertThat(HrefResolver.encodeSpaces("")).isEmpty();
        assertThat(HrefResolver.encodeSpaces(null)).isEmpty();
    }
}
