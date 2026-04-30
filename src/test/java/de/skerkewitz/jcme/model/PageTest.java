package de.skerkewitz.jcme.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    @Test
    void inaccessible_page_has_sentinel_title() {
        Page p = Page.inaccessible(42, "https://x");
        assertThat(p.isInaccessible()).isTrue();
        assertThat(p.title()).isEqualTo(Page.INACCESSIBLE_TITLE);
        assertThat(p.id()).isEqualTo(42);
    }

    @Test
    void attachment_lookups_by_id_file_id_and_title() {
        Attachment a = new Attachment("base", "att1234", "screenshot.png", Space.empty("base"), List.of(),
                Version.empty(), 0, "image/png", "", "fileGuid", "", "/dl", "");
        Page p = new Page("base", 1, "T", Space.empty("base"), List.of(), Version.empty(),
                "", "", "", List.of(), List.of(a));

        assertThat(p.attachmentById("att1234")).contains(a);
        assertThat(p.attachmentById("att12")).contains(a); // substring match like Python
        assertThat(p.attachmentById("missing")).isEmpty();

        assertThat(p.attachmentByFileId("fileGuid")).contains(a);
        assertThat(p.attachmentByFileId("nope")).isEmpty();

        assertThat(p.attachmentsByTitle("screenshot.png")).containsExactly(a);
        assertThat(p.attachmentsByTitle("nope")).isEmpty();
    }
}
