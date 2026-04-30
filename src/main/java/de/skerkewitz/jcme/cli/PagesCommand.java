package de.skerkewitz.jcme.cli;

import de.skerkewitz.jcme.export.ExportService;
import de.skerkewitz.jcme.export.ExportStats;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.function.BiFunction;

@Command(
        name = "pages",
        aliases = {"page"},
        description = "Export one or more Confluence pages by URL to Markdown.",
        mixinStandardHelpOptions = true
)
public class PagesCommand extends ExportCommandBase {

    @Parameters(arity = "1..*", paramLabel = "PAGE_URL",
            description = "One or more Confluence page URLs.")
    List<String> pageUrls;

    @Override
    protected List<String> urls() { return pageUrls; }

    @Override
    protected BiFunction<ExportService, List<String>, ExportStats> exportFunction() {
        return ExportService::exportPages;
    }
}
