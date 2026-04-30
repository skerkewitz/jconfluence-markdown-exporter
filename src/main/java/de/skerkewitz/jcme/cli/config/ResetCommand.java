package de.skerkewitz.jcme.cli.config;

import de.skerkewitz.jcme.config.ConfigException;
import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(
        name = "reset",
        description = "Reset configuration to defaults (entire config or a single key).",
        mixinStandardHelpOptions = true
)
public class ResetCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", paramLabel = "KEY",
            description = "Optional dot-notation key to reset. If omitted, resets everything.")
    String key;

    @Option(names = {"-y", "--yes"}, description = "Skip the confirmation prompt.")
    boolean yes;

    @Override
    public Integer call() {
        if (!yes) {
            String target = key == null ? "all configuration" : "'" + key + "'";
            System.out.print("Reset " + target + " to defaults? [y/N]: ");
            String answer = readLine().trim().toLowerCase();
            if (!answer.equals("y") && !answer.equals("yes")) {
                System.out.println("Aborted.");
                return 1;
            }
        }
        ConfigStore store = new ConfigStore();
        try {
            store.resetToDefaults(key);
        } catch (ConfigException e) {
            System.err.println(e.getMessage());
            return 1;
        }
        String target = key == null ? "Configuration" : "'" + key + "'";
        System.out.println(target + " reset to defaults.");
        return 0;
    }

    private static String readLine() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "" : line;
        } catch (Exception e) {
            return "";
        }
    }
}
