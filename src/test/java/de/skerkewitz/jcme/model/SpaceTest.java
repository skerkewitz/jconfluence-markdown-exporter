package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void from_json_full() throws Exception {
        JsonNode data = JSON.readTree("""
                {
                  "key": "MYKEY",
                  "name": "My Space",
                  "description": {"plain": {"value": "hello"}},
                  "homepage": {"id": "12345"}
                }
                """);
        Space s = Space.fromJson(data, "https://x.atlassian.net");
        assertThat(s.key()).isEqualTo("MYKEY");
        assertThat(s.name()).isEqualTo("My Space");
        assertThat(s.description()).isEqualTo("hello");
        assertThat(s.homepage()).isEqualTo(12345L);
    }

    @Test
    void from_json_without_homepage() throws Exception {
        JsonNode data = JSON.readTree("{\"key\":\"K\",\"name\":\"N\"}");
        Space s = Space.fromJson(data, "https://x");
        assertThat(s.homepage()).isNull();
        assertThat(s.description()).isEmpty();
    }

    @Test
    void empty_factory_returns_blank_space() {
        Space s = Space.empty("https://x");
        assertThat(s.key()).isEmpty();
        assertThat(s.homepage()).isNull();
    }
}
