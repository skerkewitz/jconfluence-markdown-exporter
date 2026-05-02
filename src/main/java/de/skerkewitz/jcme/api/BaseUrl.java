package de.skerkewitz.jcme.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * A normalized Atlassian instance URL — Confluence or Jira host.
 *
 * <p>The constructor runs the input through {@link UrlParsing#normalizeInstanceUrl(String)},
 * so two {@code BaseUrl} instances built from {@code "https://x.atlassian.net"} and
 * {@code "https://x.atlassian.net/"} compare equal and serve as the same map key.
 *
 * <p>JSON serialization round-trips as the raw URL string ({@code @JsonValue}/{@code @JsonCreator}),
 * so on-disk lockfile and config files keep their existing shape.
 */
public record BaseUrl(String value) {

    public BaseUrl {
        if (value == null) {
            throw new IllegalArgumentException("BaseUrl value must be non-null");
        }
        value = UrlParsing.normalizeInstanceUrl(value);
    }

    @JsonCreator
    public static BaseUrl of(String value) {
        return new BaseUrl(value);
    }

    /** Build a {@code BaseUrl} from a full Confluence page URL by extracting the host root. */
    public static BaseUrl fromPageUrl(String pageUrl) {
        return new BaseUrl(UrlParsing.extractBaseUrl(pageUrl));
    }

    /** Build a {@code BaseUrl} from a full Jira URL by extracting the host root. */
    public static BaseUrl fromJiraUrl(String jiraUrl) {
        return new BaseUrl(UrlParsing.extractJiraBaseUrl(jiraUrl));
    }

    public boolean isCloud() {
        return UrlParsing.isStandardAtlassianCloudUrl(value);
    }

    public Optional<UrlParsing.GatewayRef> gateway() {
        return UrlParsing.parseGatewayUrl(value);
    }

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
