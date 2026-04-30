package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.config.ExportConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizerTest {

    private static FilenameSanitizer sanitizer(ExportConfig config) {
        return new FilenameSanitizer(config);
    }

    @Test
    void replaces_default_forbidden_chars() {
        FilenameSanitizer s = sanitizer(ExportConfig.defaults());
        // Default mapping replaces <>:"/\|?* with _
        assertThat(s.sanitize("a/b\\c:d")).isEqualTo("a_b_c_d");
        assertThat(s.sanitize("file<name>")).isEqualTo("file_name_");
        assertThat(s.sanitize("a|b?c*")).isEqualTo("a_b_c_");
    }

    @Test
    void strips_control_characters() {
        FilenameSanitizer s = sanitizer(ExportConfig.defaults());
        assertThat(s.sanitize("helloworld")).isEqualTo("helloworld");
    }

    @Test
    void trims_trailing_spaces_and_dots() {
        FilenameSanitizer s = sanitizer(ExportConfig.defaults());
        assertThat(s.sanitize("name. .")).isEqualTo("name");
        assertThat(s.sanitize("trailing  ")).isEqualTo("trailing");
    }

    @Test
    void appends_underscore_for_windows_reserved_names() {
        FilenameSanitizer s = sanitizer(ExportConfig.defaults());
        assertThat(s.sanitize("CON")).isEqualTo("CON_");
        assertThat(s.sanitize("nul.txt")).isEqualTo("nul.txt_");
        assertThat(s.sanitize("COM1")).isEqualTo("COM1_");
    }

    @Test
    void respects_max_length() {
        ExportConfig cfg = withFilenameLength(ExportConfig.defaults(), 5);
        FilenameSanitizer s = sanitizer(cfg);
        assertThat(s.sanitize("hello world")).isEqualTo("hello");
    }

    @Test
    void lowercase_when_enabled() {
        ExportConfig cfg = withLowercase(ExportConfig.defaults(), true);
        FilenameSanitizer s = sanitizer(cfg);
        assertThat(s.sanitize("HELLO World")).isEqualTo("hello world");
    }

    @Test
    void parses_simple_encoding_setting() {
        Map<String, String> map = FilenameSanitizer.parseEncoding("\" \":\"-\",\":\":\"_\"");
        assertThat(map).containsEntry(" ", "-").containsEntry(":", "_");
    }

    @Test
    void parses_empty_encoding_returns_empty_map() {
        assertThat(FilenameSanitizer.parseEncoding("")).isEmpty();
        assertThat(FilenameSanitizer.parseEncoding(null)).isEmpty();
    }

    @Test
    void custom_encoding_replaces_chars() {
        ExportConfig cfg = withFilenameEncoding(ExportConfig.defaults(), "\" \":\"-\"");
        FilenameSanitizer s = sanitizer(cfg);
        assertThat(s.sanitize("hello world")).isEqualTo("hello-world");
    }

    private static ExportConfig withFilenameLength(ExportConfig base, int length) {
        return new ExportConfig(
                base.logLevel(), base.outputPath(), base.pageHref(), base.pagePath(),
                base.attachmentHref(), base.attachmentPath(), base.attachmentExportAll(),
                base.pageBreadcrumbs(), base.pagePropertiesAsFrontMatter(),
                base.filenameEncoding(), length, base.filenameLowercase(),
                base.includeDocumentTitle(), base.enableJiraEnrichment(),
                base.skipUnchanged(), base.cleanupStale(), base.lockfileName(),
                base.existenceCheckBatchSize());
    }

    private static ExportConfig withLowercase(ExportConfig base, boolean lowercase) {
        return new ExportConfig(
                base.logLevel(), base.outputPath(), base.pageHref(), base.pagePath(),
                base.attachmentHref(), base.attachmentPath(), base.attachmentExportAll(),
                base.pageBreadcrumbs(), base.pagePropertiesAsFrontMatter(),
                base.filenameEncoding(), base.filenameLength(), lowercase,
                base.includeDocumentTitle(), base.enableJiraEnrichment(),
                base.skipUnchanged(), base.cleanupStale(), base.lockfileName(),
                base.existenceCheckBatchSize());
    }

    private static ExportConfig withFilenameEncoding(ExportConfig base, String encoding) {
        return new ExportConfig(
                base.logLevel(), base.outputPath(), base.pageHref(), base.pagePath(),
                base.attachmentHref(), base.attachmentPath(), base.attachmentExportAll(),
                base.pageBreadcrumbs(), base.pagePropertiesAsFrontMatter(),
                encoding, base.filenameLength(), base.filenameLowercase(),
                base.includeDocumentTitle(), base.enableJiraEnrichment(),
                base.skipUnchanged(), base.cleanupStale(), base.lockfileName(),
                base.existenceCheckBatchSize());
    }
}
