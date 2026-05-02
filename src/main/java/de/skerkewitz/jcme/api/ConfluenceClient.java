package de.skerkewitz.jcme.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.model.PageId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed wrapper around a Confluence REST API. Mirrors the subset of methods that the
 * Python tool calls via {@code atlassian.Confluence}.
 */
public class ConfluenceClient extends RestClient {

    public ConfluenceClient(String baseUrl, HttpExecutor http) {
        super(baseUrl, http);
    }

    public JsonNode getAllSpaces(String spaceType, String spaceStatus, String expand, int limit, int start) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (spaceType != null) params.put("type", spaceType);
        if (spaceStatus != null) params.put("status", spaceStatus);
        if (expand != null) params.put("expand", expand);
        params.put("limit", limit);
        params.put("start", start);
        return getJson("/rest/api/space", params);
    }

    public JsonNode getSpace(String spaceKey, String expand) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (expand != null) params.put("expand", expand);
        return getJson("/rest/api/space/" + encode(spaceKey), params);
    }

    public JsonNode getPageById(PageId pageId, String expand) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (expand != null) params.put("expand", expand);
        return getJson("/rest/api/content/" + pageId, params);
    }

    public JsonNode getPageByTitle(String spaceKey, String title, String expand) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("spaceKey", spaceKey);
        params.put("title", title);
        params.put("type", "page");
        if (expand != null) params.put("expand", expand);
        return getJson("/rest/api/content", params);
    }

    public JsonNode getAttachmentsFromContent(PageId pageId, int start, int limit, String expand) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("start", start);
        params.put("limit", limit);
        if (expand != null) params.put("expand", expand);
        return getJson("/rest/api/content/" + pageId + "/child/attachment", params);
    }

    /** CQL search (v1). */
    public JsonNode searchCql(String cql, String expand, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("cql", cql);
        params.put("limit", limit);
        if (expand != null) params.put("expand", expand);
        return getJson("/rest/api/content/search", params);
    }

    /** Follow a {@code _links.next} continuation path returned by the v1 API. */
    public JsonNode getRelative(String relativePath) {
        return getJson(relativePath);
    }

    /** v2 API: list pages by ID (multiple {@code id} query params). */
    public JsonNode getV2Pages(Collection<String> ids) {
        LinkedHashMap<String, List<String>> params = new LinkedHashMap<>();
        params.put("id", new java.util.ArrayList<>(ids));
        params.put("limit", List.of(String.valueOf(ids.size())));
        return getJson("/api/v2/pages", params);
    }

    /** Helper overload for {@link #getJson(String, java.util.Map)} accepting multi-valued params. */
    public JsonNode getJson(String path, LinkedHashMap<String, List<String>> params) {
        return parseJson(http.get(buildUri(path, params)), buildUri(path, params));
    }

    /** Fetch user details by various identifier types. */
    public JsonNode getUserByUsername(String username) {
        return getJson("/rest/api/user", Map.of("username", username));
    }

    public JsonNode getUserByUserkey(String userkey) {
        return getJson("/rest/api/user", Map.of("key", userkey));
    }

    public JsonNode getUserByAccountId(String accountId) {
        return getJson("/rest/api/user", Map.of("accountId", accountId));
    }

    /** Download an attachment via a {@code _links.download} relative or absolute URL. */
    public byte[] downloadAttachment(String downloadLink) {
        if (downloadLink.startsWith("http://") || downloadLink.startsWith("https://")) {
            return getBytesAbsolute(downloadLink);
        }
        return getBytesAbsolute(baseUrl + (downloadLink.startsWith("/") ? "" : "/") + downloadLink);
    }

    /** Quick health check used by the Python tool to validate auth on first connection. */
    public void verifyAuth() {
        getAllSpaces(null, null, null, 1, 0);
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
