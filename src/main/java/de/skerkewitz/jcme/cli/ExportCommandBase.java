package de.skerkewitz.jcme.cli;

import de.skerkewitz.jcme.api.exceptions.AuthNotConfiguredException;
import de.skerkewitz.jcme.cli.config.InteractiveMenu;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.export.ExportService;
import de.skerkewitz.jcme.export.ExportStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

/**
 * Common entry-point logic for the {@code pages}, {@code pages-with-descendants},
 * {@code spaces}, and {@code orgs} CLI commands. Subclasses wire one of the
 * {@link ExportService} methods via {@link #exportFunction()}.
 */
public abstract class ExportCommandBase implements Callable<Integer> {

    protected static final Logger LOG = LoggerFactory.getLogger(ExportCommandBase.class);

    protected abstract List<String> urls();

    /** Pick the {@link ExportService} method to invoke for this command. */
    protected abstract BiFunction<ExportService, List<String>, ExportStats> exportFunction();

    @Override
    public Integer call() {
        ConfigStore store = new ConfigStore();
        ExportService service = new ExportService(store);
        try {
            ExportStats stats = exportFunction().apply(service, urls());
            printSummary(stats);
            return stats.failed() > 0 ? 1 : 0;
        } catch (AuthNotConfiguredException e) {
            System.err.println(e.getMessage());
            System.err.println("Opening the interactive credential prompt for "
                    + e.service() + " at " + e.url() + " — re-run the export afterwards.");
            try {
                new InteractiveMenu(store).authForUrl(e.service(), e.url());
            } catch (Exception ignored) {
                // If the menu can't run (e.g. piped stdin), fall back to a hint.
                System.err.println("Tip: run `jcme config edit auth." + e.service().toLowerCase()
                        + "` interactively, or set values via `jcme config set`.");
            }
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            LOG.error("Export failed", e);
            return 1;
        }
    }

    static void printSummary(ExportStats stats) {
        long total = stats.exported() + stats.skipped() + stats.failed();
        if (total == 0 && stats.removed() == 0) return;

        StringBuilder out = new StringBuilder();
        out.append("\n--- Export summary ---\n");
        out.append("Pages: ").append(stats.total()).append(" total\n");
        out.append("  Exported: ").append(stats.exported()).append('\n');
        out.append("  Skipped:  ").append(stats.skipped()).append('\n');
        if (stats.removed() > 0) out.append("  Removed:  ").append(stats.removed()).append('\n');
        if (stats.failed() > 0)  out.append("  Failed:   ").append(stats.failed()).append('\n');

        long attTotal = stats.attachmentsExported() + stats.attachmentsSkipped() + stats.attachmentsFailed();
        if (attTotal > 0 || stats.attachmentsRemoved() > 0) {
            out.append("Attachments: ").append(attTotal).append(" total\n");
            out.append("  Exported: ").append(stats.attachmentsExported()).append('\n');
            out.append("  Skipped:  ").append(stats.attachmentsSkipped()).append('\n');
            if (stats.attachmentsRemoved() > 0)
                out.append("  Removed:  ").append(stats.attachmentsRemoved()).append('\n');
            if (stats.attachmentsFailed() > 0)
                out.append("  Failed:   ").append(stats.attachmentsFailed()).append('\n');
        }
        System.err.print(out);
    }
}
