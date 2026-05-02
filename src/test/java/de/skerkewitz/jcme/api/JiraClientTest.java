package de.skerkewitz.jcme.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.api.exceptions.JiraAuthenticationException;
import de.skerkewitz.jcme.config.ApiDetails;
import de.skerkewitz.jcme.config.ConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraClientTest {

    private TestHttpServer server;
    private JiraClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestHttpServer();
        ConnectionConfig conn = new ConnectionConfig(false, 1, 0, 0, List.of(), false, 5, false, 1);
        client = new JiraClient(server.baseUrl(), new HttpExecutor(ApiDetails.empty(), conn));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void get_issue_returns_json() {
        server.onGet("/rest/api/2/issue/PROJ-1", 200,
                "{\"key\":\"PROJ-1\",\"fields\":{\"summary\":\"hi\"}}", Map.of());

        JsonNode issue = client.getIssue(de.skerkewitz.jcme.model.IssueKey.of("PROJ-1"));

        assertThat(issue.get("key").asText()).isEqualTo("PROJ-1");
        assertThat(issue.get("fields").get("summary").asText()).isEqualTo("hi");
    }

    @Test
    void detects_seraph_auth_failure_header() {
        server.onGet("/rest/api/2/issue/PROJ-1", 200, "irrelevant",
                Map.of("X-Seraph-Loginreason", "AUTHENTICATED_FAILED"));

        assertThatThrownBy(() -> client.getIssue(de.skerkewitz.jcme.model.IssueKey.of("PROJ-1")))
                .isInstanceOf(JiraAuthenticationException.class)
                .hasMessageContaining("Jira authentication failed");
    }
}
