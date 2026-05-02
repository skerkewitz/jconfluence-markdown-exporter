package de.skerkewitz.jcme.cli;

import de.skerkewitz.jcme.App;
import de.skerkewitz.jcme.AppInfo;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI-surface smoke tests that catch wiring regressions across {@link App} and its
 * subcommand classes (subcommand registration, aliases, help/version flags).
 *
 * <p>Drives picocli directly rather than spawning a sub-process so the test runs in
 * milliseconds. We rely on commands that don't need a real Confluence backend; the
 * full export path is exercised by integration tests like {@code ExportServiceIntegrationTest}.
 */
class AppCliSmokeTest {

    @Test
    void usage_lists_every_subcommand() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new App());
        cmd.usage(new PrintStream(out, true, StandardCharsets.UTF_8));

        String help = out.toString(StandardCharsets.UTF_8);
        assertThat(help).contains("pages");
        assertThat(help).contains("pages-with-descendants");
        assertThat(help).contains("spaces");
        assertThat(help).contains("orgs");
        assertThat(help).contains("config");
        assertThat(help).contains("version");
        assertThat(help).contains("bugreport");
    }

    @Test
    void singular_aliases_resolve_to_plural_commands() {
        CommandLine cmd = new CommandLine(new App());
        // page/space/org/page-with-descendants are aliases on the export commands.
        for (String alias : new String[]{"page", "space", "org", "page-with-descendants"}) {
            CommandLine.ParseResult result = cmd.parseArgs(alias, "https://example.atlassian.net/wiki/spaces/X");
            assertThat(result.subcommand()).isNotNull();
        }
    }

    @Test
    void version_subcommand_prints_app_name_and_version() {
        // VersionCommand writes via System.out.println, so redirect at the JVM level.
        java.io.PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            CommandLine cmd = new CommandLine(new App());
            int exit = cmd.execute("version");

            assertThat(exit).isZero();
            String captured = buffer.toString(StandardCharsets.UTF_8);
            assertThat(captured).contains(AppInfo.NAME).contains(AppInfo.VERSION);
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void unknown_subcommand_exits_with_nonzero() {
        CommandLine cmd = new CommandLine(new App());
        cmd.setErr(new PrintWriter(new ByteArrayOutputStream()));

        int exit = cmd.execute("not-a-real-command");

        assertThat(exit).isNotZero();
    }

    @Test
    void config_subcommand_has_expected_children() {
        CommandLine cmd = new CommandLine(new App());
        CommandLine config = cmd.getSubcommands().get("config");
        assertThat(config).isNotNull();
        assertThat(config.getSubcommands().keySet())
                .contains("list", "get", "set", "path", "reset", "edit");
    }

    /** Adapter so we can hand picocli a writer that ultimately lands in a byte buffer. */
    private static class PrintWriter extends java.io.PrintWriter {
        PrintWriter(java.io.OutputStream out) {
            super(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        }
    }
}
