package de.skerkewitz.jcme.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal in-process HTTP server for tests. Backed by the JDK's built-in {@link HttpServer}. */
public final class TestHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final Map<String, List<RecordedRequest>> requests = new ConcurrentHashMap<>();

    public TestHttpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Register a handler that always returns the given status + body for a path. */
    public void onGet(String path, int status, String body, Map<String, String> extraHeaders) {
        register(path, exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (extraHeaders != null) {
                extraHeaders.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            }
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }

    /** Register a handler that returns the listed responses in order, one per request. */
    public void onGetSequence(String path, List<Response> responses) {
        AtomicInteger counter = new AtomicInteger(0);
        register(path, exchange -> {
            int idx = counter.getAndIncrement();
            Response r = responses.get(Math.min(idx, responses.size() - 1));
            byte[] response = r.body.getBytes(StandardCharsets.UTF_8);
            r.headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(r.status, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }

    public int hits(String path) {
        AtomicInteger c = hits.get(path);
        return c == null ? 0 : c.get();
    }

    public List<RecordedRequest> requests(String path) {
        return requests.getOrDefault(path, List.of());
    }

    private void register(String path, HttpHandler handler) {
        hits.put(path, new AtomicInteger(0));
        requests.put(path, java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
        try {
            server.removeContext(path);
        } catch (IllegalArgumentException ignored) {
            // path not yet registered
        }
        server.createContext(path, exchange -> {
            try {
                hits.get(path).incrementAndGet();
                requests.get(path).add(RecordedRequest.from(exchange));
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public record Response(int status, String body, Map<String, String> headers) {
        public static Response of(int status, String body) {
            return new Response(status, body, new LinkedHashMap<>());
        }
        public Response withHeader(String name, String value) {
            LinkedHashMap<String, String> m = new LinkedHashMap<>(headers);
            m.put(name, value);
            return new Response(status, body, m);
        }
    }

    public record RecordedRequest(String method, String path, String query, Map<String, List<String>> headers) {
        static RecordedRequest from(HttpExchange exchange) {
            return new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    new LinkedHashMap<>(exchange.getRequestHeaders()));
        }

        public String firstHeader(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }
}
