package de.skerkewitz.jcme;

import de.skerkewitz.jcme.cli.BugReportCommand;
import de.skerkewitz.jcme.cli.OrgsCommand;
import de.skerkewitz.jcme.cli.PagesCommand;
import de.skerkewitz.jcme.cli.PagesWithDescendantsCommand;
import de.skerkewitz.jcme.cli.SpacesCommand;
import de.skerkewitz.jcme.cli.VersionCommand;
import de.skerkewitz.jcme.cli.config.ConfigCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "jcme",
        mixinStandardHelpOptions = true,
        version = AppInfo.VERSION,
        description = "Export Confluence pages, spaces, or entire organizations to Markdown files.",
        subcommands = {
                PagesCommand.class,
                PagesWithDescendantsCommand.class,
                SpacesCommand.class,
                OrgsCommand.class,
                ConfigCommand.class,
                VersionCommand.class,
                BugReportCommand.class
        }
)
public class App implements Runnable {

    @Override
    public void run() {
        // No-op: picocli prints help when no subcommand is given.
    }

    public static void main(String[] args) {
        Logging.initFromConfig();
        CommandLine cmd = new CommandLine(new App());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        if (args.length == 0) {
            cmd.usage(System.out);
            System.exit(0);
        }
        int exit = cmd.execute(args);
        System.exit(exit);
    }
}
