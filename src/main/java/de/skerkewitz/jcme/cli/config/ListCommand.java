package de.skerkewitz.jcme.cli.config;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
        name = "list",
        description = "Print the current configuration as YAML (default) or JSON.",
        mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    @Option(names = {"-o", "--output"}, description = "Output format: yaml (default) or json.")
    String output = "yaml";

    @Override
    public Integer call() {
        ConfigStore store = new ConfigStore();
        JsonNode node = store.loadAsNode();
        String fmt = output.toLowerCase();
        switch (fmt) {
            case "json" -> System.out.println(store.toJson(node));
            case "yaml", "yml" -> System.out.print(store.toYaml(node));
            default -> {
                System.err.println("Unknown format '" + output + "': expected 'yaml' or 'json'.");
                return 1;
            }
        }
        return 0;
    }
}
