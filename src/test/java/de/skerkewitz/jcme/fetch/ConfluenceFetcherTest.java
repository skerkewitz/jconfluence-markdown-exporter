package de.skerkewitz.jcme.fetch;

import de.skerkewitz.jcme.api.ApiClientFactory;
import de.skerkewitz.jcme.api.TestHttpServer;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.AuthConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.config.ConnectionConfig;
import de.skerkewitz.jcme.config.ExportConfig;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.Descendant;
import de.skerkewitz.jcme.model.Organization;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.Space;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfluenceFetcherTest {

    @TempDir
    Path tmp;
    private TestHttpServer server;
    private ConfluenceFetcher fetcher;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestHttpServer();
        Path cfg = tmp.resolve("cfg.json");
        ConfigStore store = new ConfigStore(cfg, Map.of());
        Map<String, ApiDetails> instances = new LinkedHashMap<>();
        instances.put(server.baseUrl(), new ApiDetails("alice", "tok", "", ""));
        store.save(new AppConfig(
                ExportConfig.defaults(),
                new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1),
                new AuthConfig(instances, new LinkedHashMap<>())));

        // verifyAuth() pings /rest/api/space; succeed.
        server.onGet("/rest/api/space", 200, "{\"results\":[{\"key\":\"X\",\"name\":\"X\"}]}", Map.of());

        ApiClientFactory factory = new ApiClientFactory(store);
        fetcher = new ConfluenceFetcher(factory, store);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void get_space_caches_result() {
        server.onGet("/rest/api/space/MYKEY", 200, """
                {"key":"MYKEY","name":"My Space",
                 "description":{"plain":{"value":"hi"}},
                 "homepage":{"id":"42"}}
                """, Map.of());

        Space first = fetcher.getSpace("MYKEY", server.baseUrl());
        Space second = fetcher.getSpace("MYKEY", server.baseUrl());

        assertThat(first.name()).isEqualTo("My Space");
        assertThat(first.homepage()).isEqualTo(42L);
        assertThat(first).isSameAs(second);
        assertThat(server.hits("/rest/api/space/MYKEY")).isEqualTo(1);
    }

    @Test
    void get_page_assembles_full_record() {
        server.onGet("/rest/api/space/KEY", 200, """
                {"key":"KEY","name":"Space","homepage":{"id":"42"}}
                """, Map.of());
        server.onGet("/rest/api/content/100", 200, """
                {
                  "id": "100",
                  "title": "My Page",
                  "_expandable": {"space": "/rest/api/space/KEY"},
                  "body": {
                    "view": {"value": "<p>view</p>"},
                    "export_view": {"value": "<p>export</p>"},
                    "editor2": {"value": "<root/>"}
                  },
                  "metadata": {"labels": {"results": [{"id":"1","name":"foo","prefix":"global"}]}},
                  "ancestors": [
                    {"id":"42","title":"Home","_expandable":{"space":"/rest/api/space/KEY"}},
                    {"id":"99","title":"Parent","_expandable":{"space":"/rest/api/space/KEY"}}
                  ],
                  "version": {"number": 7, "when": "2024-01-01T00:00:00Z", "friendlyWhen": "yesterday",
                              "by": {"accountId":"u1","displayName":"Bob"}}
                }
                """, Map.of());
        server.onGet("/rest/api/content/100/child/attachment", 200,
                "{\"results\":[],\"size\":0}", Map.of());

        Page p = fetcher.getPage(100, server.baseUrl());

        assertThat(p.id()).isEqualTo(100);
        assertThat(p.title()).isEqualTo("My Page");
        assertThat(p.body()).isEqualTo("<p>view</p>");
        assertThat(p.bodyExport()).isEqualTo("<p>export</p>");
        assertThat(p.editor2()).isEqualTo("<root/>");
        assertThat(p.labels()).hasSize(1);
        assertThat(p.labels().get(0).name()).isEqualTo("foo");
        // Python drops the first ancestor (homepage) — so 1 of 2 remain.
        assertThat(p.ancestors()).hasSize(1);
        assertThat(p.ancestors().get(0).title()).isEqualTo("Parent");
        assertThat(p.version().number()).isEqualTo(7);
        assertThat(p.space().key()).isEqualTo("KEY");
    }

    @Test
    void get_page_cache_hits_avoid_second_fetch() {
        server.onGet("/rest/api/space/K", 200, "{\"key\":\"K\",\"name\":\"K\"}", Map.of());
        server.onGet("/rest/api/content/200", 200, """
                {"id":"200","title":"P",
                 "_expandable":{"space":"/rest/api/space/K"},
                 "ancestors":[],"metadata":{"labels":{"results":[]}},
                 "body":{"view":{"value":""},"export_view":{"value":""},"editor2":{"value":""}},
                 "version":{"number":1}}
                """, Map.of());
        server.onGet("/rest/api/content/200/child/attachment", 200,
                "{\"results\":[],\"size\":0}", Map.of());

        Page first = fetcher.getPage(200, server.baseUrl());
        Page second = fetcher.getPage(200, server.baseUrl());

        assertThat(first).isSameAs(second);
        assertThat(server.hits("/rest/api/content/200")).isEqualTo(1);
    }

    @Test
    void get_page_returns_inaccessible_sentinel_on_404() {
        server.onGet("/rest/api/content/9999", 404, "not found", Map.of());

        Page p = fetcher.getPage(9999, server.baseUrl());

        assertThat(p.isInaccessible()).isTrue();
        assertThat(p.id()).isEqualTo(9999);
    }

    @Test
    void get_attachments_paginates_until_short_page() {
        // First page returns 50 results (full page) — fetcher should request again
        StringBuilder full = new StringBuilder("{\"size\":50,\"results\":[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) full.append(',');
            full.append(makeAttachmentJson("att" + i, "image/png", "f" + i));
        }
        full.append("]}");
        StringBuilder partial = new StringBuilder("{\"size\":3,\"results\":[");
        for (int i = 50; i < 53; i++) {
            if (i > 50) partial.append(',');
            partial.append(makeAttachmentJson("att" + i, "image/png", "f" + i));
        }
        partial.append("]}");
        server.onGetSequence("/rest/api/content/300/child/attachment", List.of(
                TestHttpServer.Response.of(200, full.toString()),
                TestHttpServer.Response.of(200, partial.toString())));
        server.onGet("/rest/api/space/SP", 200, "{\"key\":\"SP\",\"name\":\"sp\"}", Map.of());

        List<Attachment> result = fetcher.getAttachments(300, server.baseUrl());

        assertThat(result).hasSize(53);
        assertThat(server.hits("/rest/api/content/300/child/attachment")).isEqualTo(2);
    }

    @Test
    void get_descendants_uses_cql_and_follows_next_link() {
        server.onGet("/rest/api/space/K", 200, "{\"key\":\"K\",\"name\":\"K\"}", Map.of());
        // First CQL response includes a _links.next pointer
        String firstResponse = """
                {"results": [
                    {"id":"500","title":"Child A",
                     "_expandable":{"space":"/rest/api/space/K"},
                     "ancestors":[],
                     "version":{"number":1}}
                  ],
                  "_links": {"next":"/rest/api/content/search?cursor=abc"}
                }
                """;
        String secondResponse = """
                {"results": [
                    {"id":"501","title":"Child B",
                     "_expandable":{"space":"/rest/api/space/K"},
                     "ancestors":[],
                     "version":{"number":1}}
                  ],
                  "_links": {}
                }
                """;
        server.onGet("/rest/api/content/search", 200, firstResponse, Map.of());

        // Both phases of search land on /rest/api/content/search; queue two responses.
        server.onGetSequence("/rest/api/content/search", List.of(
                TestHttpServer.Response.of(200, firstResponse),
                TestHttpServer.Response.of(200, secondResponse)));

        Page parent = new Page(server.baseUrl(), 100, "Parent", Space.empty(server.baseUrl()),
                List.of(), de.skerkewitz.jcme.model.Version.empty(),
                "", "", "", List.of(), List.of());

        List<Descendant> descendants = fetcher.getDescendants(parent);
        assertThat(descendants).hasSize(2);
        assertThat(descendants).extracting(Descendant::title).containsExactly("Child A", "Child B");
    }

    @Test
    void get_organization_collects_all_pages_from_global_spaces() {
        server.onGet("/rest/api/space", 200, """
                {"results": [
                    {"key":"A","name":"Space A","homepage":{"id":"1"}},
                    {"key":"B","name":"Space B","homepage":{"id":"2"}}
                ]}
                """, Map.of());

        Organization org = fetcher.getOrganization(server.baseUrl());

        assertThat(org.spaces()).hasSize(2);
        assertThat(org.spaces()).extracting(Space::key).containsExactly("A", "B");
    }

    private static String makeAttachmentJson(String id, String mediaType, String fileId) {
        return """
                {
                  "id":"%s","title":"%s.png",
                  "_expandable":{"space":"/rest/api/space/SP"},
                  "extensions":{"fileSize":1,"mediaType":"%s","mediaTypeDescription":"PNG","fileId":"%s","comment":""},
                  "_links":{"download":"/download/%s"},
                  "container":{"id":"300","title":"Page","ancestors":[],"_expandable":{"space":"/rest/api/space/SP"}},
                  "version":{"number":1}
                }
                """.formatted(id, id, mediaType, fileId, id);
    }
}
