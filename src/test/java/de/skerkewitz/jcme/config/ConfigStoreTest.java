package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigStoreTest {

    @Test
    void loads_defaults_when_file_missing(@TempDir Path tmp) {
        Path cfg = tmp.resolve("missing.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        AppConfig loaded = store.load();

        assertThat(loaded).isEqualTo(AppConfig.defaults());
    }

    @Test
    void writes_and_reads_back(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        store.save(AppConfig.defaults());

        assertThat(Files.exists(cfg)).isTrue();
        assertThat(store.load()).isEqualTo(AppConfig.defaults());
    }

    @Test
    void set_by_path_persists_value(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        store.setByPath("export.log_level", "DEBUG");

        assertThat(store.load().export().logLevel()).isEqualTo("DEBUG");
        assertThat(store.getByPath("export.log_level").asText()).isEqualTo("DEBUG");
    }

    @Test
    void set_by_path_validates_type(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        assertThatThrownBy(() -> store.setByPath("connection_config.max_workers", "not-a-number"))
                .isInstanceOf(ConfigException.class);
    }

    @Test
    void reset_to_defaults_restores_field(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        store.setByPath("connection_config.max_workers", 5);
        assertThat(store.load().connectionConfig().maxWorkers()).isEqualTo(5);

        store.resetToDefaults("connection_config.max_workers");

        assertThat(store.load().connectionConfig().maxWorkers()).isEqualTo(20);
    }

    @Test
    void reset_to_defaults_full(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        store.setByPath("export.log_level", "DEBUG");
        store.resetToDefaults(null);

        assertThat(store.load()).isEqualTo(AppConfig.defaults());
    }

    @Test
    void env_overlay_overrides_file_value(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        Map<String, String> env = new HashMap<>();
        env.put("JCME_EXPORT__LOG_LEVEL", "DEBUG");
        env.put("JCME_CONNECTION_CONFIG__MAX_WORKERS", "5");
        env.put("JCME_CONNECTION_CONFIG__VERIFY_SSL", "false");
        ConfigStore store = new ConfigStore(cfg, env);

        AppConfig loaded = store.loadEffective();

        assertThat(loaded.export().logLevel()).isEqualTo("DEBUG");
        assertThat(loaded.connectionConfig().maxWorkers()).isEqualTo(5);
        assertThat(loaded.connectionConfig().verifySsl()).isFalse();
    }

    @Test
    void env_overlay_does_not_persist(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        Map<String, String> env = Map.of("JCME_EXPORT__LOG_LEVEL", "DEBUG");
        ConfigStore store = new ConfigStore(cfg, env);

        store.loadEffective();

        // load() (no env overlay) sees the default
        ConfigStore plain = new ConfigStore(cfg, Map.of());
        assertThat(plain.load().export().logLevel()).isEqualTo("INFO");
    }

    @Test
    void parse_cli_value_handles_json_and_strings() {
        assertThat(ConfigStore.parseCliValue("42")).isInstanceOf(JsonNode.class);
        assertThat(((JsonNode) ConfigStore.parseCliValue("42")).asInt()).isEqualTo(42);
        assertThat(ConfigStore.parseCliValue("true")).isEqualTo(((JsonNode) ConfigStore.parseCliValue("true")));
        assertThat(((JsonNode) ConfigStore.parseCliValue("true")).asBoolean()).isTrue();
        assertThat(ConfigStore.parseCliValue("hello")).isEqualTo("hello");
    }

    @Test
    void load_as_node_merges_partial_file_with_defaults(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve("app_data.json");
        Files.writeString(cfg, "{\"export\": {\"log_level\": \"WARNING\"}}");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        ObjectNode node = store.loadAsNode();

        assertThat(node.path("export").path("log_level").asText()).isEqualTo("WARNING");
        // Defaults still present for unspecified fields
        assertThat(node.path("export").path("filename_length").asInt()).isEqualTo(255);
        assertThat(node.path("connection_config").path("max_workers").asInt()).isEqualTo(20);
    }

    @Test
    void resolve_default_config_path_honors_env_override() {
        Path resolved = ConfigStore.resolveDefaultConfigPath(
                Map.of("JCME_CONFIG_PATH", "/tmp/custom.json"));
        assertThat(resolved.toString()).contains("custom.json");
    }

    @Test
    void user_config_dir_falls_back_to_os_specific_default_without_appdirs() {
        // Verify the hand-rolled resolver returns *some* path under the user's home
        // for the current platform, regardless of JNA availability.
        Path dir = ConfigStore.userConfigDir(Map.of(), "myapp");
        String s = dir.toString();
        assertThat(s).contains("myapp");
        // Should be under the user's home directory on every supported OS.
        assertThat(s).contains(System.getProperty("user.home"));
    }

    @Test
    void env_overlay_skips_jcme_config_path(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        Map<String, String> env = Map.of("JCME_CONFIG_PATH", "/tmp/custom.json");
        ConfigStore store = new ConfigStore(cfg, env);

        AppConfig loaded = store.loadEffective();

        assertThat(loaded).isEqualTo(AppConfig.defaults());
    }

    @Test
    void set_by_keys_handles_url_with_dots(@TempDir Path tmp) {
        Path cfg = tmp.resolve("app_data.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());

        store.setByKeys(
                List.of("auth", "confluence", "https://company.atlassian.net", "username"),
                "alice@example.com");

        AppConfig loaded = store.load();
        ApiDetails details = loaded.auth().confluence().get("https://company.atlassian.net");
        assertThat(details).isNotNull();
        assertThat(details.username()).isEqualTo("alice@example.com");
    }
}
