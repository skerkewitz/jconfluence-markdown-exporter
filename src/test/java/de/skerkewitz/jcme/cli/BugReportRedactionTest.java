package de.skerkewitz.jcme.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the secret-redaction logic used by {@code jcme bugreport} masks every credential
 * field (including the {@code api_url} override added later) and rewrites Atlassian-Cloud
 * hostnames to the partial form so the user can still tell which kind of instance was used.
 */
class BugReportRedactionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void redacts_all_credential_fields_including_api_url() throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree("""
                {
                  "auth": {
                    "confluence": {
                      "https://company.atlassian.net": {
                        "username": "alice@example.com",
                        "api_token": "secret-token",
                        "pat": "",
                        "cloud_id": "abc-123",
                        "api_url": "https://internal-rest.company.com"
                      }
                    },
                    "jira": {}
                  },
                  "export": {"output_path": "/tmp/private-output"}
                }
                """);

        BugReportCommand.redact(root);

        ObjectNode confluence = (ObjectNode) root.path("auth").path("confluence");
        // The Cloud hostname is reduced to the partial form so the kind of instance is still visible
        assertThat(confluence.fieldNames().next()).isEqualTo("https://******.atlassian.net");
        ObjectNode details = (ObjectNode) confluence.fields().next().getValue();
        assertThat(details.get("username").asText()).isEqualTo("[redacted]");
        assertThat(details.get("api_token").asText()).isEqualTo("[redacted]");
        assertThat(details.get("cloud_id").asText()).isEqualTo("[redacted]");
        assertThat(details.get("api_url").asText()).isEqualTo("[redacted]");
        // Empty fields stay empty rather than being filled with [redacted]
        assertThat(details.get("pat").asText()).isEmpty();

        // Output path also redacted
        assertThat(root.path("export").path("output_path").asText()).isEqualTo("[redacted]");
    }

    @Test
    void redacts_non_cloud_hostnames_completely() throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree("""
                {
                  "auth": {
                    "confluence": {
                      "https://wiki.internal.company.com": {
                        "username": "bob",
                        "api_token": "x",
                        "pat": "",
                        "cloud_id": ""
                      }
                    },
                    "jira": {}
                  },
                  "export": {"output_path": ""}
                }
                """);

        BugReportCommand.redact(root);

        ObjectNode confluence = (ObjectNode) root.path("auth").path("confluence");
        // Non-Cloud hostnames are fully replaced
        assertThat(confluence.fieldNames().next()).isEqualTo("[redacted]");
    }

    @Test
    void leaves_empty_credentials_alone() throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree("""
                {
                  "auth": {
                    "confluence": {
                      "https://x.atlassian.net": {
                        "username": "",
                        "api_token": "",
                        "pat": "",
                        "cloud_id": "",
                        "api_url": ""
                      }
                    },
                    "jira": {}
                  },
                  "export": {}
                }
                """);

        BugReportCommand.redact(root);

        ObjectNode details = (ObjectNode) root.path("auth").path("confluence")
                .fields().next().getValue();
        assertThat(details.get("username").asText()).isEmpty();
        assertThat(details.get("api_token").asText()).isEmpty();
        assertThat(details.get("api_url").asText()).isEmpty();
    }
}
