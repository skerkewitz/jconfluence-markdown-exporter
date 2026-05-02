package de.skerkewitz.jcme.lockfile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the on-disk lockfile JSON layout. Each refactor phase ({@code PageId}, {@code BaseUrl},
 * {@code SpaceKey}, ...) introduces wrapper types that must serialize back to the exact same
 * shape — string-keyed nested maps, integer version, string export_path. If this test fails,
 * a wrapper type's {@code @JsonValue} / {@code @JsonCreator} pair is wrong.
 */
class LockfileWireFormatTest {

    private static final String LEGACY_LOCKFILE = """
            {
              "lockfile_version" : 2,
              "last_export" : "2025-01-01T00:00:00Z",
              "orgs" : {
                "https://x.atlassian.net" : {
                  "spaces" : {
                    "KEY" : {
                      "pages" : {
                        "100" : {
                          "title" : "Hello",
                          "version" : 7,
                          "export_path" : "Space/Hello.md",
                          "attachments" : {
                            "att-1" : { "version" : 1, "path" : "Space/attachments/file.png" }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    @Test
    void load_then_save_preserves_legacy_shape(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("confluence-lock.json");
        Files.writeString(file, LEGACY_LOCKFILE);

        ConfluenceLock lock = ConfluenceLock.load(file);
        // Save once to round-trip; the writer always re-stamps last_export, so we
        // only assert structure, not the timestamp.
        lock.save(file, null);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode parsed = mapper.readTree(Files.readString(file));

        assertThat(parsed.get("lockfile_version").asInt()).isEqualTo(2);
        assertThat(parsed.has("last_export")).isTrue();

        JsonNode org = parsed.path("orgs").path("https://x.atlassian.net");
        assertThat(org.isObject()).as("org keyed by base URL string").isTrue();

        JsonNode space = org.path("spaces").path("KEY");
        assertThat(space.isObject()).as("space keyed by space-key string").isTrue();

        JsonNode pageEntry = space.path("pages").path("100");
        assertThat(pageEntry.isObject()).as("page keyed by stringified page id").isTrue();
        assertThat(pageEntry.get("title").asText()).isEqualTo("Hello");
        assertThat(pageEntry.get("version").asInt()).isEqualTo(7);
        assertThat(pageEntry.get("export_path").asText()).isEqualTo("Space/Hello.md");

        JsonNode attachment = pageEntry.path("attachments").path("att-1");
        assertThat(attachment.get("version").asInt()).isEqualTo(1);
        assertThat(attachment.get("path").asText()).isEqualTo("Space/attachments/file.png");
    }
}
