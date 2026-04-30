package de.skerkewitz.jcme.cli;

import de.skerkewitz.jcme.export.ExportService;
import de.skerkewitz.jcme.export.ExportStats;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.function.BiFunction;

@Command(
        name = "spaces",
        aliases = {"space"},
        description = "Export all pages in one or more Confluence spaces by URL.",
        mixinStandardHelpOptions = true
)
public class SpacesCommand extends ExportCommandBase {

    @Parameters(arity = "1..*", paramLabel = "SPACE_URL",
            description = "One or more Confluence space URLs.")
    List<String> spaceUrls;

    @Override
    protected List<String> urls() { return spaceUrls; }

    @Override
    protected BiFunction<ExportService, List<String>, ExportStats> exportFunction() {
        return ExportService::exportSpaces;
    }
}
