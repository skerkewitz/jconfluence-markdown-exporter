package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDetailsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void four_arg_constructor_defaults_api_url_to_empty() {
        ApiDetails d = new ApiDetails("alice", "tok", "", "");
        assertThat(d.apiUrl()).isEmpty();
    }

    @Test
    void five_arg_constructor_holds_api_url() {
        ApiDetails d = new ApiDetails("alice", "tok", "", "", "https://api.host.com");
        assertThat(d.apiUrl()).isEqualTo("https://api.host.com");
    }

    @Test
    void api_url_is_trimmed() {
        ApiDetails d = new ApiDetails("alice", "tok", "", "", "  https://api.host.com  ");
        assertThat(d.apiUrl()).isEqualTo("https://api.host.com");
    }

    @Test
    void empty_factory_has_blank_api_url() {
        assertThat(ApiDetails.empty().apiUrl()).isEmpty();
    }

    @Test
    void deserializes_from_json_without_api_url_for_backward_compat() throws Exception {
        String json = "{\"username\":\"alice\",\"api_token\":\"t\",\"pat\":\"\",\"cloud_id\":\"\"}";
        ApiDetails d = JSON.readValue(json, ApiDetails.class);
        assertThat(d.username()).isEqualTo("alice");
        assertThat(d.apiUrl()).isEmpty();
    }

    @Test
    void deserializes_from_json_with_api_url() throws Exception {
        String json = "{\"username\":\"alice\",\"api_token\":\"t\",\"pat\":\"\",\"cloud_id\":\"\","
                + "\"api_url\":\"https://confluencews.axa.com\"}";
        ApiDetails d = JSON.readValue(json, ApiDetails.class);
        assertThat(d.apiUrl()).isEqualTo("https://confluencews.axa.com");
    }

    @Test
    void serializes_api_url_back_to_json() throws Exception {
        ApiDetails d = new ApiDetails("alice", "t", "", "", "https://api.host");
        String json = JSON.writeValueAsString(d);
        assertThat(json).contains("\"api_url\":\"https://api.host\"");
    }
}
