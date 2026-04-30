package de.skerkewitz.jcme.lockfile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Persisted lock file. Mirrors the Python {@code ConfluenceLock} pydantic model
 * (lockfile_version=2, last_export, orgs → spaces → pages → attachments).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ConfluenceLock {

    public static final int LOCKFILE_VERSION = 2;
    private static final Logger LOG = LoggerFactory.getLogger(ConfluenceLock.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @JsonProperty("lockfile_version")
    private int lockfileVersion = LOCKFILE_VERSION;

    @JsonProperty("last_export")
    private String lastExport = "";

    @JsonProperty("orgs")
    private Map<String, OrgEntry> orgs = new LinkedHashMap<>();

    public ConfluenceLock() {}

    public int lockfileVersion() { return lockfileVersion; }
    public void setLockfileVersion(int v) { this.lockfileVersion = v; }

    public String lastExport() { return lastExport; }
    public void setLastExport(String s) { this.lastExport = s; }

    public Map<String, OrgEntry> orgs() { return orgs; }
    public void setOrgs(Map<String, OrgEntry> orgs) {
        this.orgs = orgs == null ? new LinkedHashMap<>() : orgs;
    }

    /** Load from disk, returning a fresh empty lock when missing or malformed. */
    public static ConfluenceLock load(Path file) {
        if (!Files.exists(file)) return new ConfluenceLock();
        try {
            String content = Files.readString(file);
            ConfluenceLock parsed = JSON.readValue(content, ConfluenceLock.class);
            if (parsed.lockfileVersion < LOCKFILE_VERSION) {
                LOG.info("Lock file format is outdated (v{} → v{}). Starting fresh.",
                        parsed.lockfileVersion, LOCKFILE_VERSION);
                return new ConfluenceLock();
            }
            return parsed;
        } catch (IOException e) {
            LOG.warn("Failed to parse lock file: {}. Starting fresh.", file);
            return new ConfluenceLock();
        }
    }

    /** Return a flat view of all page entries, keyed by page id. */
    public Map<String, PageEntry> allPages() {
        Map<String, PageEntry> out = new LinkedHashMap<>();
        for (OrgEntry org : orgs.values()) {
            for (SpaceEntry space : org.spaces().values()) {
                out.putAll(space.pages());
            }
        }
        return out;
    }

    public PageEntry getPage(String pageId) {
        for (OrgEntry org : orgs.values()) {
            for (SpaceEntry space : org.spaces().values()) {
                PageEntry entry = space.pages().get(pageId);
                if (entry != null) return entry;
            }
        }
        return null;
    }

    public void removePage(String pageId) {
        for (OrgEntry org : orgs.values()) {
            for (SpaceEntry space : org.spaces().values()) {
                space.pages().remove(pageId);
            }
        }
    }

    public void putPage(String orgUrl, String spaceKey, String pageId, PageEntry entry) {
        OrgEntry org = orgs.computeIfAbsent(orgUrl, k -> new OrgEntry(new LinkedHashMap<>()));
        SpaceEntry space = org.spaces().computeIfAbsent(spaceKey,
                k -> new SpaceEntry(new LinkedHashMap<>()));
        space.pages().put(pageId, entry);
    }

    /**
     * Save to disk, merging with whatever is already there to handle concurrent writes.
     * Sorts maps for deterministic output and stamps {@code last_export} with the current UTC time.
     */
    public void save(Path file, java.util.Set<String> deleteIds) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());

        ConfluenceLock existing = load(file);
        for (Map.Entry<String, OrgEntry> orgEntry : orgs.entrySet()) {
            OrgEntry mergedOrg = existing.orgs.computeIfAbsent(orgEntry.getKey(),
                    k -> new OrgEntry(new LinkedHashMap<>()));
            for (Map.Entry<String, SpaceEntry> spaceEntry : orgEntry.getValue().spaces().entrySet()) {
                SpaceEntry mergedSpace = mergedOrg.spaces().computeIfAbsent(spaceEntry.getKey(),
                        k -> new SpaceEntry(new LinkedHashMap<>()));
                mergedSpace.pages().putAll(spaceEntry.getValue().pages());
            }
        }
        if (deleteIds != null) {
            for (String id : deleteIds) existing.removePage(id);
        }

        // Sort for determinism
        Map<String, OrgEntry> sortedOrgs = new TreeMap<>(existing.orgs);
        for (OrgEntry org : sortedOrgs.values()) {
            Map<String, SpaceEntry> sortedSpaces = new TreeMap<>(org.spaces());
            for (SpaceEntry space : sortedSpaces.values()) {
                Map<String, PageEntry> sortedPages = new TreeMap<>(space.pages());
                space.pages().clear();
                space.pages().putAll(sortedPages);
            }
            org.spaces().clear();
            org.spaces().putAll(sortedSpaces);
        }
        existing.orgs.clear();
        existing.orgs.putAll(sortedOrgs);
        existing.lastExport = Instant.now().toString();

        byte[] content = JSON.writeValueAsBytes(existing);
        de.skerkewitz.jcme.export.FileIO.write(file, content);

        // Update self to reflect merged state
        this.orgs = existing.orgs;
        this.lastExport = existing.lastExport;
    }
}
