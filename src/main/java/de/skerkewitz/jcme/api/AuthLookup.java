package de.skerkewitz.jcme.api;

import de.skerkewitz.jcme.config.ApiDetails;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

/**
 * Resolve an instance URL to {@link ApiDetails} by exact match first, then by host+port,
 * mirroring the Python {@code AuthConfig._match_by_host} behaviour.
 *
 * <p>API-gateway URLs ({@code api.atlassian.com}) require an exact match — multiple
 * tenants share that hostname. Server/Data Center keys stored without a context path
 * match any context path on the same host; keys with a context path must be a prefix
 * of the lookup URL's path.
 */
public final class AuthLookup {

    private static final String GATEWAY_HOST = "api.atlassian.com";

    private AuthLookup() {}

    public static Optional<ApiDetails> find(Map<String, ApiDetails> instances, String url) {
        if (instances == null || instances.isEmpty() || url == null) return Optional.empty();
        String key = UrlParsing.normalizeInstanceUrl(url);
        ApiDetails exact = instances.get(key);
        if (exact != null) return Optional.of(exact);
        return matchByHost(instances, key);
    }

    private static Optional<ApiDetails> matchByHost(Map<String, ApiDetails> instances, String url) {
        URI parsed = parse(url);
        if (parsed == null) return Optional.empty();
        String host = parsed.getHost();
        int port = parsed.getPort();
        if (host == null) return Optional.empty();
        // Gateway URLs must match exactly — multiple tenants share api.atlassian.com.
        if (host.equals(GATEWAY_HOST)) return Optional.empty();

        for (Map.Entry<String, ApiDetails> entry : instances.entrySet()) {
            URI keyUri = parse(entry.getKey());
            if (keyUri == null) continue;
            if (GATEWAY_HOST.equals(keyUri.getHost())) continue;
            if (!host.equals(keyUri.getHost())) continue;
            if (keyUri.getPort() != port) continue;

            String keyPath = keyUri.getRawPath() == null ? "" : keyUri.getRawPath();
            String stripped = keyPath.replaceAll("^/+|/+$", "");
            if (stripped.isEmpty()) {
                // No context path on the stored key — matches any path on the same host.
                return Optional.of(entry.getValue());
            }
            String lookupPath = parsed.getRawPath() == null ? "" : parsed.getRawPath();
            if (lookupPath.startsWith(keyPath)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private static URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
