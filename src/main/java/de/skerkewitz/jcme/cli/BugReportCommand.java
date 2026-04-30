package de.skerkewitz.jcme.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.skerkewitz.jcme.AppInfo;
import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

@Command(
        name = "bugreport",
        description = "Print diagnostic info (version, OS, redacted config) for filing a bug report.",
        mixinStandardHelpOptions = true
)
public class BugReportCommand implements Runnable {

    private static final String REDACTED = "[redacted]";
    private static final String ATLASSIAN_NET = "atlassian.net";

    @Override
    public void run() {
        ConfigStore store = new ConfigStore();
        ObjectNode node = store.loadAsNode();
        redact(node);

        StringBuilder out = new StringBuilder();
        out.append("## Bug Report Diagnostic Info\n\n");
        out.append("### Version\n").append(AppInfo.NAME).append(" ").append(AppInfo.VERSION).append("\n\n");
        out.append("### System\n");
        out.append("Java: ").append(System.getProperty("java.version")).append("\n");
        out.append("Platform: ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version")).append("\n");
        out.append("Architecture: ").append(System.getProperty("os.arch")).append("\n\n");
        out.append("### Config\n");
        out.append("Config file: ").append(REDACTED).append("\n");
        out.append("```yaml\n").append(store.toYaml(node).stripTrailing()).append("\n```");
        System.out.println(out);
    }

    private static void redact(ObjectNode root) {
        JsonNode auth = root.get("auth");
        if (auth instanceof ObjectNode authObj) {
            for (String service : new String[]{"confluence", "jira"}) {
                JsonNode serviceNode = authObj.get(service);
                if (!(serviceNode instanceof ObjectNode serviceObj)) continue;
                ObjectNode replacement = serviceObj.objectNode();
                Iterator<Map.Entry<String, JsonNode>> entries = serviceObj.fields();
                while (entries.hasNext()) {
                    Map.Entry<String, JsonNode> entry = entries.next();
                    String url = entry.getKey();
                    JsonNode details = entry.getValue();
                    if (details instanceof ObjectNode detailsObj) {
                        for (String secret : new String[]{"api_token", "pat", "username", "cloud_id", "api_url"}) {
                            JsonNode v = detailsObj.get(secret);
                            if (v != null && !v.asText().isEmpty()) {
                                detailsObj.put(secret, REDACTED);
                            }
                        }
                    }
                    replacement.set(redactUrl(url), details);
                }
                authObj.set(service, replacement);
            }
        }
        JsonNode export = root.get("export");
        if (export instanceof ObjectNode exportObj) {
            JsonNode outPath = exportObj.get("output_path");
            if (outPath != null && !outPath.asText().isEmpty()) {
                exportObj.put("output_path", REDACTED);
            }
        }
    }

    private static String redactUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return REDACTED;
            if (host.equals(ATLASSIAN_NET) || host.endsWith("." + ATLASSIAN_NET)) {
                return "https://******." + ATLASSIAN_NET;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return REDACTED;
    }
}
