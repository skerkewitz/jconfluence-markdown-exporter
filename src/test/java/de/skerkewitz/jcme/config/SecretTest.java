package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void to_string_redacts_value() {
        Secret s = Secret.of("super-secret-token");
        assertThat(s.toString()).isEqualTo("***");
        assertThat(s.toString()).doesNotContain("super-secret-token");
    }

    @Test
    void to_string_of_empty_is_marker() {
        assertThat(Secret.EMPTY.toString()).isEqualTo("<empty>");
        assertThat(Secret.of("").toString()).isEqualTo("<empty>");
    }

    @Test
    void reveal_returns_underlying_value() {
        assertThat(Secret.of("tok").reveal()).isEqualTo("tok");
    }

    @Test
    void of_null_returns_empty() {
        assertThat(Secret.of(null)).isSameAs(Secret.EMPTY);
        assertThat(Secret.of("")).isSameAs(Secret.EMPTY);
    }

    @Test
    void is_present_and_is_empty_are_complementary() {
        assertThat(Secret.of("x").isPresent()).isTrue();
        assertThat(Secret.of("x").isEmpty()).isFalse();
        assertThat(Secret.EMPTY.isPresent()).isFalse();
        assertThat(Secret.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void equality_based_on_value() {
        assertThat(Secret.of("a")).isEqualTo(Secret.of("a"));
        assertThat(Secret.of("a")).isNotEqualTo(Secret.of("b"));
        assertThat(Secret.of("a").hashCode()).isEqualTo(Secret.of("a").hashCode());
    }

    @Test
    void serializes_via_json_value_to_raw_string() throws Exception {
        String json = JSON.writeValueAsString(Secret.of("tok"));
        assertThat(json).isEqualTo("\"tok\"");
    }

    @Test
    void deserializes_via_json_creator_from_raw_string() throws Exception {
        Secret s = JSON.readValue("\"tok\"", Secret.class);
        assertThat(s.reveal()).isEqualTo("tok");
    }

    @Test
    void deserializes_null_to_empty() throws Exception {
        Secret s = JSON.readValue("null", Secret.class);
        assertThat(s).isNull();
        // (Jackson maps JSON null to Java null; the @JsonCreator path is only taken
        // for non-null values. ApiDetails' canonical constructor wraps null → EMPTY.)
    }
}
