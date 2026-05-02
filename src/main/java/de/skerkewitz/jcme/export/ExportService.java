package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.api.ApiClientFactory;
import de.skerkewitz.jcme.api.BaseUrl;
import de.skerkewitz.jcme.cli.progress.ProgressUi;
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
import de.skerkewitz.jcme.model.PageId;
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
    private final ProgressUi progress;

    public ExportService(ConfigStore configStore) {
        this(configStore, ProgressUi.silent());
    }

    public ExportService(ConfigStore configStore, ProgressUi progress) {
        this.configStore = configStore;
        this.apiFactory = new ApiClientFactory(configStore);
        this.fetcher = new ConfluenceFetcher(apiFactory, configStore);
        this.progress = progress;
    }

    public ExportService(ConfigStore configStore, ApiClientFactory apiFactory,
                         ConfluenceFetcher fetcher) {
        this(configStore, apiFactory, fetcher, ProgressUi.silent());
    }

    public ExportService(ConfigStore configStore, ApiClientFactory apiFactory,
                         ConfluenceFetcher fetcher, ProgressUi progress) {
        this.configStore = configStore;
        this.apiFactory = apiFactory;
        this.fetcher = fetcher;
        this.progress = progress;
    }

    public ConfluenceFetcher fetcher() { return fetcher; }
    public ApiClientFactory apiFactory() { return apiFactory; }
    public ProgressUi progress() { return progress; }

    /** Export one or more pages by URL. Equivalent of {@code cme pages URL...}. */
    public ExportStats exportPages(List<String> urls) {
        AppConfig settings = configStore.loadEffective();
        Path outputRoot = resolveOutputRoot(settings);
        LOG.info("Output root: {}", outputRoot.toAbsolutePath());
        LockfileManager lockfile = newLockfile(settings, outputRoot);

        progress.phase("Resolving " + urls.size() + " page URL(s)");
        Set<BaseUrl> seenBaseUrls = new LinkedHashSet<>();
        List<Page> pages = new ArrayList<>();
        for (String url : urls) {
            progress.status(url);
            Page page = fetcher.resolvePageFromUrl(url);
            pages.add(page);
            seenBaseUrls.add(page.baseUrl());
        }
        progress.clearStatus();

        ExportStats stats = new ExportStats(pages.size());
        runExport(settings, outputRoot, lockfile, pages, stats);
        runCleanup(settings, lockfile, seenBaseUrls, stats);
        return stats;
    }

    /** Export pages and all their descendants. Equivalent of {@code cme pages-with-descendants}. */
    public ExportStats exportPagesWithDescendants(List<String> urls) {
        AppConfig settings = configStore.loadEffective();
        Path outputRoot = resolveOutputRoot(settings);
        LockfileManager lockfile = newLockfile(settings, outputRoot);

        progress.phase("Resolving " + urls.size() + " page URL(s) + descendants");
        Set<BaseUrl> seenBaseUrls = new LinkedHashSet<>();
        List<ExportablePage> targets = new ArrayList<>();
        for (String url : urls) {
            progress.status(url);
            Page page = fetcher.resolvePageFromUrl(url);
            targets.add(page);
            for (Descendant d : fetcher.getDescendants(page)) targets.add(d);
            seenBaseUrls.add(page.baseUrl());
        }
        progress.clearStatus();

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

        progress.phase("Resolving " + urls.size() + " space URL(s)");
        Set<BaseUrl> seenBaseUrls = new LinkedHashSet<>();
        List<ExportablePage> targets = new ArrayList<>();
        for (String url : urls) {
            progress.status(url);
            Space space = fetcher.resolveSpaceFromUrl(url);
            seenBaseUrls.add(space.baseUrl());
            collectSpacePages(space, targets);
        }
        progress.clearStatus();

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

        progress.phase("Listing spaces in " + baseUrls.size() + " organization(s)");
        Set<BaseUrl> seenBaseUrls = new LinkedHashSet<>();
        List<ExportablePage> targets = new ArrayList<>();
        for (String baseUrl : baseUrls) {
            progress.status(baseUrl);
            Organization org = fetcher.resolveOrganizationFromUrl(BaseUrl.of(baseUrl));
            seenBaseUrls.add(org.baseUrl());
            for (Space space : org.spaces()) collectSpacePages(space, targets);
        }
        progress.clearStatus();

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
        Page homepage = fetcher.getPage(PageId.of(space.homepage()), space.baseUrl());
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
            progress.phase("All " + targets.size() + " page(s) unchanged — nothing to export");
            return;
        }
        boolean serial = settings.export().logLevel() == de.skerkewitz.jcme.config.LogLevel.DEBUG
                || settings.connectionConfig().maxWorkers() <= 1;
        String phaseLabel = "Exporting " + toExport.size() + " page(s) "
                + (serial ? "serially" : "in parallel (" + settings.connectionConfig().maxWorkers() + " workers)")
                + (skippedCount > 0 ? " (" + skippedCount + " skipped)" : "");
        progress.phase(phaseLabel);

        ParallelExportRunner.RenderingContextFactory rcFactory = page ->
                new RenderingContext(page, settings.export(), fetcher, templateVars, outputRoot);
        ParallelExportRunner runner = new ParallelExportRunner(
                fetcher, exporter, lockfile, rcFactory,
                settings.connectionConfig().maxWorkers(),
                serial,
                progress, toExport.size());
        runner.run(toExport, stats);
    }

    private void runCleanup(AppConfig settings, LockfileManager lockfile, Set<BaseUrl> baseUrls,
                            ExportStats stats) {
        if (!settings.export().cleanupStale()) return;
        StaleCleanup cleanup = new StaleCleanup(apiFactory, settings);
        for (BaseUrl baseUrl : baseUrls) {
            Set<PageId> unseen = lockfile.unseenIds();
            if (unseen.isEmpty()) continue;
            progress.phase("Checking " + unseen.size() + " unseen page(s) for removal");
            List<PageId> sorted = new ArrayList<>(unseen);
            sorted.sort((a, b) -> a.toString().compareTo(b.toString()));
            progress.status(baseUrl.value());
            Set<PageId> deleted = cleanup.fetchDeletedPageIds(sorted, baseUrl);
            progress.clearStatus();
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
