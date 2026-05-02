package de.skerkewitz.jcme.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.api.exceptions.ApiException;
import de.skerkewitz.jcme.api.exceptions.AuthNotConfiguredException;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and caches authenticated Confluence/Jira clients keyed by base URL.
 *
 * <p>For standard Atlassian Cloud instances ({@code *.atlassian.net}), the Cloud ID is
 * fetched from {@code _edge/tenant_info} on first use and persisted back to the config
 * so subsequent calls can route through the API gateway (which supports scoped tokens).
 */
public final class ApiClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClientFactory.class);

    private final ConfigStore configStore;
    private final ConcurrentHashMap<String, ConfluenceClient> confluence = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JiraClient> jira = new ConcurrentHashMap<>();
    private final HttpClient probeClient;

    public ApiClientFactory(ConfigStore configStore) {
        this.configStore = configStore;
        this.probeClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Get (or create and cache) a Confluence client for the given base URL.
     * Throws {@link AuthNotConfiguredException} if no credentials are configured.
     */
    public ConfluenceClient getConfluence(BaseUrl url) {
        String normalized = UrlParsing.normalizeInstanceUrl(
                UrlParsing.ensureServiceGatewayUrl(url.value(), "confluence"));
        ConfluenceClient cached = confluence.get(normalized);
        if (cached != null) return cached;

        AppConfig settings = configStore.loadEffective();
        Optional<ApiDetails> auth = AuthLookup.find(settings.auth().confluence(), normalized);
        if (auth.isEmpty()) {
            throw new AuthNotConfiguredException(normalized, "Confluence");
        }
        ApiDetails details = auth.get();

        // api_url override skips the cloud-id probe — it's a deliberate "REST is over there" hint.
        boolean hasApiUrlOverride = details.apiUrl() != null && !details.apiUrl().isEmpty();

        if (!hasApiUrlOverride
                && (details.cloudId() == null || details.cloudId().isEmpty())
                && UrlParsing.isStandardAtlassianCloudUrl(normalized)) {
            LOG.info("Probing {}/_edge/tenant_info to auto-detect Cloud ID (5s timeout)", normalized);
            String cloudId = tryFetchCloudId(normalized);
            if (cloudId != null) {
                LOG.info("Auto-fetched Atlassian Cloud ID for {} — storing in config", normalized);
                configStore.setByKeys(java.util.List.of("auth", "confluence", normalized, "cloud_id"), cloudId);
                settings = configStore.loadEffective();
                details = AuthLookup.find(settings.auth().confluence(), normalized).orElse(details);
            } else {
                LOG.debug("No Cloud ID discovered at {}/_edge/tenant_info — proceeding without gateway routing", normalized);
            }
        }

        String sdkUrl;
        if (hasApiUrlOverride) {
            sdkUrl = UrlParsing.normalizeInstanceUrl(details.apiUrl());
            LOG.info("Using configured api_url {} for REST calls (page host: {})", sdkUrl, normalized);
        } else if (details.cloudId() != null && !details.cloudId().isEmpty()) {
            sdkUrl = UrlParsing.buildGatewayUrl("confluence", details.cloudId());
        } else {
            sdkUrl = normalized;
        }
        LOG.info("Verifying Confluence credentials against {} (timeout {}s)",
                sdkUrl, settings.connectionConfig().timeout());
        HttpExecutor http = new HttpExecutor(details, settings.connectionConfig());
        ConfluenceClient client = new ConfluenceClient(sdkUrl, http);
        try {
            client.verifyAuth();
        } catch (ApiException e) {
            LOG.warn("Confluence authentication failed for {}: {}", normalized, e.getMessage());
            throw new AuthNotConfiguredException(normalized, "Confluence", e);
        }
        LOG.info("Connected to Confluence at {}", sdkUrl);
        return confluence.computeIfAbsent(normalized, k -> client);
    }

    /**
     * Get (or create and cache) a Jira client for the given base URL.
     * If {@code url} is a Confluence gateway URL, it is auto-converted to the Jira gateway URL.
     */
    public JiraClient getJira(BaseUrl url) {
        String normalized = UrlParsing.normalizeInstanceUrl(
                UrlParsing.ensureServiceGatewayUrl(url.value(), "jira"));
        AppConfig settings = configStore.loadEffective();
        if (!settings.export().enableJiraEnrichment()) {
            throw new IllegalStateException(
                    "Jira API client requested even though Jira enrichment is disabled.");
        }
        JiraClient cached = jira.get(normalized);
        if (cached != null) return cached;

        Optional<ApiDetails> auth = AuthLookup.find(settings.auth().jira(), normalized);
        if (auth.isEmpty()) {
            throw new AuthNotConfiguredException(normalized, "Jira");
        }
        ApiDetails details = auth.get();

        boolean hasApiUrlOverride = details.apiUrl() != null && !details.apiUrl().isEmpty();

        if (!hasApiUrlOverride
                && (details.cloudId() == null || details.cloudId().isEmpty())
                && UrlParsing.isStandardAtlassianCloudUrl(normalized)) {
            String cloudId = tryFetchCloudId(normalized);
            if (cloudId != null) {
                LOG.info("Auto-fetched Atlassian Cloud ID for {} — storing in config", normalized);
                configStore.setByKeys(java.util.List.of("auth", "jira", normalized, "cloud_id"), cloudId);
                settings = configStore.loadEffective();
                details = AuthLookup.find(settings.auth().jira(), normalized).orElse(details);
            }
        }

        String sdkUrl;
        if (hasApiUrlOverride) {
            sdkUrl = UrlParsing.normalizeInstanceUrl(details.apiUrl());
            LOG.info("Using configured api_url {} for Jira REST calls (page host: {})", sdkUrl, normalized);
        } else if (details.cloudId() != null && !details.cloudId().isEmpty()) {
            sdkUrl = UrlParsing.buildGatewayUrl("jira", details.cloudId());
        } else {
            sdkUrl = normalized;
        }
        HttpExecutor http = new HttpExecutor(details, settings.connectionConfig());
        JiraClient client = new JiraClient(sdkUrl, http);
        try {
            client.verifyAuth();
        } catch (ApiException e) {
            LOG.warn("Jira authentication failed for {}: {}", normalized, e.getMessage());
            throw new AuthNotConfiguredException(normalized, "Jira", e);
        }
        LOG.info("Connected to Jira at {}", sdkUrl);
        return jira.computeIfAbsent(normalized, k -> client);
    }

    public void invalidateConfluence(BaseUrl url) {
        confluence.remove(UrlParsing.normalizeInstanceUrl(url.value()));
    }

    public void invalidateJira(BaseUrl url) {
        jira.remove(UrlParsing.normalizeInstanceUrl(url.value()));
    }

    /** Try to fetch the Atlassian Cloud ID from {@code _edge/tenant_info}. */
    String tryFetchCloudId(String baseUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/_edge/tenant_info"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = probeClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 400) {
                JsonNode node = RestClient.jsonMapper().readTree(resp.body());
                JsonNode cloudId = node.get("cloudId");
                if (cloudId != null && !cloudId.isNull()) {
                    return cloudId.asText();
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not fetch Cloud ID from {}/_edge/tenant_info: {}", baseUrl, e.getMessage());
        }
        return null;
    }
}
