package de.skerkewitz.jcme.api;

import de.skerkewitz.jcme.config.ApiDetails;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthLookupTest {

    private static final ApiDetails A = new ApiDetails("alice", "tok-a", "", "");
    private static final ApiDetails B = new ApiDetails("bob", "tok-b", "", "");

    @Test
    void exact_match_wins() {
        Map<String, ApiDetails> map = Map.of(
                "https://x.atlassian.net", A,
                "https://y.atlassian.net", B);
        assertThat(AuthLookup.find(map, "https://x.atlassian.net")).contains(A);
        assertThat(AuthLookup.find(map, "https://y.atlassian.net")).contains(B);
    }

    @Test
    void exact_match_normalizes_trailing_slash() {
        Map<String, ApiDetails> map = Map.of("https://x.atlassian.net", A);
        assertThat(AuthLookup.find(map, "https://x.atlassian.net/")).contains(A);
    }

    @Test
    void host_match_when_path_differs() {
        Map<String, ApiDetails> map = Map.of("https://wiki.company.com", A);
        assertThat(AuthLookup.find(map, "https://wiki.company.com/display/KEY/Page")).contains(A);
    }

    @Test
    void context_path_match_must_be_prefix() {
        Map<String, ApiDetails> map = Map.of("https://wiki.company.com/confluence", A);
        assertThat(AuthLookup.find(map, "https://wiki.company.com/confluence/display/KEY")).contains(A);
        assertThat(AuthLookup.find(map, "https://wiki.company.com/jira/browse/KEY-1")).isEmpty();
    }

    @Test
    void gateway_url_requires_exact_match() {
        Map<String, ApiDetails> map = Map.of(
                "https://api.atlassian.com/ex/confluence/cloud-1", A,
                "https://api.atlassian.com/ex/confluence/cloud-2", B);
        assertThat(AuthLookup.find(map, "https://api.atlassian.com/ex/confluence/cloud-1")).contains(A);
        assertThat(AuthLookup.find(map, "https://api.atlassian.com/ex/confluence/cloud-2")).contains(B);
        // No host fallback for gateway-only entries
        assertThat(AuthLookup.find(map, "https://api.atlassian.com/ex/confluence/cloud-3")).isEmpty();
    }

    @Test
    void port_mismatch_does_not_match() {
        Map<String, ApiDetails> map = Map.of("https://wiki.company.com:8443", A);
        assertThat(AuthLookup.find(map, "https://wiki.company.com:9000/display/X")).isEmpty();
        assertThat(AuthLookup.find(map, "https://wiki.company.com:8443/display/X")).contains(A);
    }

    @Test
    void empty_or_null_inputs_return_empty() {
        assertThat(AuthLookup.find(Map.of(), "https://x.atlassian.net")).isEmpty();
        assertThat(AuthLookup.find(null, "https://x.atlassian.net")).isEmpty();
        assertThat(AuthLookup.find(Map.of("https://x", A), null)).isEmpty();
    }

    @Test
    void preserves_insertion_order_for_first_match() {
        LinkedHashMap<String, ApiDetails> map = new LinkedHashMap<>();
        map.put("https://wiki.company.com", A);
        map.put("https://wiki.company.com/confluence", B);
        // Both could match for /confluence/display/KEY, but iteration order picks the first.
        assertThat(AuthLookup.find(map, "https://wiki.company.com/confluence/display/KEY")).contains(A);
    }
}
