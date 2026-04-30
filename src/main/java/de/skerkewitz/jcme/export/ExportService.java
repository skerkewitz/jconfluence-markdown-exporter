package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.api.ApiClientFactory;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.lockfile.LockfileManager;
import de.skerkewitz.jcme.markdown.PageRenderer;
import de.skerkewitz.jcme.markdown.RenderingContext;
import de.skerkewitz.jcme.model.Descendant;
import de.skerkewitz.jcme.model.ExportablePage;
import de.skerkewitz.jcme.model.Organization;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.Space;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Top-level export entrypoint used by the {@code pages}, {@code pages-with-descendants},
 * {@code spaces}, and {@code orgs} CLI commands. Wires the fetcher, page renderer, lockfile,
 * parallel runner, and stale-cleanup into one cohesive run.
 */
public final class ExportService {

    private static final Logger LOG = LoggerFactory.getLogger(ExportService.class);

    private final ConfigStore configStore;
    private final ApiClientFactory apiFactory;
    private final ConfluenceFetcher fetcher;

    public ExportService(ConfigStore configStore) {
        this.configStore = configStore;
        this.apiFactory = new ApiClientFactory(configStore);
        this.fetcher = new ConfluenceFetcher(apiFactory, configStore);
    }

    public ExportService(ConfigStore configStore, ApiClientFactory apiFactory,
                         ConfluenceFetcher fetcher) {
        this.configStore = configStore;
        this.apiFactory = apiFactory;
        this.fetcher = fetcher;
    }

    public ConfluenceFetcher fetcher() { return fetcher; }
    public ApiClientFactory apiFactory() { return apiFactory; }

    /** Export one or more pages by URL. Equivalent of {@code cme pages URL...}. */
    public ExportStats exportPages(List<String> urls) {
        AppConfig settings = configStore.loadEffective();
        Path outputRoot = resolveOutputRoot(settings);
        LOG.info("Output root: {}", outputRoot.toAbsolutePath());
        LockfileManager lockfile = newLockfile(settings, outputRoot);
        LOG.info("Resolving {} page URL(s) before export…", urls.size());

        Set<String> seenBaseUrls = new LinkedHashSet<>();
        List<Page> pages = new ArrayList<>();
        for (String url : urls) {
            Page page = fetcher.resolvePageFromUrl(url);
            pages.add(page);
            seenBaseUrls.add(page.baseUrl());
        }
        LOG.info("Resolved {} page(s) — starting export", pages.size());

        ExportStats stats = new ExportStats(pages.size());
        runExport(settings, outputRoot, lockfile, pages, stats);
        runCleanup(settings, lockfile, seenBaseUrls, stats);
        LOG.info("Export finished: {} exported, {} skipped, {} failed, {} removed",
                stats.exported(), stats.skipped(), stats.failed(), stats.removed());
        return stats;
    }

    /** Export pages and all their descendants. Equivalent of {@code cme pages-with-descendants}. */
    public ExportStats exportPagesWithDescendants(List<String> urls) {
        AppConfig settings = configStore.loadEffective();
        Path outputRoot = resolveOutputRoot(settings);
        LockfileManager lockfile = newLockfile(settings, outputRoot);

        Set<String> seenBaseUrls = new LinkedHashSet<>();
        List<ExportablePage> targets = new ArrayList<>();
        for (String url : urls) {
            Page page = fetcher.resolvePageFromUrl(url);
            targets.add(page);
            for (Descendant d : fetcher.getDescendants(page)) targets.add(d);
            seenBaseUrls.add(page.baseUrl());
        }
        ExportStats stats = new ExportStats(targets.size());
        runExport(settings, outputRoot, lockfile, targets, stats);
        runCleanup(settings, lockfile, seenBaseUrls, stats);
        return stats;
    }

    /** Export every page in the given space URLs. Equivalent of {@code cme spaces}. */
    public ExportStats exportSpaces(List<String> urls) {
        AppConfig settings = configStore.loadEffective();
        Path outputRoot = resolveOutputRoot(settings);
        LockfileManager lockfile = newLockfile(settings, outputRoot);

        Set<String> seenBaseUrls = new LinkedHashSet<>();
        List<ExportablePage> targets = new ArrayList<>();
        for (String url : urls) {
            Space space = fetcher.resolveSpaceFromUrl(url);
            seenBaseUrls.add(space.baseUrl());
            collectSpacePages(space, targets);
        }
        ExportStats stats = new ExportStats(targets.size());
        runExport(settings, outputRoot, lockfile, targets, stats);
        runCleanup(settings, lockfile, seenBaseUrls, stats);
        return stats;
    }

    /** Export every space in each organization base URL. Equivalent of {@code cme orgs}. */
    public ExportStats exportOrganizations(List<String> baseUrls) {
        AppConfig settings = configStore.loadEffective();
        Path outputRoot = resolveOutputRoot(settings);
        LockfileManager lockfile = newLockfile(settings, outputRoot);

        Set<String> seenBaseUrls = new LinkedHashSet<>();
        List<ExportablePage> targets = new ArrayList<>();
        for (String baseUrl : baseUrls) {
            Organization org = fetcher.resolveOrganizationFromUrl(baseUrl);
            seenBaseUrls.add(org.baseUrl());
            for (Space space : org.spaces()) collectSpacePages(space, targets);
        }
        ExportStats stats = new ExportStats(targets.size());
        runExport(settings, outputRoot, lockfile, targets, stats);
        runCleanup(settings, lockfile, seenBaseUrls, stats);
        return stats;
    }

    private void collectSpacePages(Space space, List<ExportablePage> out) {
        if (space.homepage() == null) {
            LOG.warn("Space '{}' (key: {}) has no homepage. No pages will be exported.",
                    space.name(), space.key());
            return;
        }
        Page homepage = fetcher.getPage(space.homepage(), space.baseUrl());
        out.add(homepage);
        for (Descendant d : fetcher.getDescendants(homepage)) out.add(d);
    }

    private void runExport(AppConfig settings, Path outputRoot, LockfileManager lockfile,
                           List<? extends ExportablePage> targets, ExportStats stats) {
        TemplateVars templateVars = new TemplateVars(new FilenameSanitizer(settings.export()));
        PageRenderer renderer = new PageRenderer(fetcher, settings.export(), templateVars, outputRoot);
        PageExporter exporter = new PageExporter(fetcher, settings.export(), templateVars,
                renderer, lockfile, stats, outputRoot);

        // mark seen + filter unchanged
        lockfile.markSeen(targets);
        List<ExportablePage> toExport = new ArrayList<>();
        for (ExportablePage target : targets) {
            Path pendingExportPath = pendingExportPath(target, settings, templateVars, outputRoot);
            if (lockfile.shouldExport(target, pendingExportPath)) {
                toExport.add(target);
            } else {
                stats.incSkipped();
            }
        }

        int skippedCount = targets.size() - toExport.size();
        if (toExport.isEmpty()) {
            LOG.info("All {} page(s) unchanged — nothing to export.", targets.size());
            return;
        }
        if (skippedCount > 0) {
            LOG.info("{} page(s) unchanged — skipping; exporting {} page(s)",
                    skippedCount, toExport.size());
        }

        ParallelExportRunner.RenderingContextFactory rcFactory = page ->
                new RenderingContext(page, settings.export(), fetcher, templateVars, outputRoot);
        boolean serial = "DEBUG".equalsIgnoreCase(settings.export().logLevel())
                || settings.connectionConfig().maxWorkers() <= 1;
        LOG.info("Exporting {} page(s) in {} mode (max_workers={})",
                toExport.size(),
                serial ? "serial" : "parallel",
                settings.connectionConfig().maxWorkers());
        ParallelExportRunner runner = new ParallelExportRunner(
                fetcher, exporter, lockfile, rcFactory,
                settings.connectionConfig().maxWorkers(),
                serial);
        runner.run(toExport, stats);
    }

    private void runCleanup(AppConfig settings, LockfileManager lockfile, Set<String> baseUrls,
                            ExportStats stats) {
        if (!settings.export().cleanupStale()) return;
        StaleCleanup cleanup = new StaleCleanup(apiFactory, settings);
        for (String baseUrl : baseUrls) {
            Set<String> unseen = lockfile.unseenIds();
            if (unseen.isEmpty()) continue;
            List<String> sorted = new ArrayList<>(unseen);
            sorted.sort(String::compareTo);
            Set<String> deleted = cleanup.fetchDeletedPageIds(sorted, baseUrl);
            if (!deleted.isEmpty()) {
                LOG.info("Removing {} stale page(s) from local export.", deleted.size());
            }
            lockfile.removePages(deleted, stats);
        }
    }

    private Path pendingExportPath(ExportablePage target, AppConfig settings,
                                   TemplateVars templateVars, Path outputRoot) {
        return switch (target) {
            case Page p -> Paths.get(de.skerkewitz.jcme.export.PathTemplate.render(
                    settings.export().pagePath(), templateVars.forPage(p, fetcher)));
            case Descendant d -> Paths.get(de.skerkewitz.jcme.export.PathTemplate.render(
                    settings.export().pagePath(), templateVars.forDescendant(d, fetcher)));
        };
    }

    private static LockfileManager newLockfile(AppConfig settings, Path outputRoot) {
        return new LockfileManager(outputRoot, settings.export().lockfileName(),
                settings.export().skipUnchanged());
    }

    private static Path resolveOutputRoot(AppConfig settings) {
        String configured = settings.export().outputPath();
        if (configured == null || configured.isEmpty()) return Paths.get("");
        return Paths.get(configured);
    }

}
