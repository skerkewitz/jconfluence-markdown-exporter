package de.skerkewitz.jcme.api;

import de.skerkewitz.jcme.model.PageId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlParsingTest {

    // --- normalizeInstanceUrl ---

    @Test
    void normalize_strips_trailing_slashes() {
        assertThat(UrlParsing.normalizeInstanceUrl("https://x.atlassian.net/")).isEqualTo("https://x.atlassian.net");
        assertThat(UrlParsing.normalizeInstanceUrl("https://x.atlassian.net///")).isEqualTo("https://x.atlassian.net");
        assertThat(UrlParsing.normalizeInstanceUrl("https://x.atlassian.net")).isEqualTo("https://x.atlassian.net");
    }

    // --- gateway URL parsing ---

    @Test
    void parses_confluence_gateway_url() {
        Optional<UrlParsing.GatewayRef> g = UrlParsing.parseGatewayUrl(
                "https://api.atlassian.com/ex/confluence/abc123-def/wiki/spaces/KEY");
        assertThat(g).isPresent();
        assertThat(g.get().service()).isEqualTo("confluence");
        assertThat(g.get().cloudId()).isEqualTo("abc123-def");
    }

    @Test
    void parses_jira_gateway_url() {
        Optional<UrlParsing.GatewayRef> g = UrlParsing.parseGatewayUrl(
                "https://api.atlassian.com/ex/jira/abc123/rest/api/2/issue/KEY-1");
        assertThat(g).isPresent();
        assertThat(g.get().service()).isEqualTo("jira");
        assertThat(g.get().cloudId()).isEqualTo("abc123");
    }

    @Test
    void non_gateway_url_returns_empty() {
        assertThat(UrlParsing.parseGatewayUrl("https://x.atlassian.net/wiki/spaces/X")).isEmpty();
    }

    @Test
    void ensure_service_gateway_swaps_service() {
        String swapped = UrlParsing.ensureServiceGatewayUrl(
                "https://api.atlassian.com/ex/confluence/cloud-id/wiki/spaces/X", "jira");
        assertThat(swapped).isEqualTo("https://api.atlassian.com/ex/jira/cloud-id");
    }

    @Test
    void ensure_service_gateway_passes_through_non_gateway() {
        String url = "https://x.atlassian.net/wiki/spaces/X";
        assertThat(UrlParsing.ensureServiceGatewayUrl(url, "jira")).isEqualTo(url);
    }

    // --- Confluence path parsing: Cloud ---

    @Test
    void parses_cloud_url_with_pageid_and_title() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/wiki/spaces/KEY/pages/123456789/Page+Title");
        assertThat(ref).isPresent();
        assertThat(ref.get().spaceKey()).isEqualTo(de.skerkewitz.jcme.model.SpaceKey.of("KEY"));
        assertThat(ref.get().pageId()).isEqualTo(PageId.of(123456789L));
        assertThat(ref.get().pageTitle()).isEqualTo("Page Title");
    }

    @Test
    void parses_cloud_url_with_only_space_key() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/wiki/spaces/MYSPACE");
        assertThat(ref).isPresent();
        assertThat(ref.get().spaceKey()).isEqualTo(de.skerkewitz.jcme.model.SpaceKey.of("MYSPACE"));
        assertThat(ref.get().pageId()).isNull();
        assertThat(ref.get().pageTitle()).isNull();
    }

    @Test
    void parses_cloud_url_with_pageid_no_title() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/wiki/spaces/KEY/pages/42");
        assertThat(ref).isPresent();
        assertThat(ref.get().spaceKey()).isEqualTo(de.skerkewitz.jcme.model.SpaceKey.of("KEY"));
        assertThat(ref.get().pageId()).isEqualTo(PageId.of(42L));
        assertThat(ref.get().pageTitle()).isNull();
    }

    @Test
    void parses_cloud_gateway_path() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath(
                "/ex/confluence/cloud-id/wiki/spaces/KEY/pages/42/Title");
        assertThat(ref).isPresent();
        assertThat(ref.get().spaceKey()).isEqualTo(de.skerkewitz.jcme.model.SpaceKey.of("KEY"));
        assertThat(ref.get().pageId()).isEqualTo(PageId.of(42L));
        assertThat(ref.get().pageTitle()).isEqualTo("Title");
    }

    @Test
    void parses_cloud_url_with_extra_segment_after_title() {
        // e.g. /wiki/spaces/KEY/pages/123/Title/edit
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/wiki/spaces/KEY/pages/123/Title/edit");
        // The trailing "/edit" segment is matched and discarded by the regex
        assertThat(ref).isPresent();
        assertThat(ref.get().pageId()).isEqualTo(PageId.of(123L));
        assertThat(ref.get().pageTitle()).isEqualTo("Title");
    }

    // --- Confluence path parsing: Server ---

    @Test
    void parses_server_long_url() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/display/SPACEKEY/Page+Title");
        assertThat(ref).isPresent();
        assertThat(ref.get().spaceKey()).isEqualTo(de.skerkewitz.jcme.model.SpaceKey.of("SPACEKEY"));
        assertThat(ref.get().pageTitle()).isEqualTo("Page Title");
        assertThat(ref.get().pageId()).isNull();
    }

    @Test
    void parses_server_short_url() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/SPACEKEY/Page+Title");
        assertThat(ref).isPresent();
        assertThat(ref.get().spaceKey()).isEqualTo(de.skerkewitz.jcme.model.SpaceKey.of("SPACEKEY"));
        assertThat(ref.get().pageTitle()).isEqualTo("Page Title");
    }

    @Test
    void parses_server_space_only() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/SPACEKEY");
        assertThat(ref).isPresent();
        assertThat(ref.get().spaceKey()).isEqualTo(de.skerkewitz.jcme.model.SpaceKey.of("SPACEKEY"));
        assertThat(ref.get().pageTitle()).isNull();
    }

    @Test
    void empty_path_returns_empty() {
        assertThat(UrlParsing.parseConfluencePath("")).isEmpty();
        assertThat(UrlParsing.parseConfluencePath(null)).isEmpty();
    }

    @Test
    void normalizes_trailing_slash() {
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath("/wiki/spaces/KEY/pages/123/Title/");
        assertThat(ref).isPresent();
        assertThat(ref.get().pageId()).isEqualTo(PageId.of(123L));
    }

    // --- pageId query param ---

    @Test
    void extracts_pageid_query_param() {
        Optional<PageId> id = UrlParsing.extractPageIdQueryParam(
                "https://wiki.company.com/pages/viewpage.action?pageId=987654");
        assertThat(id).hasValue(PageId.of(987654L));
    }

    @Test
    void pageid_query_param_is_case_insensitive() {
        Optional<PageId> id = UrlParsing.extractPageIdQueryParam(
                "https://wiki.company.com/pages/viewpage.action?PAGEID=42");
        assertThat(id).hasValue(PageId.of(42L));
    }

    @Test
    void missing_pageid_returns_empty() {
        assertThat(UrlParsing.extractPageIdQueryParam("https://wiki.company.com/x?foo=bar")).isEmpty();
        assertThat(UrlParsing.extractPageIdQueryParam("https://wiki.company.com/x")).isEmpty();
    }

    // --- extractBaseUrl ---

    @Test
    void extract_base_url_for_atlassian_cloud() {
        assertThat(UrlParsing.extractBaseUrl("https://company.atlassian.net/wiki/spaces/KEY/pages/123/T"))
                .isEqualTo("https://company.atlassian.net");
    }

    @Test
    void extract_base_url_preserves_context_path_on_server() {
        assertThat(UrlParsing.extractBaseUrl("https://wiki.company.com/confluence/display/KEY/Page"))
                .isEqualTo("https://wiki.company.com/confluence");
    }

    @Test
    void extract_base_url_preserves_non_default_port() {
        assertThat(UrlParsing.extractBaseUrl("https://wiki.company.com:8443/display/KEY/Page"))
                .isEqualTo("https://wiki.company.com:8443");
    }

    @Test
    void extract_base_url_for_gateway() {
        assertThat(UrlParsing.extractBaseUrl(
                "https://api.atlassian.com/ex/confluence/cloud-id/wiki/spaces/X"))
                .isEqualTo("https://api.atlassian.com/ex/confluence/cloud-id");
    }

    @Test
    void extract_base_url_invalid_url_throws() {
        assertThatThrownBy(() -> UrlParsing.extractBaseUrl("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extract_jira_base_url_strips_jira_route_segments() {
        assertThat(UrlParsing.extractJiraBaseUrl("https://jira.company.com/jira/browse/KEY-1"))
                .isEqualTo("https://jira.company.com/jira");
        assertThat(UrlParsing.extractJiraBaseUrl("https://jira.company.com/browse/KEY-1"))
                .isEqualTo("https://jira.company.com");
    }

    // --- relativePath ---

    @Test
    void relative_path_strips_base_context() {
        assertThat(UrlParsing.relativePath(
                "https://host/confluence/spaces/KEY",
                "https://host/confluence")).isEqualTo("/spaces/KEY");
    }

    @Test
    void relative_path_with_no_context() {
        assertThat(UrlParsing.relativePath(
                "https://host/wiki/spaces/KEY",
                "https://host")).isEqualTo("/wiki/spaces/KEY");
    }

    // --- isStandardAtlassianCloudUrl ---

    @Test
    void detects_standard_atlassian_cloud_url() {
        assertThat(UrlParsing.isStandardAtlassianCloudUrl("https://company.atlassian.net")).isTrue();
        assertThat(UrlParsing.isStandardAtlassianCloudUrl("https://wiki.company.com")).isFalse();
        assertThat(UrlParsing.isStandardAtlassianCloudUrl("https://api.atlassian.com/ex/confluence/x")).isFalse();
    }
}
