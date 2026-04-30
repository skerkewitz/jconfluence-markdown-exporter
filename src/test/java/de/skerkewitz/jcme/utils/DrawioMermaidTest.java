package de.skerkewitz.jcme.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DrawioMermaidTest {

    @Test
    void extracts_plain_mermaid_data() {
        String xml = """
                <mxfile>
                  <UserObject mermaidData="graph TD; A--&gt;B" id="x"/>
                </mxfile>
                """;
        Optional<String> mermaid = DrawioMermaid.extractMermaidFromXml(xml);
        assertThat(mermaid).hasValue("```mermaid\ngraph TD; A-->B\n```");
    }

    @Test
    void extracts_mermaid_data_from_json_wrapper() {
        // mermaidData is JSON-encoded (entities-escaped)
        String json = "{\"data\":\"graph TD; A-->B\"}";
        String escaped = json.replace("\"", "&quot;");
        String xml = """
                <mxfile><UserObject mermaidData="%s"/></mxfile>
                """.formatted(escaped);
        Optional<String> mermaid = DrawioMermaid.extractMermaidFromXml(xml);
        assertThat(mermaid).hasValue("```mermaid\ngraph TD; A-->B\n```");
    }

    @Test
    void returns_empty_when_no_user_object() {
        assertThat(DrawioMermaid.extractMermaidFromXml("<mxfile/>")).isEmpty();
    }

    @Test
    void returns_empty_for_empty_input() {
        assertThat(DrawioMermaid.extractMermaidFromXml("")).isEmpty();
        assertThat(DrawioMermaid.extractMermaidFromXml(null)).isEmpty();
    }

    @Test
    void extracts_from_file_on_disk(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("diagram.drawio");
        Files.writeString(file, "<mxfile><UserObject mermaidData=\"flowchart\"/></mxfile>");

        Optional<String> result = DrawioMermaid.extractMermaid(file);

        assertThat(result).hasValue("```mermaid\nflowchart\n```");
    }

    @Test
    void returns_empty_for_missing_file(@TempDir Path tmp) {
        assertThat(DrawioMermaid.extractMermaid(tmp.resolve("missing.drawio"))).isEmpty();
        assertThat(DrawioMermaid.extractMermaid(null)).isEmpty();
    }
}
