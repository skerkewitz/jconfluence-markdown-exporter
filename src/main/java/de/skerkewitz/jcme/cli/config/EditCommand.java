package de.skerkewitz.jcme.cli.config;

import de.skerkewitz.jcme.config.ConfigStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "edit",
        description = "Open the interactive editor for a specific config key.",
        mixinStandardHelpOptions = true
)
public class EditCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "KEY",
            description = "Config key to open in the interactive editor "
                    + "(e.g. auth.confluence, auth.jira, export.log_level).")
    String key;

    @Override
    public void run() {
        new InteractiveMenu(new ConfigStore()).editKey(key);
    }
}
