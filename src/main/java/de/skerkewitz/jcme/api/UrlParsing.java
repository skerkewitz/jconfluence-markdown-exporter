package de.skerkewitz.jcme.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL parsing helpers for Confluence and Jira URLs.
 *
 * <p>Supports:
 * <ul>
 *   <li>Atlassian Cloud: {@code https://company.atlassian.net/wiki/spaces/KEY/pages/123/Title}</li>
 *   <li>Atlassian Cloud (gateway): {@code https://api.atlassian.com/ex/confluence/{cloudId}/wiki/...}</li>
 *   <li>Server (long): {@code https://wiki.company.com/display/KEY/Title}</li>
 *   <li>Server (short): {@code https://wiki.company.com/KEY/Title}</li>
 *   <li>Server (param): {@code https://wiki.company.com/pages/viewpage.action?pageId=123456}</li>
 * </ul>
 */
public final class UrlParsing {

    public static final String CLOUD_DOMAIN = ".atlassian.net";
    public static final String GATEWAY_PREFIX = "https://api.atlassian.com/ex";

    private static final Pattern GATEWAY_RE = Pattern.compile(
            "https://api\\.atlassian\\.com/ex/(confluence|jira)/([^/?#]+)");

    // Cloud: [/ex/confluence/<id>][/wiki]/spaces/<KEY>[/pages/<ID>[/<TITLE>]][/<extra>]
    private static final Pattern CLOUD_PATH_RE = Pattern.compile(
            "^(?:/ex/confluence/[^/]+)?(?:/wiki)?/spaces/"
                    + "(?<spaceKey>[A-Za-z0-9_~-]+)"
                    + "(?:/pages/(?<pageId>\\d+)(?:/(?<pageTitle>[^/?#]+))?)?"
                    + "(?:/(?!pages/)[^/?#]+)?/?$");

    // Server (long/short): [/display]/<KEY>[/<TITLE>]
    private static final Pattern SERVER_PATH_RE = Pattern.compile(
            "^(?:/display)?/(?<spaceKey>[A-Za-z0-9._-]+)(?:/(?<pageTitle>[^/?#]+))?/?$");

    private static final Set<String> CONFLUENCE_ROUTE_SEGMENTS = Set.of(
            "wiki", "display", "spaces", "rest", "pages", "plugins", "dosearchsite.action");

    private static final Set<String> JIRA_ROUTE_SEGMENTS = Set.of(
            "agile", "backlog", "board", "browse", "issues", "plugins",
            "projects", "rest", "secure", "servicedesk", "software");

    private UrlParsing() {}

    /** Strip trailing slashes from an instance URL for consistent key storage. */
    public static String normalizeInstanceUrl(String url) {
        if (url == null) return null;
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') end--;
        return url.substring(0, end);
    }

    /** Returns {@code (service, cloudId)} when {@code url} is an Atlassian API gateway URL. */
    public static Optional<GatewayRef> parseGatewayUrl(String url) {
        if (url == null) return Optional.empty();
        Matcher m = GATEWAY_RE.matcher(url);
        if (m.find()) {
            return Optional.of(new GatewayRef(m.group(1).toLowerCase(Locale.ROOT), m.group(2)));
        }
        return Optional.empty();
    }

    public static String buildGatewayUrl(String service, String cloudId) {
        return GATEWAY_PREFIX + "/" + service.toLowerCase(Locale.ROOT) + "/" + cloudId;
    }

    /**
     * Ensure the gateway URL uses the specified service. Non-gateway URLs are returned as-is.
     */
    public static String ensureServiceGatewayUrl(String url, String service) {
        return parseGatewayUrl(url)
                .map(g -> buildGatewayUrl(service != null ? service : g.service(), g.cloudId()))
                .orElse(url);
    }

    /** Whether {@code url} looks like a standard Atlassian Cloud instance URL. */
    public static boolean isStandardAtlassianCloudUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (host.equals("atlassian.net") || host.endsWith(CLOUD_DOMAIN));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse a Confluence URL path into a {@link ConfluenceRef}. Tries the Cloud regex first,
     * then falls back to the Server regex. Returns empty if neither matches.
     */
    public static Optional<ConfluenceRef> parseConfluencePath(String path) {
        if (path == null || path.isEmpty()) return Optional.empty();
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        Matcher cloud = CLOUD_PATH_RE.matcher(p);
        if (cloud.matches()) {
            return Optional.of(refFromMatcher(cloud));
        }
        Matcher server = SERVER_PATH_RE.matcher(p);
        if (server.matches()) {
            return Optional.of(refFromMatcher(server));
        }
        return Optional.empty();
    }

    /** Extract the {@code pageId} query param from a URL. Case-insensitive on key. */
    public static Optional<Long> extractPageIdQueryParam(String url) {
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            if (query == null || query.isEmpty()) return Optional.empty();
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                if (!key.equalsIgnoreCase("pageid")) continue;
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                try {
                    return Optional.of(Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        } catch (URISyntaxException ignored) {
            // fall through
        }
        return Optional.empty();
    }

    /**
     * Extract the base URL from a Confluence URL.
     *
     * <p>For Atlassian Cloud URLs ({@code *.atlassian.net}) returns {@code {scheme}://{host}}.
     * For API gateway URLs returns {@code https://api.atlassian.com/ex/{service}/{cloudId}}.
     * For Server/Data Center instances with a context path, the context path is preserved.
     */
    public static String extractBaseUrl(String url) {
        return extractBaseUrl(url, CONFLUENCE_ROUTE_SEGMENTS);
    }

    /** Same as {@link #extractBaseUrl(String)} but for Jira URLs (different routing segments). */
    public static String extractJiraBaseUrl(String url) {
        return extractBaseUrl(url, JIRA_ROUTE_SEGMENTS);
    }

    private static String extractBaseUrl(String url, Set<String> routeSegments) {
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "Invalid URL: a scheme (http:// or https://) and hostname are required.", e);
        }
        if (parsed.getScheme() == null || parsed.getHost() == null) {
            throw new IllegalArgumentException(
                    "Invalid URL: a scheme (http:// or https://) and hostname are required.");
        }
        Optional<GatewayRef> gateway = parseGatewayUrl(url);
        if (gateway.isPresent()) {
            return normalizeInstanceUrl(buildGatewayUrl(gateway.get().service(), gateway.get().cloudId()));
        }

        String path = parsed.getRawPath();
        StringBuilder context = new StringBuilder();
        if (path != null && !path.isEmpty()) {
            for (String segment : path.split("/")) {
                if (segment.isEmpty()) continue;
                if (routeSegments.contains(segment.toLowerCase(Locale.ROOT))) break;
                context.append('/').append(segment);
            }
        }

        StringBuilder base = new StringBuilder()
                .append(parsed.getScheme()).append("://").append(parsed.getHost());
        int port = parsed.getPort();
        if (port != -1 && port != 80 && port != 443) {
            base.append(':').append(port);
        }
        base.append(context);
        return normalizeInstanceUrl(base.toString());
    }

    /**
     * Returns the relative path of {@code fullUrl} after stripping the configured base URL's path.
     * Handles trailing slashes and missing paths gracefully.
     */
    public static String relativePath(String fullUrl, String baseUrl) {
        try {
            String fullPath = Optional.ofNullable(new URI(fullUrl).getRawPath()).orElse("");
            String basePath = Optional.ofNullable(new URI(baseUrl).getRawPath()).orElse("");
            // strip trailing slash from base path
            while (!basePath.isEmpty() && basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            if (!basePath.isEmpty() && fullPath.startsWith(basePath)) {
                return fullPath.substring(basePath.length());
            }
            return fullPath;
        } catch (URISyntaxException e) {
            return fullUrl;
        }
    }

    private static ConfluenceRef refFromMatcher(Matcher m) {
        String spaceKey = decodePart(group(m, "spaceKey"));
        String pageIdStr = group(m, "pageId");
        Long pageId = pageIdStr == null ? null : Long.parseLong(pageIdStr);
        String pageTitle = decodePart(group(m, "pageTitle"));
        return new ConfluenceRef(spaceKey, pageId, pageTitle);
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String decodePart(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            // Match Python's urllib.parse.unquote_plus: '+' decodes to space.
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /** Build a query string from key/value pairs, URL-encoding keys and values. */
    public static String buildQueryString(List<Map.Entry<String, String>> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params) {
            if (sb.length() > 0) sb.append('&');
            sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** Convenience for building a query string from a (possibly multi-valued) ordered map. */
    public static String buildQueryString(LinkedHashMap<String, List<String>> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            for (String v : e.getValue()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    public record GatewayRef(String service, String cloudId) {}
}
