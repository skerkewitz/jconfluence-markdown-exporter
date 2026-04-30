package de.skerkewitz.jcme.export;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.api.ApiClientFactory;
import de.skerkewitz.jcme.api.ConfluenceClient;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.lockfile.LockfileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Identify pages that disappeared from Confluence between exports and trigger their cleanup.
 *
 * <p>Uses the v2 REST API (multiple {@code id=} query params) when {@code use_v2_api} is set,
 * else the v1 CQL {@code id in (...)} content search (capped at {@value #CQL_MAX_BATCH_SIZE}).
 * Per-batch failures are isolated: affected ids are assumed alive so they're never falsely
 * pruned from the lockfile.
 */
public final class StaleCleanup {

    public static final int CQL_MAX_BATCH_SIZE = 25;
    private static final Logger LOG = LoggerFactory.getLogger(StaleCleanup.class);

    private final ApiClientFactory apiFactory;
    private final AppConfig config;

    public StaleCleanup(ApiClientFactory apiFactory, AppConfig config) {
        this.apiFactory = apiFactory;
        this.config = config;
    }

    public Set<String> fetchDeletedPageIds(List<String> pageIds, String baseUrl) {
        if (pageIds.isEmpty()) return Set.of();
        boolean useV2 = config.connectionConfig().useV2Api();
        int configuredBatch = config.export().existenceCheckBatchSize();
        int batchSize = useV2 ? configuredBatch : Math.min(configuredBatch, CQL_MAX_BATCH_SIZE);
        int batches = (pageIds.size() + batchSize - 1) / batchSize;
        LOG.debug("Checking existence of {} page(s) in {} batch(es) via {} API",
                pageIds.size(), batches, useV2 ? "v2" : "v1 CQL");

        Set<String> existing = new HashSet<>();
        ConfluenceClient client = apiFactory.getConfluence(baseUrl);
        for (int i = 0; i < pageIds.size(); i += batchSize) {
            List<String> batch = pageIds.subList(i, Math.min(i + batchSize, pageIds.size()));
            try {
                if (useV2) existing.addAll(fetchV2(client, batch));
                else existing.addAll(fetchCql(client, batch));
            } catch (Exception e) {
                LOG.warn("Failed to check page existence for batch ({} IDs). "
                        + "Skipping deletion for these pages: {}", batch.size(), e.getMessage());
                existing.addAll(batch);
            }
        }

        Set<String> deleted = new HashSet<>(pageIds);
        deleted.removeAll(existing);
        return deleted;
    }

    public void run(LockfileManager lockfile, String baseUrl) {
        if (!config.export().cleanupStale()) {
            LOG.debug("Stale page cleanup disabled — skipping.");
            return;
        }
        Set<String> unseen = lockfile.unseenIds();
        if (unseen.isEmpty()) {
            LOG.debug("No unseen pages in lockfile — nothing to clean up.");
            return;
        }
        List<String> sorted = new ArrayList<>(unseen);
        sorted.sort(String::compareTo);
        Set<String> deleted = fetchDeletedPageIds(sorted, baseUrl);
        if (!deleted.isEmpty()) {
            LOG.info("Removing {} stale page(s) from local export.", deleted.size());
        }
        lockfile.removePages(deleted, new ExportStats()); // stats handled by caller separately
    }

    private static Set<String> fetchV2(ConfluenceClient client, List<String> batch) {
        JsonNode response = client.getV2Pages(batch);
        Set<String> ids = new HashSet<>();
        if (response == null) return ids;
        for (JsonNode node : response.path("results")) {
            ids.add(node.path("id").asText());
        }
        return ids;
    }

    private static Set<String> fetchCql(ConfluenceClient client, List<String> batch) {
        String cql = "id in (" + String.join(",", batch) + ")";
        JsonNode response = client.searchCql(cql, null, batch.size());
        Set<String> ids = new HashSet<>();
        if (response == null) return ids;
        for (JsonNode node : response.path("results")) {
            ids.add(node.path("id").asText());
        }
        return ids;
    }
}
