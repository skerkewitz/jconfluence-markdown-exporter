package de.skerkewitz.jcme.cli;

import de.skerkewitz.jcme.api.exceptions.AuthNotConfiguredException;
import de.skerkewitz.jcme.cli.config.InteractiveMenu;
import de.skerkewitz.jcme.cli.progress.ProgressUi;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.export.ExportService;
import de.skerkewitz.jcme.export.ExportStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
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
        ProgressUi progress = ProgressUi.detect();
        ExportService service = new ExportService(store, progress);
        long started = System.currentTimeMillis();
        try {
            ExportStats stats = exportFunction().apply(service, urls());
            progress.summary(stats, resolveOutputRoot(store), System.currentTimeMillis() - started);
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

    private static Path resolveOutputRoot(ConfigStore store) {
        AppConfig settings = store.loadEffective();
        String configured = settings.export().outputPath();
        return (configured == null || configured.isEmpty()) ? Paths.get("") : Paths.get(configured);
    }
}
