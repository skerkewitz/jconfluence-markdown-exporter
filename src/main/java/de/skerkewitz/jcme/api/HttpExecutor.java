package de.skerkewitz.jcme.api;

import de.skerkewitz.jcme.api.exceptions.ApiException;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Low-level HTTP executor with retry/backoff, configurable SSL verification, and auth headers.
 * Wraps Java's {@link HttpClient}. One instance per (base-url, auth) combination.
 */
public final class HttpExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(HttpExecutor.class);

    private final HttpClient client;
    private final ApiDetails auth;
    private final ConnectionConfig conn;
    private final Set<Integer> retryStatusCodes;

    public HttpExecutor(ApiDetails auth, ConnectionConfig conn) {
        this.auth = auth;
        this.conn = conn;
        this.retryStatusCodes = Set.copyOf(conn.retryStatusCodes());
        this.client = buildClient(conn);
    }

    /** Send a GET request. Retries on configured status codes per {@link ConnectionConfig}. */
    public HttpResponse<byte[]> get(URI uri) {
        return send(HttpRequest.newBuilder(uri).GET());
    }

    /** Send a request built from the given builder. Retries on configured status codes. */
    public HttpResponse<byte[]> send(HttpRequest.Builder builder) {
        builder.timeout(Duration.ofSeconds(conn.timeout()));
        applyAuth(builder);
        builder.header("Accept", "application/json, */*;q=0.5");
        builder.header("User-Agent", "jcme/0.1");
        HttpRequest request = builder.build();

        int maxAttempts = conn.backoffAndRetry() ? Math.max(1, conn.maxBackoffRetries() + 1) : 1;
        AtomicReference<Exception> lastError = new AtomicReference<>();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long started = System.currentTimeMillis();
            LOG.debug("HTTP {} {} (attempt {}/{}, timeout {}s)",
                    request.method(), request.uri(), attempt + 1, maxAttempts, conn.timeout());
            try {
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                long elapsed = System.currentTimeMillis() - started;
                if (status < 400) {
                    LOG.debug("HTTP {} {} -> {} in {} ms ({} bytes)",
                            request.method(), request.uri(), status, elapsed,
                            response.body() == null ? 0 : response.body().length);
                    return response;
                }
                if (!retryStatusCodes.contains(status) || attempt == maxAttempts - 1) {
                    LOG.warn("HTTP {} {} -> {} after {} ms — not retrying",
                            request.method(), request.uri(), status, elapsed);
                    return response;
                }
                Duration backoff = backoffDuration(attempt);
                LOG.warn("HTTP {} from {} after {} ms — retrying in {}s (attempt {}/{})",
                        status, request.uri(), elapsed, backoff.toSeconds(), attempt + 1, maxAttempts);
                sleep(backoff);
            } catch (IOException | InterruptedException e) {
                long elapsed = System.currentTimeMillis() - started;
                lastError.set(e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new ApiException("Interrupted while sending request", e);
                }
                if (attempt == maxAttempts - 1) {
                    LOG.error("Network error contacting {} after {} ms: {}",
                            request.uri(), elapsed, e.getMessage());
                    throw new ApiException("Network error contacting " + request.uri(), e);
                }
                Duration backoff = backoffDuration(attempt);
                LOG.warn("Network error for {} after {} ms — retrying in {}s (attempt {}/{}): {}",
                        request.uri(), elapsed, backoff.toSeconds(), attempt + 1, maxAttempts, e.getMessage());
                sleep(backoff);
            }
        }
        Exception err = lastError.get();
        throw new ApiException("Request failed after " + maxAttempts + " attempts",
                err != null ? err : new RuntimeException("unknown"));
    }

    private Duration backoffDuration(int attempt) {
        long base = (long) Math.pow(conn.backoffFactor(), attempt);
        return Duration.ofSeconds(Math.min(base, conn.maxBackoffSeconds()));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Interrupted while sleeping for backoff", e);
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (auth == null) return;
        if (auth.pat().isPresent()) {
            builder.header("Authorization", "Bearer " + auth.pat().reveal());
        } else if (notBlank(auth.username()) && auth.apiToken().isPresent()) {
            String creds = auth.username() + ":" + auth.apiToken().reveal();
            String encoded = Base64.getEncoder().encodeToString(creds.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
    }

    private static HttpClient buildClient(ConnectionConfig conn) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(conn.timeout()));
        if (!conn.verifySsl()) {
            builder.sslContext(trustAllSslContext());
        }
        return builder.build();
    }

    private static SSLContext trustAllSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to build trust-all SSL context", e);
        }
    }

    private static final class TrustAllManager implements X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    /** Test hook: convert a header map for assertion. */
    static String headerOf(HttpRequest req, String name) {
        return req.headers().firstValue(name).orElse(null);
    }

    /** Used by callers that want to know what auth-related headers the client will send. */
    public Map<String, String> debugAuthHeaders() {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://example.invalid")).GET();
        applyAuth(b);
        return b.build().headers().map().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.join(",", e.getValue())));
    }
}
