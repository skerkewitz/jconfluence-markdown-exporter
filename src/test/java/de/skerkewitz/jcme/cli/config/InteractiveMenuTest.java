package de.skerkewitz.jcme.cli.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveMenuTest {

    private static Prompt scripted(String input, ByteArrayOutputStream sink) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8));
        return new Prompt(null, reader, new PrintStream(sink));
    }

    private ConfigStore newStore(@TempDir Path tmp) {
        return new ConfigStore(tmp.resolve("cfg.json"), Map.of());
    }

    @Test
    void main_menu_exit_does_nothing(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        // "5" → Exit
        InteractiveMenu menu = new InteractiveMenu(store, scripted("5\n", new ByteArrayOutputStream()));
        menu.mainMenu();

        assertThat(store.load()).isEqualTo(AppConfig.defaults());
    }

    @Test
    void edit_key_for_scalar_string_persists_value(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Single text entry that becomes the new value.
        InteractiveMenu menu = new InteractiveMenu(store, scripted("DEBUG\n", out));

        menu.editKey("export.log_level");

        assertThat(store.load().export().logLevel()).isEqualTo(de.skerkewitz.jcme.config.LogLevel.DEBUG);
        assertThat(out.toString()).contains("Saved");
    }

    @Test
    void edit_key_for_boolean_uses_yes_no_confirm(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InteractiveMenu menu = new InteractiveMenu(store, scripted("n\n", out));

        menu.editKey("export.skip_unchanged");

        assertThat(store.load().export().skipUnchanged()).isFalse();
    }

    @Test
    void edit_key_for_int_validates_input(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InteractiveMenu menu = new InteractiveMenu(store, scripted("42\n", out));

        menu.editKey("connection_config.max_workers");

        assertThat(store.load().connectionConfig().maxWorkers()).isEqualTo(42);
    }

    @Test
    void edit_key_for_unknown_key_prints_message(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InteractiveMenu menu = new InteractiveMenu(store, scripted("\n", out));

        menu.editKey("nonexistent.path");

        assertThat(out.toString()).contains("not found");
    }

    @Test
    void auth_for_url_creates_new_instance_with_credentials(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Add new instance flow:
        // [pick "Add new instance" → idx 1]
        // confirm URL (default) → ""
        // username → "alice@example.com"
        // api_token (secret, no console → plain input) → "tok"
        // pat (secret) → ""
        // cloud_id → ""
        // After save, return → pick "Back" → idx 2
        String input = String.join("\n",
                "1",                       // top: Add new instance
                "",                        // URL: keep prefilled
                "alice@example.com",       // username
                "tok",                     // api_token
                "",                        // pat
                "",                        // cloud_id
                "2",                       // back
                ""                         // EOF
        ) + "\n";
        InteractiveMenu menu = new InteractiveMenu(store, scripted(input, out));

        menu.authForUrl("confluence", "https://x.atlassian.net");

        AppConfig loaded = store.load();
        assertThat(loaded.auth().confluence()).containsKey("https://x.atlassian.net");
        assertThat(loaded.auth().confluence().get("https://x.atlassian.net").username())
                .isEqualTo("alice@example.com");
        assertThat(loaded.auth().confluence().get("https://x.atlassian.net").apiToken().reveal())
                .isEqualTo("tok");
    }

    @Test
    void auth_for_url_with_existing_instance_lists_it(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        store.setByKeys(java.util.List.of("auth", "confluence", "https://x.atlassian.net", "username"), "bob");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Pick the existing instance (index 1), then "Back" (index 3)
        String input = "1\n3\n";
        InteractiveMenu menu = new InteractiveMenu(store, scripted(input, out));

        menu.authForUrl("confluence", null);

        // Should have listed the URL in the choices
        assertThat(out.toString()).contains("https://x.atlassian.net");
    }

    @Test
    void instance_count_reflects_persisted_auth(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        store.setByKeys(java.util.List.of("auth", "confluence", "https://a.atlassian.net", "username"), "a");
        store.setByKeys(java.util.List.of("auth", "confluence", "https://b.atlassian.net", "username"), "b");

        InteractiveMenu menu = new InteractiveMenu(store, scripted("", new ByteArrayOutputStream()));
        assertThat(menu.instanceCount("confluence")).isEqualTo(2);
        assertThat(menu.instanceCount("jira")).isEqualTo(0);
    }

    @Test
    void editing_invalid_int_prints_error_and_keeps_value(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InteractiveMenu menu = new InteractiveMenu(store, scripted("not-a-number\n", out));

        menu.editKey("connection_config.max_workers");

        assertThat(store.load().connectionConfig().maxWorkers()).isEqualTo(20);
        assertThat(out.toString()).contains("Not an integer");
    }

    @Test
    void edit_key_section_prints_message(@TempDir Path tmp) {
        ConfigStore store = newStore(tmp);
        store.save(AppConfig.defaults());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InteractiveMenu menu = new InteractiveMenu(store, scripted("\n", out));

        menu.editKey("export");

        // Editing a whole section is rejected with a hint
        JsonNode after = store.getByPath("export");
        assertThat(after).isNotNull();
        assertThat(out.toString()).contains("section");
    }
}
