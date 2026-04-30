package de.skerkewitz.jcme.cli;

import de.skerkewitz.jcme.AppInfo;
import picocli.CommandLine.Command;

@Command(
        name = "version",
        description = "Show the installed version of jconfluence-markdown-exporter.",
        mixinStandardHelpOptions = true
)
public class VersionCommand implements Runnable {
    @Override
    public void run() {
        System.out.println(AppInfo.NAME + " " + AppInfo.VERSION);
    }
}
