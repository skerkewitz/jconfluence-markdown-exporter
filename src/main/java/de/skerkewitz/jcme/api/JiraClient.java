package de.skerkewitz.jcme.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.api.exceptions.ApiException;
import de.skerkewitz.jcme.api.exceptions.JiraAuthenticationException;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Typed wrapper around a Jira REST API. Exposes only the methods used by the
 * exporter (issue lookup + project list for auth verification).
 *
 * <p>Detects the {@code X-Seraph-Loginreason: AUTHENTICATED_FAILED} header that Jira
 * Server returns when SSO/session has expired and surfaces it as
 * {@link JiraAuthenticationException}.
 */
public class JiraClient extends RestClient {

    public JiraClient(String baseUrl, HttpExecutor http) {
        super(baseUrl, http);
    }

    public JsonNode getIssue(de.skerkewitz.jcme.model.IssueKey key) {
        return getJsonChecked("/rest/api/2/issue/" + java.net.URLEncoder.encode(
                key.value(), java.nio.charset.StandardCharsets.UTF_8));
    }

    public JsonNode getAllProjects() {
        return getJsonChecked("/rest/api/2/project");
    }

    public void verifyAuth() {
        getAllProjects();
    }

    private JsonNode getJsonChecked(String path) {
        URI uri = buildUri(path, java.util.Map.of());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).GET();
        HttpResponse<byte[]> response = http.send(builder);
        String seraph = response.headers().firstValue("X-Seraph-Loginreason").orElse("");
        if ("AUTHENTICATED_FAILED".equals(seraph)) {
            throw new JiraAuthenticationException(
                    "Jira authentication failed for request to " + uri);
        }
        if (response.statusCode() >= 400) {
            throw new ApiException(
                    "GET " + uri + " failed with status " + response.statusCode(),
                    response.statusCode(), uri.toString());
        }
        return parseJson(response, uri);
    }
}
