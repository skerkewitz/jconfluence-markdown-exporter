package de.skerkewitz.jcme.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Extract Mermaid diagrams embedded inside a {@code .drawio} XML file.
 *
 * <p>Drawio stores mermaid sources on a {@code UserObject} element via the
 * {@code mermaidData} attribute (HTML-escaped, sometimes wrapped in a JSON object
 * with a {@code data} key). This helper unwraps the variants and returns a
 * markdown {@code ```mermaid} fenced block.
 */
public final class DrawioMermaid {

    private static final Logger LOG = LoggerFactory.getLogger(DrawioMermaid.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private DrawioMermaid() {}

    public static Optional<String> extractMermaid(Path file) {
        if (file == null || !Files.exists(file)) return Optional.empty();
        try {
            String xml = Files.readString(file, StandardCharsets.UTF_8);
            return extractMermaidFromXml(xml);
        } catch (IOException e) {
            LOG.debug("Could not read drawio file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<String> extractMermaidFromXml(String xml) {
        if (xml == null || xml.isEmpty()) return Optional.empty();
        try {
            var doc = Jsoup.parse(xml, "", Parser.xmlParser());
            Element user = doc.selectFirst("UserObject");
            if (user == null) return Optional.empty();
            String raw = user.attr("mermaidData");
            if (raw.isEmpty()) return Optional.empty();
            String unescaped = org.jsoup.parser.Parser.unescapeEntities(raw, false);
            String diagram = unwrapJson(unescaped);
            return Optional.of("```mermaid\n" + diagram + "\n```");
        } catch (Exception e) {
            LOG.debug("Error extracting mermaid data from drawio XML: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String unwrapJson(String value) {
        try {
            JsonNode parsed = JSON.readTree(value);
            if (parsed.isObject() && parsed.has("data")) {
                return parsed.get("data").asText(value);
            }
        } catch (IOException ignored) {
            // not JSON — return value as-is
        }
        return value;
    }
}
