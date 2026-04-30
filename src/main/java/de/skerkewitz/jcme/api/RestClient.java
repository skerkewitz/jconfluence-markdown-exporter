package de.skerkewitz.jcme.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.skerkewitz.jcme.api.exceptions.ApiException;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Higher-level REST helper built on top of {@link HttpExecutor}.
 *
 * <p>Builds URLs against a fixed base URL, parses JSON responses into Jackson nodes,
 * and exposes a small surface that's easy to extend for typed Confluence/Jira clients.
 */
public class RestClient {

    protected static final ObjectMapper JSON = new ObjectMapper();

    protected final String baseUrl;
    protected final HttpExecutor http;

    public RestClient(String baseUrl, HttpExecutor http) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.http = http;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public HttpExecutor http() {
        return http;
    }

    /** GET a path relative to the base URL with the given query parameters and parse as JSON. */
    public JsonNode getJson(String path, Map<String, ?> params) {
        URI uri = buildUri(path, toMultiMap(params));
        HttpResponse<byte[]> response = http.get(uri);
        return parseJson(response, uri);
    }

    /** GET a fully-qualified URL and parse as JSON. */
    public JsonNode getJsonAbsolute(String absoluteUrl) {
        URI uri = URI.create(absoluteUrl);
        HttpResponse<byte[]> response = http.get(uri);
        return parseJson(response, uri);
    }

    /** GET a fully-qualified URL and return raw bytes. */
    public byte[] getBytesAbsolute(String absoluteUrl) {
        URI uri = URI.create(absoluteUrl);
        HttpResponse<byte[]> response = http.get(uri);
        if (response.statusCode() >= 400) {
            throw new ApiException(
                    "GET " + uri + " failed with status " + response.statusCode(),
                    response.statusCode(), uri.toString());
        }
        return response.body();
    }

    /** GET a path relative to the base URL with the given query parameters; return JSON node. */
    public JsonNode getJson(String path) {
        return getJson(path, Map.of());
    }

    /** Send an arbitrary request (e.g. a POST) and return the JSON response. */
    public JsonNode sendJson(HttpRequest.Builder builder, URI uri) {
        HttpResponse<byte[]> response = http.send(builder);
        return parseJson(response, uri);
    }

    protected URI buildUri(String path, Map<String, List<String>> params) {
        StringBuilder url = new StringBuilder();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            url.append(path);
        } else {
            url.append(baseUrl);
            if (!path.startsWith("/")) url.append('/');
            url.append(path);
        }
        if (params != null && !params.isEmpty()) {
            String separator = url.indexOf("?") >= 0 ? "&" : "?";
            url.append(separator).append(UrlParsing.buildQueryString(new LinkedHashMap<>(params)));
        }
        return URI.create(url.toString());
    }

    protected static JsonNode parseJson(HttpResponse<byte[]> response, URI uri) {
        int status = response.statusCode();
        if (status >= 400) {
            String preview = previewBody(response.body());
            throw new ApiException(
                    "GET " + uri + " failed with status " + status + ": " + preview,
                    status, uri.toString());
        }
        try {
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                return JSON.nullNode();
            }
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    "Could not parse JSON response from " + uri + ": " + e.getOriginalMessage(),
                    status, uri.toString(), e);
        } catch (java.io.IOException e) {
            throw new ApiException(
                    "I/O error reading response from " + uri,
                    status, uri.toString(), e);
        }
    }

    private static String previewBody(byte[] body) {
        if (body == null) return "<empty>";
        String s = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        if (s.length() > 200) s = s.substring(0, 200) + "...";
        return s.replace("\n", " ");
    }

    private static Map<String, List<String>> toMultiMap(Map<String, ?> params) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        if (params == null) return result;
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            Object v = entry.getValue();
            if (v == null) continue;
            if (v instanceof List<?> list) {
                List<String> values = new java.util.ArrayList<>(list.size());
                for (Object item : list) {
                    if (item != null) values.add(item.toString());
                }
                if (!values.isEmpty()) result.put(entry.getKey(), values);
            } else {
                result.put(entry.getKey(), List.of(v.toString()));
            }
        }
        return result;
    }

    public static ObjectMapper jsonMapper() {
        return JSON;
    }
}
