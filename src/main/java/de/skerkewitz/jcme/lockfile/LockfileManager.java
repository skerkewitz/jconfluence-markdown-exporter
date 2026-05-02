package de.skerkewitz.jcme.lockfile;

import de.skerkewitz.jcme.export.ExportStats;
import de.skerkewitz.jcme.export.FileIO;
import de.skerkewitz.jcme.markdown.RenderingContext;
import de.skerkewitz.jcme.model.ExportablePage;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.PageId;
import de.skerkewitz.jcme.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks page versions, attachment versions, and "seen this run" state for skip-unchanged
 * and stale-file cleanup. Equivalent to the Python {@code LockfileManager}.
 *
 * <p>Each export run creates one instance scoped to the configured output directory; this
 * replaces the Python class-level static state with explicit ownership.
 */
public final class LockfileManager {

    private static final Logger LOG = LoggerFactory.getLogger(LockfileManager.class);

    private final Path outputPath;
    private final Path lockfilePath;
    private final boolean enabled;
    private final ConfluenceLock lock;
    private final Map<String, PageEntry> snapshot;
    private final Set<String> seenPageIds = new HashSet<>();
    private final ReentrantLock writeLock = new ReentrantLock();

    public LockfileManager(Path outputPath, String lockfileName, boolean enabled) {
        this.outputPath = outputPath;
        this.lockfilePath = outputPath.resolve(lockfileName);
        this.enabled = enabled;
        this.lock = enabled ? ConfluenceLock.load(lockfilePath) : null;
        this.snapshot = enabled
                ? new LinkedHashMap<>(lock.allPages())
                : Map.of();
        if (enabled) {
            LOG.debug("Lockfile initialized: {} ({} tracked page(s))",
                    lockfilePath, snapshot.size());
        }
    }

    public boolean enabled() { return enabled; }
    public Path outputPath() { return outputPath; }
    public Path lockfilePath() { return lockfilePath; }

    /** Return any attachment entries previously recorded for the page, or empty. */
    public Map<String, AttachmentEntry> attachmentEntriesForPage(PageId pageId) {
        if (!enabled) return Map.of();
        PageEntry entry = lock.getPage(pageId.toString());
        return entry != null ? entry.attachments() : Map.of();
    }

    /** Record a successful page export, atomically merging with the existing lockfile. */
    public void recordPage(Page page, Map<String, AttachmentEntry> attachmentEntries,
                           RenderingContext rc) {
        if (!enabled) return;
        Version v = page.version();
        if (v == null) {
            LOG.warn("Page {} has no version info. Skipping lock entry.", page.id());
            return;
        }
        writeLock.lock();
        try {
            String exportPath = rc.pageExportPath(page).toString().replace('\\', '/');
            PageEntry entry = new PageEntry(page.title(), v.number(), exportPath,
                    attachmentEntries == null ? new LinkedHashMap<>() : attachmentEntries);
            String spaceKeyForLock = page.space() != null && page.space().key() != null
                    ? page.space().key().value() : "";
            lock.putPage(page.baseUrl().value(), spaceKeyForLock, page.id().toString(), entry);
            try {
                lock.save(lockfilePath, null);
            } catch (IOException e) {
                LOG.warn("Failed to save lockfile {}: {}", lockfilePath, e.getMessage());
            }
            seenPageIds.add(page.id().toString());
        } finally {
            writeLock.unlock();
        }
    }

    /** Mark page ids as seen in this run (so they're excluded from stale cleanup). */
    public void markSeen(Iterable<? extends ExportablePage> pages) {
        if (!enabled) return;
        for (ExportablePage p : pages) seenPageIds.add(p.id().toString());
    }

    public void markSeenIds(Iterable<PageId> ids) {
        if (!enabled) return;
        for (PageId id : ids) seenPageIds.add(id.toString());
    }

    /**
     * Whether {@code page} should be re-exported: true if not yet tracked, version differs,
     * export-path changed, or the local file is missing.
     */
    public boolean shouldExport(ExportablePage page, Path resolvedExportPath) {
        if (!enabled) return true;
        String pageId = page.id().toString();
        PageEntry entry = lock.getPage(pageId);
        if (entry == null) {
            LOG.debug("Page id={} not in lockfile — will export", pageId);
            return true;
        }
        Version v = page.version();
        if (v == null) return true;

        Path expected = outputPath.resolve(entry.exportPath());
        if (!Files.exists(expected)) {
            LOG.debug("Page id={} output file missing — will re-export", pageId);
            return true;
        }
        String newPath = resolvedExportPath.toString().replace('\\', '/');
        if (entry.version() != v.number() || !entry.exportPath().equals(newPath)) {
            LOG.debug("Page id={} changed (v{} → v{}) — will export",
                    pageId, entry.version(), v.number());
            return true;
        }
        LOG.debug("Page id={} unchanged (v{}) — skipping", pageId, entry.version());
        return false;
    }

    /** Return lockfile page IDs that were not encountered during this run. */
    public Set<PageId> unseenIds() {
        if (!enabled) return Set.of();
        Set<String> all = new HashSet<>(lock.allPages().keySet());
        all.removeAll(seenPageIds);
        Set<PageId> result = new HashSet<>(all.size());
        for (String s : all) {
            try {
                result.add(PageId.parse(s));
            } catch (IllegalArgumentException e) {
                LOG.warn("Skipping unparseable page id in lockfile: {}", s);
            }
        }
        return result;
    }

    /**
     * Delete files + lockfile entries for pages confirmed deleted from Confluence,
     * plus orphan files for pages whose export-path moved. Updates the supplied stats.
     */
    public void removePages(Set<PageId> deletedIds, ExportStats stats) {
        if (!enabled) return;
        Set<String> toDelete = new HashSet<>();

        // Handle moved pages: delete the old file when export_path changed mid-run.
        for (String pageId : seenPageIds) {
            PageEntry old = snapshot.get(pageId);
            PageEntry now = lock.getPage(pageId);
            if (old != null && now != null && !old.exportPath().equals(now.exportPath())) {
                FileIO.deleteIfExists(outputPath.resolve(old.exportPath()));
                LOG.info("Deleted old path for moved page: {}", old.exportPath());
            }
        }

        for (PageId pageId : deletedIds) {
            String key = pageId.toString();
            PageEntry entry = lock.getPage(key);
            if (entry != null) {
                FileIO.deleteIfExists(outputPath.resolve(entry.exportPath()));
                LOG.info("Deleted removed page: {}", entry.exportPath());
                toDelete.add(key);
            }
        }

        if (!toDelete.isEmpty()) {
            writeLock.lock();
            try {
                lock.save(lockfilePath, toDelete);
            } catch (IOException e) {
                LOG.warn("Failed to save lockfile during cleanup: {}", e.getMessage());
            } finally {
                writeLock.unlock();
            }
        }
        for (int i = 0; i < toDelete.size(); i++) stats.incRemoved();
    }
}
