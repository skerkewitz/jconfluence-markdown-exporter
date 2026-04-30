package de.skerkewitz.jcme.cli.config;

import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;

@Command(
        name = "path",
        description = "Print the path to the configuration file.",
        mixinStandardHelpOptions = true
)
public class PathCommand implements Runnable {
    @Override
    public void run() {
        System.out.println(new ConfigStore().configPath());
    }
}
