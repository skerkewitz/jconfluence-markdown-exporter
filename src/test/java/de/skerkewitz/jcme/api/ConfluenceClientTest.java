package de.skerkewitz.jcme.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.ConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluenceClientTest {

    private TestHttpServer server;
    private ConfluenceClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestHttpServer();
        ConnectionConfig conn = new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1);
        client = new ConfluenceClient(server.baseUrl(), new HttpExecutor(ApiDetails.empty(), conn));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void get_page_by_id_includes_expand_param() {
        server.onGet("/rest/api/content/42", 200,
                "{\"id\":\"42\",\"title\":\"Hello\"}", Map.of());

        JsonNode page = client.getPageById(42, "body.view,version");

        assertThat(page.get("id").asText()).isEqualTo("42");
        TestHttpServer.RecordedRequest req = server.requests("/rest/api/content/42").get(0);
        assertThat(req.query()).contains("expand=body.view%2Cversion");
    }

    @Test
    void get_attachments_uses_paging_params() {
        server.onGet("/rest/api/content/7/child/attachment", 200,
                "{\"results\":[],\"size\":0,\"start\":0,\"limit\":50}", Map.of());

        client.getAttachmentsFromContent(7, 50, 50, "container.ancestors,version");

        TestHttpServer.RecordedRequest req = server.requests("/rest/api/content/7/child/attachment").get(0);
        assertThat(req.query()).contains("start=50");
        assertThat(req.query()).contains("limit=50");
        assertThat(req.query()).contains("expand=container.ancestors%2Cversion");
    }

    @Test
    void get_v2_pages_emits_multiple_id_query_params() {
        server.onGet("/api/v2/pages", 200, "{\"results\":[]}", Map.of());

        client.getV2Pages(List.of("1", "2", "3"));

        TestHttpServer.RecordedRequest req = server.requests("/api/v2/pages").get(0);
        // Multiple id= parameters in any order
        long idCount = java.util.Arrays.stream(req.query().split("&"))
                .filter(p -> p.startsWith("id=")).count();
        assertThat(idCount).isEqualTo(3);
        assertThat(req.query()).contains("limit=3");
    }

    @Test
    void search_cql_passes_cql_and_limit() {
        server.onGet("/rest/api/content/search", 200, "{\"results\":[]}", Map.of());

        client.searchCql("type=page AND ancestor=42", "metadata.properties,ancestors,version", 250);

        TestHttpServer.RecordedRequest req = server.requests("/rest/api/content/search").get(0);
        assertThat(req.query()).contains("cql=type%3Dpage+AND+ancestor%3D42");
        assertThat(req.query()).contains("limit=250");
    }

    @Test
    void get_page_by_title_includes_space_and_title() {
        server.onGet("/rest/api/content", 200,
                "{\"results\":[{\"id\":\"42\"}]}", Map.of());

        JsonNode result = client.getPageByTitle("KEY", "My Page", "version");

        assertThat(result.get("results").get(0).get("id").asText()).isEqualTo("42");
        TestHttpServer.RecordedRequest req = server.requests("/rest/api/content").get(0);
        assertThat(req.query()).contains("spaceKey=KEY");
        assertThat(req.query()).contains("title=My+Page");
        assertThat(req.query()).contains("type=page");
    }

    @Test
    void download_attachment_returns_raw_bytes() {
        server.onGet("/download/file.png", 200, "binary-data-here", Map.of());

        byte[] bytes = client.downloadAttachment("/download/file.png");

        assertThat(new String(bytes)).isEqualTo("binary-data-here");
    }

    @Test
    void download_attachment_handles_absolute_url() {
        server.onGet("/download/file.bin", 200, "more-binary-data", Map.of());

        byte[] bytes = client.downloadAttachment(server.baseUrl() + "/download/file.bin");

        assertThat(new String(bytes)).isEqualTo("more-binary-data");
    }
}
