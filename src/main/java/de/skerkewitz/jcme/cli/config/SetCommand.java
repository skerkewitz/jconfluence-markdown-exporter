package de.skerkewitz.jcme.cli.config;

import de.skerkewitz.jcme.config.ConfigException;
import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "set",
        description = "Set one or more configuration values (KEY=VALUE pairs).",
        mixinStandardHelpOptions = true
)
public class SetCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", paramLabel = "KEY=VALUE",
            description = "One or more dot-notation key=value pairs.")
    List<String> keyValues;

    @Override
    public Integer call() {
        ConfigStore store = new ConfigStore();
        for (String kv : keyValues) {
            int eq = kv.indexOf('=');
            if (eq < 0) {
                System.err.println("Invalid format '" + kv + "': expected key=value.");
                return 1;
            }
            String key = kv.substring(0, eq).trim();
            String rawValue = kv.substring(eq + 1);
            Object value = ConfigStore.parseCliValue(rawValue);
            try {
                store.setByPath(key, value);
            } catch (ConfigException e) {
                System.err.println("Failed to set '" + key + "': " + e.getMessage());
                return 1;
            }
        }
        System.out.println("Configuration updated.");
        return 0;
    }
}
