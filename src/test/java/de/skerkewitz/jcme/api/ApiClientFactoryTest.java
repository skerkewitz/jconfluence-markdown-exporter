package de.skerkewitz.jcme.api;

import de.skerkewitz.jcme.api.exceptions.AuthNotConfiguredException;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.AuthConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.config.ConnectionConfig;
import de.skerkewitz.jcme.config.ExportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiClientFactoryTest {

    private TestHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void throws_when_no_auth_configured(@TempDir Path tmp) {
        ConfigStore store = new ConfigStore(tmp.resolve("cfg.json"), Map.of());
        store.save(AppConfig.defaults());
        ApiClientFactory factory = new ApiClientFactory(store);

        assertThatThrownBy(() -> factory.getConfluence(BaseUrl.of("https://nope.atlassian.net")))
                .isInstanceOf(AuthNotConfiguredException.class)
                .hasMessageContaining("Confluence");
    }

    @Test
    void caches_confluence_client_per_url(@TempDir Path tmp) {
        ConfigStore store = configWithAuth(tmp, server.baseUrl());
        // verifyAuth() calls /rest/api/space?... — make it succeed.
        server.onGet("/rest/api/space", 200, "{\"results\":[],\"size\":0}", Map.of());
        ApiClientFactory factory = new ApiClientFactory(store);

        ConfluenceClient c1 = factory.getConfluence(BaseUrl.of(server.baseUrl()));
        ConfluenceClient c2 = factory.getConfluence(BaseUrl.of(server.baseUrl() + "/"));

        assertThat(c1).isSameAs(c2);
        assertThat(server.hits("/rest/api/space")).isEqualTo(1);
    }

    @Test
    void invalidate_forces_new_client(@TempDir Path tmp) {
        ConfigStore store = configWithAuth(tmp, server.baseUrl());
        server.onGet("/rest/api/space", 200, "{\"results\":[]}", Map.of());
        ApiClientFactory factory = new ApiClientFactory(store);

        ConfluenceClient c1 = factory.getConfluence(BaseUrl.of(server.baseUrl()));
        factory.invalidateConfluence(BaseUrl.of(server.baseUrl()));
        ConfluenceClient c2 = factory.getConfluence(BaseUrl.of(server.baseUrl()));

        assertThat(c1).isNotSameAs(c2);
        assertThat(server.hits("/rest/api/space")).isEqualTo(2);
    }

    @Test
    void wraps_auth_failure_in_exception(@TempDir Path tmp) {
        ConfigStore store = configWithAuth(tmp, server.baseUrl());
        server.onGet("/rest/api/space", 401, "unauthorized", Map.of());
        ApiClientFactory factory = new ApiClientFactory(store);

        assertThatThrownBy(() -> factory.getConfluence(BaseUrl.of(server.baseUrl())))
                .isInstanceOf(AuthNotConfiguredException.class);
    }

    @Test
    void try_fetch_cloud_id_returns_value_on_200(@TempDir Path tmp) {
        ConfigStore store = configWithAuth(tmp, server.baseUrl());
        server.onGet("/_edge/tenant_info", 200, "{\"cloudId\":\"abc-123\"}", Map.of());
        ApiClientFactory factory = new ApiClientFactory(store);

        String cloudId = factory.tryFetchCloudId(server.baseUrl());

        assertThat(cloudId).isEqualTo("abc-123");
    }

    @Test
    void try_fetch_cloud_id_returns_null_on_failure(@TempDir Path tmp) {
        ConfigStore store = configWithAuth(tmp, server.baseUrl());
        server.onGet("/_edge/tenant_info", 404, "no", Map.of());
        ApiClientFactory factory = new ApiClientFactory(store);

        assertThat(factory.tryFetchCloudId(server.baseUrl())).isNull();
    }

    @Test
    void api_url_override_routes_rest_calls_to_alternative_host(@TempDir Path tmp) throws Exception {
        // Two TestHttpServers: one acts as the page-URL host (no REST endpoints),
        // the second acts as the REST API host. We register auth keyed by the
        // page-URL host but with api_url pointing at the REST host.
        try (TestHttpServer pageHost = new TestHttpServer();
             TestHttpServer apiHost = new TestHttpServer()) {

            Path cfg = tmp.resolve("cfg.json");
            ConfigStore store = new ConfigStore(cfg, Map.of());
            Map<String, ApiDetails> instances = new LinkedHashMap<>();
            instances.put(pageHost.baseUrl(), new ApiDetails(
                    "alice", "tok", "", "", apiHost.baseUrl()));
            store.save(new AppConfig(
                    ExportConfig.defaults(),
                    new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1),
                    new AuthConfig(instances, new LinkedHashMap<>())));

            // verifyAuth() should hit the API host, not the page host.
            apiHost.onGet("/rest/api/space", 200, "{\"results\":[]}", Map.of());

            ApiClientFactory factory = new ApiClientFactory(store);
            ConfluenceClient client = factory.getConfluence(BaseUrl.of(pageHost.baseUrl()));

            assertThat(client.baseUrl()).isEqualTo(apiHost.baseUrl());
            assertThat(apiHost.hits("/rest/api/space")).isEqualTo(1);
            assertThat(pageHost.hits("/rest/api/space")).isZero();
        }
    }

    @Test
    void api_url_override_skips_cloud_id_probe(@TempDir Path tmp) throws Exception {
        try (TestHttpServer pageHost = new TestHttpServer();
             TestHttpServer apiHost = new TestHttpServer()) {

            Path cfg = tmp.resolve("cfg.json");
            ConfigStore store = new ConfigStore(cfg, Map.of());
            Map<String, ApiDetails> instances = new LinkedHashMap<>();
            // Use a page host that LOOKS like Atlassian Cloud (.atlassian.net) so the probe
            // would otherwise trigger; the api_url override should suppress it.
            // We can't bind to that hostname so we use it only as a config key with no
            // actual reachability. The probe target gets a 5s timeout — without the
            // skip, this test would take 5 s instead of milliseconds.
            String fakePageHost = "https://x.atlassian.net";
            instances.put(fakePageHost, new ApiDetails(
                    "alice", "tok", "", "", apiHost.baseUrl()));
            store.save(new AppConfig(
                    ExportConfig.defaults(),
                    new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1),
                    new AuthConfig(instances, new LinkedHashMap<>())));

            apiHost.onGet("/rest/api/space", 200, "{\"results\":[]}", Map.of());

            ApiClientFactory factory = new ApiClientFactory(store);
            long started = System.currentTimeMillis();
            factory.getConfluence(BaseUrl.of(fakePageHost));
            long elapsed = System.currentTimeMillis() - started;

            // Should be much faster than the 5-second cloud-id probe timeout.
            assertThat(elapsed).isLessThan(2_000);
        }
    }

    private ConfigStore configWithAuth(Path tmp, String url) {
        Path cfg = tmp.resolve("cfg.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());
        Map<String, ApiDetails> instances = new LinkedHashMap<>();
        instances.put(url, new ApiDetails("alice", "tok", "", ""));
        AppConfig appConfig = new AppConfig(
                ExportConfig.defaults(),
                new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1),
                new AuthConfig(instances, new LinkedHashMap<>()));
        store.save(appConfig);
        return store;
    }
}
