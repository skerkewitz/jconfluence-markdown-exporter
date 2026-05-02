package de.skerkewitz.jcme.model;

import de.skerkewitz.jcme.api.BaseUrl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentTest {

    private static final BaseUrl BASE = BaseUrl.of("https://x.atlassian.net");

    private static Attachment make(String mediaType, String comment, String title, String fileId) {
        return new Attachment(
                BASE,
                "att1",
                title,
                Space.empty(BASE),
                List.of(),
                Version.empty(),
                42L,
                mediaType,
                "PNG image",
                fileId,
                "",
                "/download/att1",
                comment
        );
    }

    @Test
    void png_extension_resolved_via_mime_table() {
        Attachment a = make("image/png", "", "diagram.png", "guid");
        assertThat(a.extension()).isEqualTo(".png");
        assertThat(a.filename()).isEqualTo("guid.png");
    }

    @Test
    void drawio_diagram_uses_drawio_extension_when_comment_matches() {
        Attachment a = make("application/vnd.jgraph.mxfile", "draw.io diagram", "diag", "guid");
        assertThat(a.extension()).isEqualTo(".drawio");
        assertThat(a.filename()).isEqualTo("guid.drawio");
    }

    @Test
    void drawio_preview_uses_drawio_png_extension() {
        Attachment a = make("image/png", "draw.io preview", "diag.png", "guid");
        assertThat(a.extension()).isEqualTo(".drawio.png");
    }

    @Test
    void title_without_extension_strips_known_extension() {
        Attachment a = make("image/png", "", "screenshot.png", "guid");
        assertThat(a.titleWithoutExtension()).isEqualTo("screenshot");
    }

    @Test
    void title_without_extension_falls_back_to_last_dot() {
        // Title doesn't end with the resolved extension — fall back to dot-trimming.
        Attachment a = make("application/octet-stream", "", "foo.bar", "guid");
        assertThat(a.extension()).isEqualTo("");
        assertThat(a.titleWithoutExtension()).isEqualTo("foo");
    }

    @Test
    void unknown_media_type_returns_empty_extension() {
        Attachment a = make("application/x-something-weird", "", "x", "guid");
        assertThat(a.extension()).isEqualTo("");
        assertThat(a.filename()).isEqualTo("guid");
    }
}
