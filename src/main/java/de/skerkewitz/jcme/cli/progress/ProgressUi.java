package de.skerkewitz.jcme.cli.progress;

import de.skerkewitz.jcme.export.ExportStats;
import de.skerkewitz.jcme.model.PageId;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * User-facing progress reporter for export runs.
 *
 * <p>Two render modes:
 * <ul>
 *   <li><b>ANSI</b> — colored, with a single-line replacing status indicator (used while
 *       resolving URLs and during stale-page cleanup) and a box-drawn summary panel at
 *       the end. Auto-selected when stderr is a TTY and {@code NO_COLOR} / {@code CI}
 *       env vars are unset.</li>
 *   <li><b>Plain</b> — newlined lines only, no ANSI escape codes. Used when piped /
 *       redirected to a file / running under CI.</li>
 * </ul>
 *
 * <p>Per-page progress is always printed as one line per page (with a running counter
 * like {@code [12/47]}) to avoid mangling output when many parallel workers are active.
 */
public final class ProgressUi {

    static final String RESET = "[0m";
    static final String BOLD  = "[1m";
    static final String DIM   = "[2m";
    static final String GREEN = "[32m";
    static final String RED   = "[31m";
    static final String YELLOW = "[33m";
    static final String CYAN  = "[36m";
    private static final String CLEAR_LINE = "\r[K";

    private final PrintStream out;
    private final boolean ansi;
    private boolean statusActive;

    public ProgressUi(PrintStream out, boolean ansi) {
        this.out = out;
        this.ansi = ansi;
    }

    /** Auto-detect: ANSI when stderr looks like a TTY and the user hasn't opted out. */
    public static ProgressUi detect() {
        return new ProgressUi(System.err, isAnsiTerminal());
    }

    /** No-op variant for tests / silent runs. */
    public static ProgressUi silent() {
        return new ProgressUi(new PrintStream(OutputStream.nullOutputStream(),
                false, StandardCharsets.UTF_8), false);
    }

    public boolean ansi() { return ansi; }

    /** Print a section header like {@code → Resolving 5 page URL(s)}. */
    public synchronized void phase(String label) {
        clearStatusInternal();
        out.println();
        out.println(bold("→ " + label));
        out.flush();
    }

    /**
     * Update a single-line replacing status (only meaningful in ANSI mode). Subsequent
     * updates overwrite the previous line; {@link #clearStatus()} or any other write
     * removes it. Falls back to a normal line when not on a TTY.
     */
    public synchronized void status(String message) {
        if (ansi) {
            out.print(CLEAR_LINE);
            out.print(dim(message));
            out.flush();
            statusActive = true;
        } else {
            out.println("  " + message);
            out.flush();
        }
    }

    public synchronized void clearStatus() {
        clearStatusInternal();
    }

    /** Print a single per-page progress line with a running counter and outcome glyph. */
    public synchronized void pageDone(int done, int total, PageId pageId, String title, Outcome outcome, Duration elapsed) {
        clearStatusInternal();
        String glyph = switch (outcome) {
            case EXPORTED -> green(ansi ? "✓" : "[ok]");
            case SKIPPED -> dim(ansi ? "·" : "[skip]");
            case FAILED -> red(ansi ? "✗" : "[fail]");
        };
        String time = elapsed != null ? dim(" " + formatDuration(elapsed)) : "";
        out.printf(Locale.ROOT, "  %s [%d/%d] %s (id=%d)%s%n", glyph, done, total, ellipsize(title, 60), pageId.value(), time);
        out.flush();
    }

    /** Print the final summary panel. */
    public synchronized void summary(ExportStats stats, Path outputRoot, Duration elapsed) {
        clearStatusInternal();
        long pageTotal = stats.exported() + stats.skipped() + stats.failed();
        if (pageTotal == 0 && stats.removed() == 0
                && stats.attachmentsExported() == 0 && stats.attachmentsRemoved() == 0) {
            return;
        }
        String title = stats.failed() > 0
                ? yellow("Export finished with errors")
                : green("Export complete");

        StringBuilder body = new StringBuilder();
        appendRow(body, "Pages",       String.valueOf(stats.total()), null);
        appendRow(body, "  Exported",  String.valueOf(stats.exported()), GREEN);
        appendRow(body, "  Skipped",   String.valueOf(stats.skipped()), DIM);
        if (stats.removed() > 0) appendRow(body, "  Removed", String.valueOf(stats.removed()), DIM);
        if (stats.failed() > 0)  appendRow(body, "  Failed",  String.valueOf(stats.failed()), RED);

        long attTotal = stats.attachmentsExported() + stats.attachmentsSkipped() + stats.attachmentsFailed();
        if (attTotal > 0 || stats.attachmentsRemoved() > 0) {
            appendRow(body, "Attachments", String.valueOf(attTotal), null);
            appendRow(body, "  Exported",  String.valueOf(stats.attachmentsExported()), GREEN);
            appendRow(body, "  Skipped",   String.valueOf(stats.attachmentsSkipped()), DIM);
            if (stats.attachmentsRemoved() > 0) appendRow(body, "  Removed", String.valueOf(stats.attachmentsRemoved()), DIM);
            if (stats.attachmentsFailed() > 0)  appendRow(body, "  Failed",  String.valueOf(stats.attachmentsFailed()), RED);
        }
        appendRow(body, "Output",  outputRoot.toAbsolutePath().toString(), CYAN);
        if (elapsed != null) appendRow(body, "Elapsed", formatDuration(elapsed), DIM);

        renderPanel(title, body.toString());
    }

    private void renderPanel(String title, String body) {
        if (!ansi) {
            out.println();
            out.println("--- " + stripAnsi(title) + " ---");
            out.print(stripAnsi(body));
            out.println("------");
            out.flush();
            return;
        }
        // Determine width based on the widest visible line.
        int width = visibleWidth(title) + 4;
        for (String line : body.split("\n")) {
            int w = visibleWidth(line);
            if (w + 4 > width) width = w + 4;
        }
        String horizontal = "─".repeat(width);
        out.println();
        out.println("┌─" + horizontal + "─┐");
        out.println("│ " + center(title, width) + " │");
        out.println("├─" + horizontal + "─┤");
        for (String line : body.split("\n")) {
            out.println("│ " + padRight(line, width) + " │");
        }
        out.println("└─" + horizontal + "─┘");
        out.flush();
    }

    private void appendRow(StringBuilder body, String key, String value, String valueColor) {
        body.append(padRight(key, 14));
        if (valueColor != null && ansi) {
            body.append(valueColor).append(value).append(RESET);
        } else {
            body.append(value);
        }
        body.append('\n');
    }

    private void clearStatusInternal() {
        if (ansi && statusActive) {
            out.print(CLEAR_LINE);
            out.flush();
            statusActive = false;
        }
    }

    // ---------- helpers ----------

    static boolean isAnsiTerminal() {
        if (System.getenv("NO_COLOR") != null) return false;
        if ("true".equalsIgnoreCase(System.getenv("CI"))) return false;
        // System.console() is non-null only when stdin/stdout/stderr are connected to a
        // real terminal — captures the "piped to a file" / "redirected" case correctly.
        return System.console() != null;
    }

    static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String bold(String s)  { return ansi ? BOLD + s + RESET : s; }
    private String dim(String s)   { return ansi ? DIM + s + RESET : s; }
    private String green(String s) { return ansi ? GREEN + s + RESET : s; }
    private String red(String s)   { return ansi ? RED + s + RESET : s; }
    private String yellow(String s){ return ansi ? YELLOW + s + RESET : s; }

    /** Strip ANSI escape sequences for the plain-text fallback. */
    static String stripAnsi(String s) {
        return s.replaceAll("\\[[0-9;]*m", "");
    }

    /** Length without ANSI codes — used for column padding. */
    static int visibleWidth(String s) {
        return stripAnsi(s).codePointCount(0, stripAnsi(s).length());
    }

    static String padRight(String s, int width) {
        int pad = width - visibleWidth(s);
        return pad <= 0 ? s : s + " ".repeat(pad);
    }

    static String center(String s, int width) {
        int pad = width - visibleWidth(s);
        if (pad <= 0) return s;
        int left = pad / 2;
        int right = pad - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }

    static String formatDuration(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + " ms";
        double s = ms / 1000.0;
        // Locale.ROOT pins the decimal separator to '.' so the output is identical
        // on machines with German / French / etc. regional settings.
        if (s < 60) return String.format(Locale.ROOT, "%.1fs", s);
        long total = ms / 1000;
        return String.format(Locale.ROOT, "%dm %02ds", total / 60, total % 60);
    }

    public enum Outcome { EXPORTED, SKIPPED, FAILED }
}
