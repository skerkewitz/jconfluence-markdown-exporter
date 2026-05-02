package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.api.ConfluenceClient;
import de.skerkewitz.jcme.api.exceptions.ApiException;
import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.fetch.ConfluenceFetcher;
import de.skerkewitz.jcme.lockfile.AttachmentEntry;
import de.skerkewitz.jcme.lockfile.LockfileManager;
import de.skerkewitz.jcme.markdown.PageRenderer;
import de.skerkewitz.jcme.markdown.RenderingContext;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single-page export pipeline:
 * <ol>
 *   <li>Filter the page's attachments down to the ones referenced (or all when
 *       {@code attachment_export_all} is set).</li>
 *   <li>Download attachments to disk, skipping unchanged versions and reaping orphans.</li>
 *   <li>Render Markdown via {@link PageRenderer} and write the file.</li>
 * </ol>
 */
public final class PageExporter {

    private static final Logger LOG = LoggerFactory.getLogger(PageExporter.class);

    private final ConfluenceFetcher fetcher;
    private final ExportConfig export;
    private final TemplateVars templateVars;
    private final PageRenderer renderer;
    private final LockfileManager lockfile;
    private final ExportStats stats;
    private final Path outputRoot;

    public PageExporter(ConfluenceFetcher fetcher, ExportConfig export, TemplateVars templateVars,
                        PageRenderer renderer, LockfileManager lockfile, ExportStats stats,
                        Path outputRoot) {
        this.fetcher = fetcher;
        this.export = export;
        this.templateVars = templateVars;
        this.renderer = renderer;
        this.lockfile = lockfile;
        this.stats = stats;
        this.outputRoot = outputRoot;
    }

    /** Export a single page. Returns the attachment entries to record in the lockfile. */
    public Map<String, AttachmentEntry> exportPage(Page page) throws IOException {
        if (page.isInaccessible()) {
            LOG.warn("Skipping export for inaccessible page id={}", page.id());
            return Map.of();
        }
        LOG.info("Exporting page id={} '{}' (v{})", page.id(), page.title(),
                page.version() == null ? "?" : page.version().number());

        RenderingContext rc = new RenderingContext(page, export, fetcher, templateVars, outputRoot);
        Map<String, AttachmentEntry> attachmentEntries = exportAttachments(page, rc);

        Path mdPath = outputRoot.resolve(rc.pageExportPath(page));
        LOG.debug("Rendering markdown for page id={} to {}", page.id(), mdPath);
        long renderStart = System.currentTimeMillis();
        String markdown = renderer.render(page);
        LOG.debug("Rendered page id={} ({} chars) in {} ms", page.id(), markdown.length(),
                System.currentTimeMillis() - renderStart);
        FileIO.writeString(mdPath, markdown);
        LOG.info("Wrote {} bytes to {}", markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, mdPath);
        return attachmentEntries;
    }

    private Map<String, AttachmentEntry> exportAttachments(Page page, RenderingContext rc) {
        Map<String, AttachmentEntry> oldEntries = lockfile.attachmentEntriesForPage(page.id());
        Map<String, AttachmentEntry> newEntries = new LinkedHashMap<>();
        ConfluenceClient client = fetcher.apiFactory().getConfluence(page.baseUrl());

        List<Attachment> selected = selectAttachmentsForExport(page);
        if (!selected.isEmpty()) {
            LOG.info("Page id={} '{}': processing {} attachment(s)",
                    page.id(), page.title(), selected.size());
        }
        for (Attachment attachment : selected) {
            String attId = attachment.id();
            int attVersion = attachment.version() == null ? 0 : attachment.version().number();
            Path target = outputRoot.resolve(rc.attachmentExportPath(attachment));

            AttachmentEntry old = oldEntries.get(attId);
            if (old != null && old.version() == attVersion) {
                Path expected = outputRoot.resolve(old.path());
                if (Files.exists(expected)) {
                    newEntries.put(attId, old);
                    LOG.debug("Skipping unchanged attachment '{}' (v{})", attachment.title(), attVersion);
                    stats.incAttachmentsSkipped();
                    continue;
                }
            }

            try {
                LOG.debug("Downloading attachment '{}' ({} bytes expected) from {}",
                        attachment.title(), attachment.fileSize(), attachment.downloadLink());
                long start = System.currentTimeMillis();
                byte[] bytes = client.downloadAttachment(attachment.downloadLink());
                FileIO.write(target, bytes);
                LOG.info("Saved attachment '{}' ({} bytes) in {} ms -> {}",
                        attachment.title(), bytes.length, System.currentTimeMillis() - start, target);
                stats.incAttachmentsExported();
                if (attVersion > 0) {
                    String relPath = rc.attachmentExportPath(attachment).toString().replace('\\', '/');
                    newEntries.put(attId, new AttachmentEntry(attVersion, relPath));
                }
            } catch (ApiException e) {
                LOG.warn("Failed to download attachment '{}': {}. Skipping.",
                        attachment.title(), e.getMessage());
                stats.incAttachmentsFailed();
            } catch (IOException e) {
                LOG.warn("I/O error writing attachment '{}': {}. Skipping.",
                        attachment.title(), e.getMessage());
                stats.incAttachmentsFailed();
            }
        }

        // Reap orphan files left behind when an attachment moves to a new path.
        for (Map.Entry<String, AttachmentEntry> entry : oldEntries.entrySet()) {
            AttachmentEntry now = newEntries.get(entry.getKey());
            if (now != null && !entry.getValue().path().equals(now.path())) {
                FileIO.deleteIfExists(outputRoot.resolve(entry.getValue().path()));
                LOG.info("Deleted old attachment file: {}", entry.getValue().path());
                stats.incAttachmentsRemoved();
            }
        }
        return newEntries;
    }

    /** Filter attachments to the ones actually referenced in the page body, plus drawio pairs. */
    private List<Attachment> selectAttachmentsForExport(Page page) {
        if (export.attachmentExportAll()) return page.attachments();
        java.util.List<Attachment> out = new java.util.ArrayList<>();
        String body = page.body() == null ? "" : page.body();
        String bodyExport = page.bodyExport() == null ? "" : page.bodyExport();
        Set<String> linkTargets = extractAttachmentLikeTargets(body, bodyExport);
        for (Attachment a : page.attachments()) {
            String name = a.filename();
            if (name.endsWith(".drawio") && body.contains("diagramName=" + a.title())) {
                out.add(a);
                continue;
            }
            if ((name.endsWith(".drawio.png") || name.endsWith(".drawio"))
                    && bodyExport.contains(a.title().replace(" ", "%20"))) {
                out.add(a);
                continue;
            }
            if (a.fileId() != null && !a.fileId().isEmpty() && body.contains(a.fileId())) {
                out.add(a);
                continue;
            }
            if (a.fileId() != null && !a.fileId().isEmpty() && bodyExport.contains(a.fileId())) {
                out.add(a);
                continue;
            }
            if (isReferencedByDownloadUrl(a, linkTargets)) {
                out.add(a);
            }
        }
        return out;
    }

    private static Set<String> extractAttachmentLikeTargets(String... htmlSnippets) {
        Set<String> out = new LinkedHashSet<>();
        for (String html : htmlSnippets) {
            if (html == null || html.isBlank()) continue;
            Document doc = Jsoup.parseBodyFragment(html);
            for (Element el : doc.select("img[src],a[href]")) {
                String url = el.hasAttr("src") ? el.attr("src") : el.attr("href");
                if (url == null || url.isBlank()) continue;
                String decoded = decode(url);
                if (looksLikeConfluenceAttachmentUrl(decoded)) {
                    out.add(decoded);
                }
            }
        }
        return out;
    }

    private static boolean isReferencedByDownloadUrl(Attachment a, Set<String> urls) {
        if (urls.isEmpty()) return false;
        String title = decode(a.title());
        String download = stripQuery(decode(a.downloadLink()));
        for (String raw : urls) {
            String url = stripQuery(raw);
            if (!looksLikeConfluenceAttachmentUrl(url)) continue;
            if (!download.isEmpty() && url.contains(download)) return true;
            int slash = url.lastIndexOf('/');
            String last = slash >= 0 ? url.substring(slash + 1) : url;
            if (!title.isEmpty() && decode(last).equals(title)) return true;
        }
        return false;
    }

    private static boolean looksLikeConfluenceAttachmentUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("/download/attachments/") || lower.contains("/download/thumbnails/");
    }

    private static String stripQuery(String s) {
        if (s == null) return "";
        int q = s.indexOf('?');
        return q >= 0 ? s.substring(0, q) : s;
    }

    private static String decode(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return s;
        }
    }
}
