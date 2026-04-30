package de.skerkewitz.jcme.cli.config;

import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;

@Command(
        name = "config",
        description = "Manage configuration interactively or via subcommands.",
        subcommands = {
                ListCommand.class,
                GetCommand.class,
                SetCommand.class,
                PathCommand.class,
                ResetCommand.class,
                EditCommand.class
        },
        mixinStandardHelpOptions = true
)
public class ConfigCommand implements Runnable {
    @Override
    public void run() {
        new InteractiveMenu(new ConfigStore()).mainMenu();
    }
}
