package de.skerkewitz.jcme.cli;

import de.skerkewitz.jcme.export.ExportService;
import de.skerkewitz.jcme.export.ExportStats;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.function.BiFunction;

@Command(
        name = "orgs",
        aliases = {"org"},
        description = "Export all spaces of one or more Confluence organizations.",
        mixinStandardHelpOptions = true
)
public class OrgsCommand extends ExportCommandBase {

    @Parameters(arity = "1..*", paramLabel = "BASE_URL",
            description = "One or more Confluence base URLs.")
    List<String> baseUrls;

    @Override
    protected List<String> urls() { return baseUrls; }

    @Override
    protected BiFunction<ExportService, List<String>, ExportStats> exportFunction() {
        return ExportService::exportOrganizations;
    }
}
