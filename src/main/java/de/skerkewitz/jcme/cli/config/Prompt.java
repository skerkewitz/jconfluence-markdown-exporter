package de.skerkewitz.jcme.cli.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tiny prompt helpers used by the interactive configuration menu. Wraps
 * {@link System#console()} for masked input when available; falls back to
 * {@link BufferedReader} on stdin (with a one-line warning when secrets are
 * about to be entered without echo masking).
 */
public final class Prompt {

    private final BufferedReader reader;
    private final PrintStream out;
    private final java.io.Console console;

    public Prompt() {
        this(System.console(), new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
    }

    Prompt(java.io.Console console, BufferedReader reader, PrintStream out) {
        this.console = console;
        this.reader = reader;
        this.out = out;
    }

    /** Print a question, return the user's line (trimmed). Empty input → {@code defaultValue}. */
    public String text(String message, String defaultValue) {
        String suffix = defaultValue == null || defaultValue.isEmpty()
                ? ""
                : " [" + defaultValue + "]";
        out.print(message + suffix + ": ");
        out.flush();
        String line = readLine();
        if (line == null) return defaultValue == null ? "" : defaultValue;
        line = line.trim();
        return line.isEmpty() && defaultValue != null ? defaultValue : line;
    }

    /**
     * Read a secret with masked echo when the controlling terminal supports it.
     * Falls back to plain stdin (with an explicit warning) so the menu still works
     * under piped input or in IDEs that don't expose a console.
     */
    public String secret(String message) {
        if (console != null) {
            char[] chars = console.readPassword("%s: ", message);
            return chars == null ? "" : new String(chars);
        }
        out.print(message + " (input not masked): ");
        out.flush();
        String line = readLine();
        return line == null ? "" : line.trim();
    }

    /** Yes/no question. Empty input keeps {@code defaultValue}. */
    public boolean confirm(String message, boolean defaultValue) {
        String hint = defaultValue ? "[Y/n]" : "[y/N]";
        out.print(message + " " + hint + ": ");
        out.flush();
        String line = readLine();
        if (line == null) return defaultValue;
        line = line.trim().toLowerCase();
        if (line.isEmpty()) return defaultValue;
        return line.startsWith("y");
    }

    /**
     * Numbered selection. Returns the chosen index, or -1 when the user enters
     * an empty line (treated as cancel). Re-prompts on invalid input.
     */
    public int select(String title, List<String> choices) {
        if (choices.isEmpty()) return -1;
        while (true) {
            out.println();
            out.println(title);
            for (int i = 0; i < choices.size(); i++) {
                out.println("  " + (i + 1) + ") " + choices.get(i));
            }
            out.print("Select [1-" + choices.size() + "] (empty to cancel): ");
            out.flush();
            String line = readLine();
            if (line == null) return -1;
            line = line.trim();
            if (line.isEmpty()) return -1;
            try {
                int idx = Integer.parseInt(line) - 1;
                if (idx >= 0 && idx < choices.size()) return idx;
            } catch (NumberFormatException ignored) {
                // fall through to "invalid" branch
            }
            out.println("Invalid choice — try again.");
        }
    }

    public void println(String message) {
        out.println(message);
    }

    public void println() { out.println(); }

    private String readLine() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
