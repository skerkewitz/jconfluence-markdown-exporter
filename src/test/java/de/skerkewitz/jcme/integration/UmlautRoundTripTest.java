package de.skerkewitz.jcme.integration;

import de.skerkewitz.jcme.api.TestHttpServer;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.AuthConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.config.ConnectionConfig;
import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.export.ExportService;
import de.skerkewitz.jcme.export.ExportStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that German umlauts (ä ö ü ß and capitals) survive the full pipeline:
 * UTF-8 JSON over HTTP → Jackson → Java String → markdown body → file on disk.
 *
 * <p>If this test passes but you still see broken umlauts on stdout/stderr, the data is
 * fine and the problem is the terminal codepage (e.g. cmd.exe defaulting to cp850 / cp1252).
 * Run {@code chcp 65001} on Windows or check the markdown file in a UTF-8-aware editor.
 */
class UmlautRoundTripTest {

    private TestHttpServer server;
    private Path output;
    private ConfigStore configStore;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        server = new TestHttpServer();
        output = tmp.resolve("out");
        Files.createDirectories(output);

        Path configFile = tmp.resolve("cfg.json");
        configStore = new ConfigStore(configFile, Map.of());
        Map<String, ApiDetails> instances = new LinkedHashMap<>();
        instances.put(server.baseUrl(), new ApiDetails("alice", "tok", "", ""));

        ExportConfig export = new ExportConfig(
                "INFO", output.toString(),
                "relative", "{space_name}/{page_title}.md",
                "relative", "{space_name}/attachments/{attachment_file_id}{attachment_extension}",
                false, true, true,
                ExportConfig.DEFAULT_FILENAME_ENCODING,
                255, false, true, false, true, true,
                "lock.json", 250
        );
        ConnectionConfig conn = new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1);
        configStore.save(new AppConfig(export, conn, new AuthConfig(instances, new LinkedHashMap<>())));

        // verifyAuth() — pings /rest/api/space?... — succeed.
        server.onGet("/rest/api/space", 200, "{\"results\":[]}", Map.of());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void umlauts_in_title_and_body_survive_round_trip() throws Exception {
        String title = "Über die Größe der Bäume — ÄÖÜß";
        String body = "<p>Heute fällt das Lösegeld für die Götter.</p>";

        server.onGet("/rest/api/space/K", 200,
                "{\"key\":\"K\",\"name\":\"Mein Söße Space\",\"homepage\":{\"id\":\"1\"}}",
                Map.of("Content-Type", "application/json; charset=UTF-8"));
        server.onGet("/rest/api/content/100", 200, """
                {"id":"100","title":"%s",
                 "_expandable":{"space":"/rest/api/space/K"},
                 "ancestors":[],"metadata":{"labels":{"results":[]}},
                 "body":{"view":{"value":"%s"},"export_view":{"value":""},"editor2":{"value":""}},
                 "version":{"number":1}}
                """.formatted(title, body),
                Map.of("Content-Type", "application/json; charset=UTF-8"));
        server.onGet("/rest/api/content/100/child/attachment", 200,
                "{\"results\":[],\"size\":0}", Map.of());

        ExportService service = new ExportService(configStore);
        ExportStats stats = service.exportPages(List.of(
                server.baseUrl() + "/wiki/spaces/K/pages/100/X"));

        assertThat(stats.exported()).isEqualTo(1);

        // Filename should contain the umlauts (default sanitizer leaves them alone).
        Path md = output.resolve("Mein Söße Space/" + title + ".md");
        assertThat(Files.exists(md))
                .as("Markdown file with umlauts in path: %s", md)
                .isTrue();

        // File content is UTF-8; read back explicitly.
        String content = Files.readString(md, StandardCharsets.UTF_8);
        assertThat(content)
                .as("H1 heading should contain the original umlauts")
                .contains("# " + title);
        assertThat(content)
                .as("Body text should contain the original umlauts")
                .contains("Heute fällt das Lösegeld für die Götter.");
    }

    @Test
    void filename_with_unicode_chars_writes_to_disk() throws Exception {
        // Sanity check: the OS / JDK can write files with non-ASCII names. If this
        // fails on Windows, java.io.tmpdir or the working drive may not support Unicode.
        String name = "Größenangabe.md";
        Path file = output.resolve(name);
        Files.writeString(file, "hello", StandardCharsets.UTF_8);

        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(file.getFileName().toString()).isEqualTo(name);
    }
}
