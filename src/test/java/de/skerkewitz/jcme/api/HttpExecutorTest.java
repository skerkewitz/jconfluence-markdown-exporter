package de.skerkewitz.jcme.api;

import de.skerkewitz.jcme.api.exceptions.ApiException;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.ConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpExecutorTest {

    private TestHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestHttpServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static ConnectionConfig fastConn() {
        // backoffFactor=1, maxBackoffSeconds=0 → sleep(0) on retries (instant).
        return new ConnectionConfig(true, 1, 0, 3, List.of(503, 502, 429), false, 5, false, 1);
    }

    @Test
    void successful_get_returns_body() {
        server.onGet("/ok", 200, "{\"ok\":true}", Map.of());
        HttpExecutor exec = new HttpExecutor(ApiDetails.empty(), fastConn());

        HttpResponse<byte[]> response = exec.get(URI.create(server.baseUrl() + "/ok"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body())).isEqualTo("{\"ok\":true}");
    }

    @Test
    void retries_on_503_then_succeeds() {
        server.onGetSequence("/flaky", List.of(
                TestHttpServer.Response.of(503, ""),
                TestHttpServer.Response.of(503, ""),
                TestHttpServer.Response.of(200, "{\"ok\":true}")
        ));
        HttpExecutor exec = new HttpExecutor(ApiDetails.empty(), fastConn());

        HttpResponse<byte[]> response = exec.get(URI.create(server.baseUrl() + "/flaky"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(server.hits("/flaky")).isEqualTo(3);
    }

    @Test
    void gives_up_after_max_attempts_returning_last_response() {
        server.onGet("/down", 503, "down for maintenance", Map.of());
        // maxBackoffRetries = 2 → 3 total attempts
        ConnectionConfig conn = new ConnectionConfig(true, 1, 0, 2, List.of(503), false, 5, false, 1);
        HttpExecutor exec = new HttpExecutor(ApiDetails.empty(), conn);

        HttpResponse<byte[]> response = exec.get(URI.create(server.baseUrl() + "/down"));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(server.hits("/down")).isEqualTo(3);
    }

    @Test
    void does_not_retry_on_status_not_in_set() {
        server.onGet("/notfound", 404, "missing", Map.of());
        HttpExecutor exec = new HttpExecutor(ApiDetails.empty(), fastConn());

        HttpResponse<byte[]> response = exec.get(URI.create(server.baseUrl() + "/notfound"));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(server.hits("/notfound")).isEqualTo(1);
    }

    @Test
    void disables_retry_when_backoff_disabled() {
        server.onGet("/down", 503, "x", Map.of());
        ConnectionConfig conn = new ConnectionConfig(false, 1, 0, 5, List.of(503), false, 5, false, 1);
        HttpExecutor exec = new HttpExecutor(ApiDetails.empty(), conn);

        HttpResponse<byte[]> response = exec.get(URI.create(server.baseUrl() + "/down"));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(server.hits("/down")).isEqualTo(1);
    }

    @Test
    void sends_pat_as_bearer_when_present() {
        server.onGet("/auth", 200, "ok", Map.of());
        ApiDetails auth = new ApiDetails("alice", "ignored-when-pat-set", "pat-value", "");
        HttpExecutor exec = new HttpExecutor(auth, fastConn());

        exec.get(URI.create(server.baseUrl() + "/auth"));

        TestHttpServer.RecordedRequest req = server.requests("/auth").get(0);
        assertThat(req.firstHeader("Authorization")).isEqualTo("Bearer pat-value");
    }

    @Test
    void sends_basic_auth_when_username_and_token_present() {
        server.onGet("/auth", 200, "ok", Map.of());
        ApiDetails auth = new ApiDetails("alice", "secret-token", "", "");
        HttpExecutor exec = new HttpExecutor(auth, fastConn());

        exec.get(URI.create(server.baseUrl() + "/auth"));

        TestHttpServer.RecordedRequest req = server.requests("/auth").get(0);
        // Base64("alice:secret-token") == YWxpY2U6c2VjcmV0LXRva2Vu
        assertThat(req.firstHeader("Authorization")).isEqualTo("Basic YWxpY2U6c2VjcmV0LXRva2Vu");
    }

    @Test
    void sends_no_auth_header_when_no_credentials() {
        server.onGet("/anon", 200, "ok", Map.of());
        HttpExecutor exec = new HttpExecutor(ApiDetails.empty(), fastConn());

        exec.get(URI.create(server.baseUrl() + "/anon"));

        TestHttpServer.RecordedRequest req = server.requests("/anon").get(0);
        assertThat(req.firstHeader("Authorization")).isNull();
    }

    @Test
    void wraps_network_error_in_api_exception() {
        // Connect to a closed port → connection refused
        HttpExecutor exec = new HttpExecutor(ApiDetails.empty(), fastConn());

        assertThatThrownBy(() -> exec.get(URI.create("http://127.0.0.1:1/never")))
                .isInstanceOf(ApiException.class);
    }
}
