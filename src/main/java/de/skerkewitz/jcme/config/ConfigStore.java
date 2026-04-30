package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import net.harawata.appdirs.AppDirsFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persistence and access layer for the application configuration.
 *
 * <p>The config file is JSON. ENV vars (prefix {@code JCME_}, nested delimiter {@code __})
 * overlay the file when {@link #loadEffective()} is called, mirroring the Python
 * pydantic-settings behaviour.
 */
public final class ConfigStore {

    public static final String ENV_PREFIX = "JCME_";
    public static final String ENV_DELIMITER = "__";
    public static final String ENV_CONFIG_PATH = "JCME_CONFIG_PATH";
    public static final String APP_NAME = "jconfluence-markdown-exporter";

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper YAML = new ObjectMapper(
            new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    private final Path configPath;
    private final Map<String, String> env;

    public ConfigStore() {
        this(resolveDefaultConfigPath(System.getenv()), System.getenv());
    }

    public ConfigStore(Path configPath, Map<String, String> env) {
        this.configPath = configPath;
        this.env = env;
    }

    public Path configPath() {
        return configPath;
    }

    /** Resolve the config file path: {@code JCME_CONFIG_PATH} overrides; else OS-specific app dir. */
    public static Path resolveDefaultConfigPath(Map<String, String> env) {
        String override = env.get(ENV_CONFIG_PATH);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        String dir = AppDirsFactory.getInstance().getUserConfigDir(APP_NAME, null, null);
        return Paths.get(dir, "app_data.json");
    }

    /** Load the persisted config from disk. Returns defaults if file missing or invalid. */
    public AppConfig load() {
        ObjectNode node = loadAsNode();
        return validate(node);
    }

    /** Load config + overlay env-var overrides. */
    public AppConfig loadEffective() {
        ObjectNode node = loadAsNode();
        applyEnvOverrides(node, env);
        return validate(node);
    }

    /** Save the given config to disk (creates parent dirs as needed). */
    public void save(AppConfig config) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = JSON.writeValueAsString(config);
            Path tmp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ConfigException("Failed to save config to " + configPath, e);
        }
    }

    /** Load the raw JSON tree (ObjectNode); returns defaults tree if missing/invalid. */
    public ObjectNode loadAsNode() {
        if (!Files.exists(configPath)) {
            return JSON.valueToTree(AppConfig.defaults());
        }
        try {
            JsonNode parsed = JSON.readTree(Files.readString(configPath));
            if (parsed instanceof ObjectNode obj) {
                ObjectNode defaults = JSON.valueToTree(AppConfig.defaults());
                deepMerge(defaults, obj);
                return defaults;
            }
        } catch (IOException ignored) {
            // fall through to defaults
        }
        return JSON.valueToTree(AppConfig.defaults());
    }

    /** Set a single value by dot-notation path and save. */
    public void setByPath(String dotPath, Object value) {
        setByKeys(List.of(dotPath.split("\\.")), value);
    }

    /** Set a single value by an explicit list of key components and save. */
    public void setByKeys(List<String> keys, Object value) {
        if (keys.isEmpty()) {
            throw new ConfigException("Empty config key");
        }
        ObjectNode node = loadAsNode();
        setNested(node, keys, JSON.valueToTree(value));
        AppConfig validated = validate(node);
        save(validated);
    }

    /** Get the JsonNode at a dot-notation path, or {@code null} if missing. */
    public JsonNode getByPath(String dotPath) {
        return getByKeys(List.of(dotPath.split("\\.")));
    }

    public JsonNode getByKeys(List<String> keys) {
        JsonNode current = loadAsNode();
        for (String k : keys) {
            if (current == null || current.isNull()) return null;
            current = current.get(k);
        }
        return current;
    }

    /** Reset config (all or a single dot-path) to defaults and save. */
    public void resetToDefaults(String dotPath) {
        if (dotPath == null || dotPath.isBlank()) {
            save(AppConfig.defaults());
            return;
        }
        List<String> keys = List.of(dotPath.split("\\."));
        JsonNode defaultValue = navigate(JSON.valueToTree(AppConfig.defaults()), keys);
        if (defaultValue == null) {
            throw new ConfigException("Invalid config path: " + dotPath);
        }
        ObjectNode node = loadAsNode();
        setNested(node, keys, defaultValue.deepCopy());
        save(validate(node));
    }

    /** Render a JsonNode as YAML (used by config list / config get). */
    public String toYaml(JsonNode node) {
        try {
            return YAML.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ConfigException("Failed to render YAML", e);
        }
    }

    /** Render a JsonNode as pretty JSON. */
    public String toJson(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ConfigException("Failed to render JSON", e);
        }
    }

    public ObjectMapper jsonMapper() {
        return JSON;
    }

    /** Parse a CLI value string: try JSON first, then accept Python-style True/False, else raw string. */
    public static Object parseCliValue(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (IOException ignored) {
            // not JSON
        }
        String lower = raw.toLowerCase();
        if (lower.equals("true")) return true;
        if (lower.equals("false")) return false;
        return raw;
    }

    private AppConfig validate(JsonNode node) {
        try {
            return JSON.treeToValue(node, AppConfig.class);
        } catch (JsonProcessingException e) {
            throw new ConfigException("Invalid configuration: " + e.getOriginalMessage(), e);
        }
    }

    private static void setNested(ObjectNode root, List<String> keys, JsonNode value) {
        ObjectNode cursor = root;
        for (int i = 0; i < keys.size() - 1; i++) {
            String k = keys.get(i);
            JsonNode next = cursor.get(k);
            if (!(next instanceof ObjectNode)) {
                next = JSON.createObjectNode();
                cursor.set(k, next);
            }
            cursor = (ObjectNode) next;
        }
        cursor.set(keys.get(keys.size() - 1), value);
    }

    private static JsonNode navigate(JsonNode root, List<String> keys) {
        JsonNode current = root;
        for (String k : keys) {
            if (current == null) return null;
            current = current.get(k);
        }
        return current;
    }

    /** Recursively merge {@code overlay} into {@code base} (in place). Overlay wins. */
    private static void deepMerge(ObjectNode base, ObjectNode overlay) {
        overlay.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode incoming = entry.getValue();
            JsonNode existing = base.get(key);
            if (existing instanceof ObjectNode existingObj && incoming instanceof ObjectNode incomingObj) {
                deepMerge(existingObj, incomingObj);
            } else {
                base.set(key, incoming);
            }
        });
    }

    /**
     * Apply environment-variable overrides to the JSON tree.
     * Env vars matching {@code JCME_<SECTION>__<FIELD>[...]} are converted to nested keys.
     * Values are JSON-parsed first (so booleans/numbers/arrays work), falling back to strings.
     */
    static void applyEnvOverrides(ObjectNode root, Map<String, String> env) {
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(ENV_PREFIX)) continue;
            if (name.equals(ENV_CONFIG_PATH)) continue;
            String stripped = name.substring(ENV_PREFIX.length());
            if (stripped.isEmpty()) continue;
            String[] parts = stripped.split(ENV_DELIMITER);
            List<String> keys = new ArrayList<>(parts.length);
            for (String p : parts) {
                if (p.isEmpty()) continue;
                keys.add(p.toLowerCase());
            }
            if (keys.isEmpty()) continue;
            JsonNode value = parseEnvValue(entry.getValue());
            setNested(root, keys, value);
        }
    }

    private static JsonNode parseEnvValue(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (IOException ignored) {
            // not JSON
        }
        String lower = raw.toLowerCase();
        if (lower.equals("true")) return JSON.getNodeFactory().booleanNode(true);
        if (lower.equals("false")) return JSON.getNodeFactory().booleanNode(false);
        return JSON.getNodeFactory().textNode(raw);
    }

}
