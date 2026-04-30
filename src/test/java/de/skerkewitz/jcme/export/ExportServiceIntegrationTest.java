package de.skerkewitz.jcme.export;

import de.skerkewitz.jcme.api.TestHttpServer;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.AuthConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.config.ConnectionConfig;
import de.skerkewitz.jcme.config.ExportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that exercises {@link ExportService#exportPages} end-to-end against a
 * fake Confluence served by {@link TestHttpServer}.
 */
class ExportServiceIntegrationTest {

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
                "INFO",
                output.toString(),
                "relative",
                "{space_name}/{page_title}.md",
                "relative",
                "{space_name}/attachments/{attachment_file_id}{attachment_extension}",
                false, true, true,
                ExportConfig.DEFAULT_FILENAME_ENCODING,
                255, false, true, false, // jira enrichment off (no jira server)
                true, true,
                "lock.json", 250
        );
        ConnectionConfig conn = new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1);
        configStore.save(new AppConfig(export, conn, new AuthConfig(instances, new LinkedHashMap<>())));

        // verifyAuth() pings /rest/api/space?... — succeed
        server.onGet("/rest/api/space", 200,
                "{\"results\":[{\"key\":\"K\",\"name\":\"Space\",\"homepage\":{\"id\":\"1\"}}]}",
                Map.of());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void exports_a_simple_page_to_disk() throws Exception {
        // Space lookup
        server.onGet("/rest/api/space/K", 200,
                "{\"key\":\"K\",\"name\":\"Space\",\"homepage\":{\"id\":\"1\"}}", Map.of());
        // Page fetch
        server.onGet("/rest/api/content/100", 200, """
                {
                  "id":"100","title":"Hello",
                  "_expandable":{"space":"/rest/api/space/K"},
                  "ancestors":[],
                  "metadata":{"labels":{"results":[]}},
                  "body":{"view":{"value":"<p>hello world</p>"},"export_view":{"value":""},"editor2":{"value":""}},
                  "version":{"number":1}
                }
                """, Map.of());
        // No attachments
        server.onGet("/rest/api/content/100/child/attachment", 200,
                "{\"results\":[],\"size\":0}", Map.of());

        ExportService service = new ExportService(configStore);
        ExportStats stats = service.exportPages(List.of(
                server.baseUrl() + "/wiki/spaces/K/pages/100/Hello"));

        assertThat(stats.exported()).isEqualTo(1);
        assertThat(stats.failed()).isZero();
        Path md = output.resolve("Space/Hello.md");
        assertThat(Files.exists(md)).isTrue();
        String content = Files.readString(md);
        assertThat(content).contains("# Hello");
        assertThat(content).contains("hello world");
    }

    @Test
    void skips_unchanged_page_on_second_run() throws Exception {
        server.onGet("/rest/api/space/K", 200,
                "{\"key\":\"K\",\"name\":\"Space\",\"homepage\":{\"id\":\"1\"}}", Map.of());
        server.onGet("/rest/api/content/100", 200, """
                {"id":"100","title":"Hello",
                 "_expandable":{"space":"/rest/api/space/K"},
                 "ancestors":[],"metadata":{"labels":{"results":[]}},
                 "body":{"view":{"value":"<p>v1</p>"},"export_view":{"value":""},"editor2":{"value":""}},
                 "version":{"number":1}}
                """, Map.of());
        server.onGet("/rest/api/content/100/child/attachment", 200,
                "{\"results\":[],\"size\":0}", Map.of());

        ExportService service = new ExportService(configStore);
        String url = server.baseUrl() + "/wiki/spaces/K/pages/100/Hello";

        ExportStats first = service.exportPages(List.of(url));
        ExportStats second = service.exportPages(List.of(url));

        assertThat(first.exported()).isEqualTo(1);
        assertThat(second.exported()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
    }

    @Test
    void downloads_referenced_attachments() throws Exception {
        server.onGet("/rest/api/space/K", 200,
                "{\"key\":\"K\",\"name\":\"Space\",\"homepage\":{\"id\":\"1\"}}", Map.of());
        // Body references the attachment by file_id
        server.onGet("/rest/api/content/200", 200, """
                {"id":"200","title":"WithAttachment",
                 "_expandable":{"space":"/rest/api/space/K"},
                 "ancestors":[],"metadata":{"labels":{"results":[]}},
                 "body":{"view":{"value":"<img data-media-id=\\"file-guid-1\\" alt=\\"img\\"/>"},
                         "export_view":{"value":""},"editor2":{"value":""}},
                 "version":{"number":1}}
                """, Map.of());
        server.onGet("/rest/api/content/200/child/attachment", 200, """
                {"results":[
                  {"id":"att1","title":"img.png",
                   "_expandable":{"space":"/rest/api/space/K"},
                   "extensions":{"fileSize":3,"mediaType":"image/png","fileId":"file-guid-1","comment":""},
                   "_links":{"download":"/download/att1.png"},
                   "container":{"id":"200","title":"P","ancestors":[],"_expandable":{"space":"/rest/api/space/K"}},
                   "version":{"number":1}}
                 ],"size":1}
                """, Map.of());
        server.onGet("/download/att1.png", 200, "PNG", Map.of());

        ExportService service = new ExportService(configStore);
        ExportStats stats = service.exportPages(List.of(
                server.baseUrl() + "/wiki/spaces/K/pages/200/X"));

        assertThat(stats.exported()).isEqualTo(1);
        assertThat(stats.attachmentsExported()).isEqualTo(1);
        Path attFile = output.resolve("Space/attachments/file-guid-1.png");
        assertThat(Files.exists(attFile)).isTrue();
        assertThat(Files.readString(attFile)).isEqualTo("PNG");
    }

    @Test
    void cleans_up_pages_no_longer_in_confluence() throws Exception {
        server.onGet("/rest/api/space/K", 200,
                "{\"key\":\"K\",\"name\":\"Space\",\"homepage\":{\"id\":\"1\"}}", Map.of());

        // First run: export both pages 100 and 200
        server.onGetSequence("/rest/api/content/100", List.of(TestHttpServer.Response.of(200, """
                {"id":"100","title":"Page 100",
                 "_expandable":{"space":"/rest/api/space/K"},
                 "ancestors":[],"metadata":{"labels":{"results":[]}},
                 "body":{"view":{"value":"<p>x</p>"},"export_view":{"value":""},"editor2":{"value":""}},
                 "version":{"number":1}}
                """)));
        server.onGet("/rest/api/content/100/child/attachment", 200,
                "{\"results\":[],\"size\":0}", Map.of());
        server.onGet("/rest/api/content/200", 200, """
                {"id":"200","title":"Page 200",
                 "_expandable":{"space":"/rest/api/space/K"},
                 "ancestors":[],"metadata":{"labels":{"results":[]}},
                 "body":{"view":{"value":"<p>x</p>"},"export_view":{"value":""},"editor2":{"value":""}},
                 "version":{"number":1}}
                """, Map.of());
        server.onGet("/rest/api/content/200/child/attachment", 200,
                "{\"results\":[],\"size\":0}", Map.of());

        ExportService service = new ExportService(configStore);
        ExportStats first = service.exportPages(List.of(
                server.baseUrl() + "/wiki/spaces/K/pages/100/X",
                server.baseUrl() + "/wiki/spaces/K/pages/200/Y"));
        assertThat(first.exported()).isEqualTo(2);

        // Second run: only export page 100. The CQL existence check returns just "100".
        server.onGet("/rest/api/content/search", 200, "{\"results\":[{\"id\":\"100\"}]}", Map.of());

        ExportService service2 = new ExportService(configStore);
        ExportStats second = service2.exportPages(List.of(
                server.baseUrl() + "/wiki/spaces/K/pages/100/X"));

        assertThat(second.removed()).isEqualTo(1);
        assertThat(Files.exists(output.resolve("Space/Page 100.md"))).isTrue();
        assertThat(Files.exists(output.resolve("Space/Page 200.md"))).isFalse();
    }
}
