package de.skerkewitz.jcme.cli.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.skerkewitz.jcme.api.UrlParsing;
import de.skerkewitz.jcme.config.ConfigException;
import de.skerkewitz.jcme.config.ConfigStore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Interactive configuration menu — equivalent to the Python {@code main_config_menu_loop}.
 *
 * <p>Three flows are supported:
 * <ul>
 *   <li>Top-level menu (no key) — browse the four sections.</li>
 *   <li>{@code auth.confluence} / {@code auth.jira} — list, add, remove instances.</li>
 *   <li>{@code key.path} for a scalar — single edit, then return.</li>
 * </ul>
 *
 * <p>The auth-failure recovery flow uses the {@code authConfluence}/{@code authJira} entry
 * points with a pre-filled URL.
 */
public final class InteractiveMenu {

    private final ConfigStore store;
    private final Prompt prompt;

    public InteractiveMenu(ConfigStore store, Prompt prompt) {
        this.store = store;
        this.prompt = prompt;
    }

    public InteractiveMenu(ConfigStore store) {
        this(store, new Prompt());
    }

    /** Open the top-level menu. */
    public void mainMenu() {
        while (true) {
            int choice = prompt.select("Configuration menu",
                    List.of("Configure Confluence credentials",
                            "Configure Jira credentials",
                            "Edit a setting (e.g. export.output_path)",
                            "Reset configuration to defaults",
                            "Exit"));
            switch (choice) {
                case 0 -> authMenu("confluence", null);
                case 1 -> authMenu("jira", null);
                case 2 -> editScalarPrompted();
                case 3 -> resetMenu();
                default -> { return; }
            }
        }
    }

    /** Drop the user into the editor for a specific dot-path. */
    public void editKey(String key) {
        if (key == null || key.isEmpty()) {
            mainMenu();
            return;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.equals("auth.confluence")) {
            authMenu("confluence", null);
            return;
        }
        if (normalized.equals("auth.jira")) {
            authMenu("jira", null);
            return;
        }
        editScalar(key);
    }

    /** Pre-filled auth flow — used by the auth-failure recovery hook in the CLI. */
    public void authForUrl(String service, String url) {
        authMenu(service.toLowerCase(Locale.ROOT), url);
    }

    // ---------- auth flow ----------

    private void authMenu(String service, String prefilledUrl) {
        while (true) {
            List<String> instances = listAuthInstances(service);
            List<String> choices = new ArrayList<>(instances);
            choices.add("Add new instance" + (prefilledUrl == null ? "" : " (" + prefilledUrl + ")"));
            choices.add("Back");
            int idx = prompt.select(service.substring(0, 1).toUpperCase(Locale.ROOT)
                    + service.substring(1) + " credentials", choices);
            if (idx < 0 || idx == choices.size() - 1) return;
            if (idx == choices.size() - 2) {
                // Add new — use prefilledUrl on first invocation, then clear it.
                addAuthInstance(service, prefilledUrl);
                prefilledUrl = null;
                continue;
            }
            String url = instances.get(idx);
            instanceMenu(service, url);
        }
    }

    private void addAuthInstance(String service, String prefilledUrl) {
        prompt.println();
        String url = prompt.text("Instance URL (e.g. https://company.atlassian.net)", prefilledUrl);
        if (url.isBlank()) {
            prompt.println("URL cannot be empty.");
            return;
        }
        url = UrlParsing.normalizeInstanceUrl(url);
        captureAuthFields(service, url);
    }

    private void instanceMenu(String service, String url) {
        while (true) {
            int idx = prompt.select("Instance: " + url,
                    List.of("Edit credentials", "Remove instance", "Back"));
            switch (idx) {
                case 0 -> captureAuthFields(service, url);
                case 1 -> {
                    if (prompt.confirm("Remove credentials for " + url + "?", false)) {
                        removeAuthInstance(service, url);
                    }
                    return;
                }
                default -> { return; }
            }
        }
    }

    private void captureAuthFields(String service, String url) {
        prompt.println();
        prompt.println("Enter credentials for " + url);
        prompt.println("(leave a value blank to keep the current setting; empty for none)");
        String username = prompt.text("Username (email for Atlassian Cloud)", currentSecret(service, url, "username"));
        String apiToken = prompt.secret("API token (recommended for Cloud)");
        String pat      = prompt.secret("Personal Access Token (used for Server/DC; leave blank if using API token)");
        String cloudId  = prompt.text("Cloud ID (auto-detected for *.atlassian.net; usually leave blank)",
                currentSecret(service, url, "cloud_id"));
        String apiUrl   = prompt.text(
                "REST API URL (only set if the API is on a different host than the page URL; "
                        + "e.g. https://confluencews.example.com)",
                currentSecret(service, url, "api_url"));

        try {
            store.setByKeys(List.of("auth", service, url, "username"), username);
            if (!apiToken.isEmpty()) store.setByKeys(List.of("auth", service, url, "api_token"), apiToken);
            if (!pat.isEmpty())      store.setByKeys(List.of("auth", service, url, "pat"), pat);
            store.setByKeys(List.of("auth", service, url, "cloud_id"), cloudId);
            store.setByKeys(List.of("auth", service, url, "api_url"), apiUrl);
            prompt.println("Saved.");
        } catch (ConfigException e) {
            prompt.println("Failed to save: " + e.getMessage());
        }
    }

    private void removeAuthInstance(String service, String url) {
        ObjectNode root = store.loadAsNode();
        JsonNode auth = root.path("auth");
        if (auth instanceof ObjectNode authObj) {
            JsonNode svc = authObj.path(service);
            if (svc instanceof ObjectNode svcObj) {
                svcObj.remove(url);
            }
        }
        store.save(parseAndValidate(root));
        prompt.println("Removed " + url + ".");
    }

    private List<String> listAuthInstances(String service) {
        List<String> out = new ArrayList<>();
        JsonNode svc = store.loadAsNode().path("auth").path(service);
        if (svc instanceof ObjectNode obj) {
            Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) out.add(it.next());
        }
        return out;
    }

    private String currentSecret(String service, String url, String field) {
        JsonNode value = store.getByKeys(List.of("auth", service, url, field));
        return value == null || value.isNull() ? "" : value.asText("");
    }

    // ---------- scalar edit ----------

    private void editScalarPrompted() {
        String key = prompt.text("Setting key (dot notation, e.g. export.log_level)", "");
        if (!key.isBlank()) editScalar(key);
    }

    private void editScalar(String key) {
        JsonNode current = store.getByPath(key);
        if (current == null || current.isMissingNode()) {
            prompt.println("Key '" + key + "' not found.");
            return;
        }
        if (current.isObject()) {
            prompt.println("'" + key + "' is a section, not a single value. Use a leaf key (e.g. "
                    + key + ".log_level).");
            return;
        }
        if (current.isBoolean()) {
            boolean updated = prompt.confirm("Set " + key + " to true?", current.asBoolean());
            saveScalar(key, updated);
        } else if (current.isInt() || current.isLong()) {
            String raw = prompt.text("New value for " + key, current.asText());
            try {
                saveScalar(key, Long.parseLong(raw.trim()));
            } catch (NumberFormatException e) {
                prompt.println("Not an integer.");
            }
        } else {
            String raw = prompt.text("New value for " + key, current.asText(""));
            saveScalar(key, raw);
        }
    }

    private void saveScalar(String key, Object value) {
        try {
            store.setByPath(key, value);
            prompt.println("Saved.");
        } catch (ConfigException e) {
            prompt.println("Failed: " + e.getMessage());
        }
    }

    // ---------- reset ----------

    private void resetMenu() {
        if (prompt.confirm("Reset ALL configuration to defaults?", false)) {
            store.resetToDefaults(null);
            prompt.println("Configuration reset to defaults.");
        }
    }

    private de.skerkewitz.jcme.config.AppConfig parseAndValidate(ObjectNode root) {
        try {
            return store.jsonMapper().treeToValue(root, de.skerkewitz.jcme.config.AppConfig.class);
        } catch (Exception e) {
            throw new ConfigException("Invalid configuration: " + e.getMessage(), e);
        }
    }

    /** For tests: number of configured instances for a service. */
    int instanceCount(String service) {
        return listAuthInstances(service).size();
    }
}
