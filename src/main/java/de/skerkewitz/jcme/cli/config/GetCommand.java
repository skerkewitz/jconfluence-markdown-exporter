package de.skerkewitz.jcme.cli.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(
        name = "get",
        description = "Print the current value of a single config key.",
        mixinStandardHelpOptions = true
)
public class GetCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "KEY",
            description = "Config key in dot notation (e.g. export.log_level).")
    String key;

    @Override
    public Integer call() {
        ConfigStore store = new ConfigStore();
        JsonNode value = store.getByPath(key);
        if (value == null || value.isMissingNode()) {
            System.err.println("Key '" + key + "' not found.");
            return 1;
        }
        if (value.isObject() || value.isArray()) {
            System.out.print(store.toYaml(value));
        } else {
            System.out.println(value.asText());
        }
        return 0;
    }
}
