package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonHelpersTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void extract_space_key_handles_full_ref() throws Exception {
        var node = JSON.readTree("{\"_expandable\":{\"space\":\"/rest/api/space/MYSPACE\"}}");
        assertThat(JsonHelpers.extractSpaceKey(node)).isEqualTo("MYSPACE");
    }

    @Test
    void extract_space_key_returns_empty_when_absent() throws Exception {
        assertThat(JsonHelpers.extractSpaceKey(JSON.readTree("{}"))).isEmpty();
        assertThat(JsonHelpers.extractSpaceKey(JSON.readTree("{\"_expandable\":{}}"))).isEmpty();
    }

    @Test
    void walk_handles_missing_intermediates() throws Exception {
        var node = JSON.readTree("{\"a\":{\"b\":{\"c\":1}}}");
        assertThat(JsonHelpers.walk(node, "a", "b", "c").asInt()).isEqualTo(1);
        assertThat(JsonHelpers.walk(node, "a", "x", "c").isMissingNode()).isTrue();
    }
}
