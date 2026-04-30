package de.skerkewitz.jcme.cli.progress;

import de.skerkewitz.jcme.export.ExportStats;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressUiTest {

    private static final String ESC = "";

    private static String capture(boolean ansi, java.util.function.Consumer<ProgressUi> body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProgressUi ui = new ProgressUi(new PrintStream(out, true, StandardCharsets.UTF_8), ansi);
        body.accept(ui);
        return out.toString(StandardCharsets.UTF_8);
    }

    // -------------------- Plain mode --------------------

    @Test
    void plain_mode_emits_no_ansi_escapes() {
        String text = capture(false, ui -> {
            ui.phase("Resolving 3 page URL(s)");
            ui.status("https://x.com/page");
            ui.pageDone(1, 3, 42, "My Page", ProgressUi.Outcome.EXPORTED, 320);
            ui.summary(stats(2, 0, 0), Path.of("/tmp/out"), 1500);
        });

        assertThat(text).doesNotContain(ESC);
        assertThat(text).contains("Resolving 3 page URL(s)");
        assertThat(text).contains("[1/3]");
        assertThat(text).contains("My Page");
        assertThat(text).contains("Export complete");
    }

    @Test
    void plain_mode_status_uses_full_lines() {
        String text = capture(false, ui -> ui.status("checking https://x.com"));
        // PrintStream uses the platform line separator (\r\n on Windows).
        assertThat(text).startsWith("  checking https://x.com");
        assertThat(text).endsWith(System.lineSeparator());
    }

    // -------------------- ANSI mode --------------------

    @Test
    void ansi_mode_uses_carriage_return_for_status() {
        String text = capture(true, ui -> ui.status("checking..."));
        assertThat(text).contains("\r" + ESC + "[K");
        assertThat(text).contains("checking...");
        // Status line does NOT end with newline so subsequent prints can overwrite it
        assertThat(text).doesNotEndWith("\n");
    }

    @Test
    void ansi_mode_clears_status_before_subsequent_lines() {
        String text = capture(true, ui -> {
            ui.status("step 1");
            ui.pageDone(1, 1, 1, "p", ProgressUi.Outcome.EXPORTED, 100);
        });
        // After the page line, status is cleared; the page line does end with newline.
        assertThat(text).contains("[1/1]");
        // Status clearing pattern shows up at least twice (initial print + clear)
        long clearCount = text.split("\\u001b\\[K", -1).length - 1;
        assertThat(clearCount).isGreaterThanOrEqualTo(2);
    }

    @Test
    void per_page_outcome_glyphs_differ() {
        String exported = capture(true, ui -> ui.pageDone(1, 3, 1, "ok", ProgressUi.Outcome.EXPORTED, 100));
        String skipped  = capture(true, ui -> ui.pageDone(2, 3, 2, "sk", ProgressUi.Outcome.SKIPPED, -1));
        String failed   = capture(true, ui -> ui.pageDone(3, 3, 3, "ng", ProgressUi.Outcome.FAILED, -1));
        // Different ANSI color codes for each outcome
        assertThat(exported).contains(ProgressUi.GREEN);
        assertThat(skipped).contains(ProgressUi.DIM);
        assertThat(failed).contains(ProgressUi.RED);
    }

    @Test
    void summary_panel_includes_all_buckets() {
        ExportStats stats = new ExportStats(50);
        for (int i = 0; i < 45; i++) stats.incExported();
        for (int i = 0; i < 3; i++) stats.incSkipped();
        for (int i = 0; i < 2; i++) stats.incFailed();
        for (int i = 0; i < 12; i++) stats.incAttachmentsExported();

        String text = capture(true, ui -> ui.summary(stats, Path.of("/tmp/out"), 12_345));

        assertThat(text).contains("Pages");
        assertThat(text).contains("45");      // exported
        assertThat(text).contains("3");       // skipped
        assertThat(text).contains("2");       // failed
        assertThat(text).contains("Attachments");
        assertThat(text).contains("12");      // attachments exported
        assertThat(text).contains("12.3s");   // formatted elapsed time
        assertThat(text).contains("Export finished with errors");
    }

    @Test
    void summary_panel_skipped_when_nothing_done() {
        ExportStats empty = new ExportStats(0);
        String text = capture(true, ui -> ui.summary(empty, Path.of("/tmp/out"), 100));
        assertThat(text).isEmpty();
    }

    @Test
    void summary_uses_complete_title_when_no_failures() {
        ExportStats stats = new ExportStats(1);
        stats.incExported();
        String text = capture(true, ui -> ui.summary(stats, Path.of("/tmp/out"), 100));
        assertThat(text).contains("Export complete");
        assertThat(text).doesNotContain("with errors");
    }

    // -------------------- Static helpers --------------------

    @Test
    void format_ms_handles_sub_second_seconds_and_minutes() {
        assertThat(ProgressUi.formatMs(450)).isEqualTo("450 ms");
        assertThat(ProgressUi.formatMs(1_500)).isEqualTo("1.5s");
        assertThat(ProgressUi.formatMs(72_000)).isEqualTo("1m 12s");
    }

    @Test
    void ellipsize_truncates_long_strings() {
        assertThat(ProgressUi.ellipsize("hello world", 5)).isEqualTo("hell…");
        assertThat(ProgressUi.ellipsize("short", 100)).isEqualTo("short");
        assertThat(ProgressUi.ellipsize(null, 10)).isEmpty();
    }

    @Test
    void strip_ansi_removes_escape_sequences() {
        assertThat(ProgressUi.stripAnsi(ProgressUi.RED + "hi" + ProgressUi.RESET))
                .isEqualTo("hi");
    }

    @Test
    void visible_width_ignores_ansi_codes() {
        assertThat(ProgressUi.visibleWidth(ProgressUi.RED + "abc" + ProgressUi.RESET))
                .isEqualTo(3);
    }

    @Test
    void silent_factory_writes_nothing() {
        ProgressUi silent = ProgressUi.silent();
        // Should not throw, should not affect anything visible. Just call methods.
        silent.phase("x");
        silent.status("y");
        silent.pageDone(1, 1, 1, "p", ProgressUi.Outcome.EXPORTED, 100);
        silent.summary(new ExportStats(1), Path.of("/tmp"), 100);
    }

    private static ExportStats stats(int exported, int skipped, int failed) {
        ExportStats s = new ExportStats(exported + skipped + failed);
        for (int i = 0; i < exported; i++) s.incExported();
        for (int i = 0; i < skipped; i++) s.incSkipped();
        for (int i = 0; i < failed; i++) s.incFailed();
        return s;
    }
}
