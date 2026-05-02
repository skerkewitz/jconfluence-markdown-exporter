package de.skerkewitz.jcme.lockfile;

import de.skerkewitz.jcme.api.BaseUrl;
import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.export.ExportStats;
import de.skerkewitz.jcme.export.FilenameSanitizer;
import de.skerkewitz.jcme.export.TemplateVars;
import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.markdown.RenderingContext;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.PageId;
import de.skerkewitz.jcme.model.Space;
import de.skerkewitz.jcme.model.User;
import de.skerkewitz.jcme.model.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LockfileManagerTest {

    private static final BaseUrl BASE = BaseUrl.of("https://x.atlassian.net");

    private static class StubFetcher extends ConfluenceFetcher {
        StubFetcher() { super(null, null); }
        Map<PageId, Page> pages = new HashMap<>();
        @Override public Page getPage(PageId pageId, BaseUrl baseUrl) {
            return pages.getOrDefault(pageId, Page.inaccessible(pageId, baseUrl));
        }
    }

    private static Page page(long id, int version, String title, Space space) {
        return new Page(BASE, PageId.of(id), title, space, List.of(),
                new Version(version, User.empty(), "", ""),
                "<p>x</p>", "", "", List.of(), List.of());
    }

    private RenderingContext rc(Page page, Path output) {
        ExportConfig export = ExportConfig.defaults();
        return new RenderingContext(page, export, new StubFetcher(),
                new TemplateVars(new FilenameSanitizer(export)), output);
    }

    @Test
    void disabled_manager_short_circuits_all_operations(@TempDir Path tmp) {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", false);

        assertThat(mgr.shouldExport(page(1, 1, "T", new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "K", "", null)),
                tmp.resolve("anything"))).isTrue();
        assertThat(mgr.unseenIds()).isEmpty();
        assertThat(mgr.attachmentEntriesForPage(PageId.of(1))).isEmpty();
        // Should not write any file
        assertThat(tmp.toFile().list()).isEmpty();
    }

    @Test
    void record_page_persists_lockfile_entry(@TempDir Path tmp) throws Exception {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", true);
        Page p = page(1, 5, "Hello", new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "Space", "", null));

        mgr.recordPage(p, new LinkedHashMap<>(), rc(p, tmp));

        assertThat(Files.exists(tmp.resolve("lock.json"))).isTrue();
        ConfluenceLock loaded = ConfluenceLock.load(tmp.resolve("lock.json"));
        PageEntry entry = loaded.getPage("1");
        assertThat(entry).isNotNull();
        assertThat(entry.title()).isEqualTo("Hello");
        assertThat(entry.version()).isEqualTo(5);
    }

    @Test
    void should_export_returns_true_for_unknown_page(@TempDir Path tmp) {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", true);
        Space space = new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "Space", "", null);
        Page p = page(99, 1, "New", space);

        assertThat(mgr.shouldExport(p, tmp.resolve("Space/New.md"))).isTrue();
    }

    @Test
    void should_export_returns_false_for_unchanged_page(@TempDir Path tmp) throws Exception {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", true);
        Space space = new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "Space", "", null);
        Page p = page(1, 5, "Hello", space);
        // First export: record
        Path mdPath = tmp.resolve(rc(p, tmp).pageExportPath(p));
        Files.createDirectories(mdPath.getParent());
        Files.writeString(mdPath, "x");
        mgr.recordPage(p, new LinkedHashMap<>(), rc(p, tmp));

        // Re-load the manager (simulates a new run)
        LockfileManager reloaded = new LockfileManager(tmp, "lock.json", true);
        assertThat(reloaded.shouldExport(p, rc(p, tmp).pageExportPath(p))).isFalse();
    }

    @Test
    void should_export_returns_true_when_version_bumped(@TempDir Path tmp) throws Exception {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", true);
        Space space = new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "Space", "", null);
        Page v1 = page(1, 1, "Hello", space);
        Files.createDirectories(tmp.resolve(rc(v1, tmp).pageExportPath(v1)).getParent());
        Files.writeString(tmp.resolve(rc(v1, tmp).pageExportPath(v1)), "x");
        mgr.recordPage(v1, new LinkedHashMap<>(), rc(v1, tmp));

        Page v2 = page(1, 2, "Hello", space);
        LockfileManager reloaded = new LockfileManager(tmp, "lock.json", true);
        assertThat(reloaded.shouldExport(v2, rc(v2, tmp).pageExportPath(v2))).isTrue();
    }

    @Test
    void should_export_returns_true_when_local_file_missing(@TempDir Path tmp) {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", true);
        Space space = new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "Space", "", null);
        Page p = page(1, 1, "Hello", space);
        // Record, but don't create the local file
        mgr.recordPage(p, new LinkedHashMap<>(), rc(p, tmp));

        LockfileManager reloaded = new LockfileManager(tmp, "lock.json", true);
        assertThat(reloaded.shouldExport(p, rc(p, tmp).pageExportPath(p))).isTrue();
    }

    @Test
    void unseen_ids_returns_pages_not_marked_seen_this_run(@TempDir Path tmp) {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", true);
        Space space = new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "Space", "", null);
        Page p1 = page(1, 1, "P1", space);
        Page p2 = page(2, 1, "P2", space);
        mgr.recordPage(p1, new LinkedHashMap<>(), rc(p1, tmp));
        mgr.recordPage(p2, new LinkedHashMap<>(), rc(p2, tmp));

        LockfileManager reloaded = new LockfileManager(tmp, "lock.json", true);
        reloaded.markSeenIds(List.of(PageId.of(1L))); // only p1 seen

        assertThat(reloaded.unseenIds()).containsExactlyInAnyOrder(PageId.of(2L));
    }

    @Test
    void remove_pages_deletes_local_files_and_lockfile_entries(@TempDir Path tmp) throws Exception {
        LockfileManager mgr = new LockfileManager(tmp, "lock.json", true);
        Space space = new Space(BASE, de.skerkewitz.jcme.model.SpaceKey.of("K"), "Space", "", null);
        Page p = page(1, 1, "Doomed", space);
        Path mdPath = tmp.resolve(rc(p, tmp).pageExportPath(p));
        Files.createDirectories(mdPath.getParent());
        Files.writeString(mdPath, "x");
        mgr.recordPage(p, new LinkedHashMap<>(), rc(p, tmp));

        LockfileManager reloaded = new LockfileManager(tmp, "lock.json", true);
        ExportStats stats = new ExportStats();
        reloaded.removePages(Set.of(PageId.of(1L)), stats);

        assertThat(Files.exists(mdPath)).isFalse();
        assertThat(stats.removed()).isEqualTo(1);
        ConfluenceLock check = ConfluenceLock.load(tmp.resolve("lock.json"));
        assertThat(check.getPage("1")).isNull();
    }
}
