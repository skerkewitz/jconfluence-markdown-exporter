package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.lockfile.AttachmentEntry;
import de.skerkewitz.jcme.lockfile.LockfileManager;
import de.skerkewitz.jcme.markdown.RenderingContext;
import de.skerkewitz.jcme.model.ExportablePage;
import de.skerkewitz.jcme.model.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs page exports in parallel via a fixed thread pool sized from
 * {@code connection_config.max_workers}. Each task fetches the full {@link Page} via
 * the cached fetcher, runs {@link PageExporter}, then records the lockfile entry.
 *
 * <p>Caller is responsible for marking pages as seen ({@link LockfileManager#markSeen}) and
 * filtering out unchanged pages before passing the list. Failures on individual pages are
 * logged and counted in {@link ExportStats#incFailed()} — they don't abort the run.
 */
public final class ParallelExportRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelExportRunner.class);

    private final ConfluenceFetcher fetcher;
    private final PageExporter exporter;
    private final LockfileManager lockfile;
    private final RenderingContextFactory rcFactory;
    private final int maxWorkers;
    private final boolean serial;

    public ParallelExportRunner(ConfluenceFetcher fetcher, PageExporter exporter,
                                LockfileManager lockfile, RenderingContextFactory rcFactory,
                                int maxWorkers, boolean serial) {
        this.fetcher = fetcher;
        this.exporter = exporter;
        this.lockfile = lockfile;
        this.rcFactory = rcFactory;
        this.maxWorkers = Math.max(1, maxWorkers);
        this.serial = serial || maxWorkers <= 1;
    }

    public void run(List<? extends ExportablePage> pages, ExportStats stats) {
        if (pages.isEmpty()) return;

        if (serial) {
            for (ExportablePage page : pages) runOne(page, stats);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(maxWorkers, namedFactory());
        try {
            List<Future<?>> futures = new ArrayList<>(pages.size());
            for (ExportablePage page : pages) {
                futures.add(executor.submit(() -> runOne(page, stats)));
            }
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) { /* runOne handles errors */ }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runOne(ExportablePage page, ExportStats stats) {
        try {
            Page full = fetcher.getPage(page.id(), page.baseUrl());
            Map<String, AttachmentEntry> attachments = exporter.exportPage(full);
            RenderingContext rc = rcFactory.create(full);
            lockfile.recordPage(full, attachments, rc);
            stats.incExported();
        } catch (Exception e) {
            LOG.warn("Failed to export page {}: {}", page.id(), e.toString());
            stats.incFailed();
        }
    }

    private java.util.concurrent.ThreadFactory namedFactory() {
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, "jcme-export-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    /** Creates a {@link RenderingContext} appropriate for the page being recorded. */
    @FunctionalInterface
    public interface RenderingContextFactory {
        RenderingContext create(Page page);
    }
}
