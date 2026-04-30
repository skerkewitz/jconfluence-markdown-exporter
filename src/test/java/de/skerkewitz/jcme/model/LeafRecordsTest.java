package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeafRecordsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode parse(String s) throws Exception {
        return JSON.readTree(s);
    }

    @Test
    void user_from_json_extracts_all_fields() throws Exception {
        User u = User.fromJson(parse("""
                {
                  "accountId": "123",
                  "username": "alice",
                  "displayName": "Alice Smith",
                  "publicName": "alice",
                  "email": "alice@example.com"
                }
                """));
        assertThat(u.accountId()).isEqualTo("123");
        assertThat(u.username()).isEqualTo("alice");
        assertThat(u.displayName()).isEqualTo("Alice Smith");
        assertThat(u.email()).isEqualTo("alice@example.com");
    }

    @Test
    void user_from_json_handles_missing_fields() throws Exception {
        User u = User.fromJson(parse("{}"));
        assertThat(u.accountId()).isEmpty();
        assertThat(u.email()).isEmpty();
    }

    @Test
    void user_from_null_returns_empty() {
        assertThat(User.fromJson(null)).isEqualTo(User.empty());
    }

    @Test
    void version_from_json_includes_user() throws Exception {
        Version v = Version.fromJson(parse("""
                {"number": 5, "when": "2024-01-01T00:00:00Z", "friendlyWhen": "yesterday",
                 "by": {"accountId": "u1", "displayName": "Bob"}}
                """));
        assertThat(v.number()).isEqualTo(5);
        assertThat(v.when()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(v.by().displayName()).isEqualTo("Bob");
    }

    @Test
    void version_empty_when_missing() throws Exception {
        Version v = Version.fromJson(parse("{}"));
        assertThat(v.number()).isEqualTo(0);
        assertThat(v.by()).isEqualTo(User.empty());
    }

    @Test
    void label_from_json() throws Exception {
        Label l = Label.fromJson(parse("{\"id\":\"42\",\"name\":\"important\",\"prefix\":\"global\"}"));
        assertThat(l).isEqualTo(new Label("42", "important", "global"));
    }

    @Test
    void jira_issue_from_json_extracts_status_name() throws Exception {
        JiraIssue issue = JiraIssue.fromJson(parse("""
                {"key": "PROJ-1", "fields": {
                    "summary": "Fix bug",
                    "description": "Bug details",
                    "status": {"name": "In Progress"}
                }}
                """));
        assertThat(issue.key()).isEqualTo("PROJ-1");
        assertThat(issue.summary()).isEqualTo("Fix bug");
        assertThat(issue.status()).isEqualTo("In Progress");
    }

    @Test
    void jira_issue_handles_missing_fields() throws Exception {
        JiraIssue issue = JiraIssue.fromJson(parse("{\"key\":\"K\"}"));
        assertThat(issue.key()).isEqualTo("K");
        assertThat(issue.summary()).isEmpty();
        assertThat(issue.status()).isEmpty();
    }
}
