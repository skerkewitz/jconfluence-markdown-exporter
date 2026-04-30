package de.skerkewitz.jcme.markdown;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Render the YAML front matter block for an exported page. */
public final class FrontMatterRenderer {

    private static final ObjectMapper YAML = new ObjectMapper(
            new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    private FrontMatterRenderer() {}

    /**
     * Build the front matter block from page-properties + tags. Returns an empty string when
     * neither produces any content.
     */
    public static String render(Map<String, Object> pageProperties, List<String> tags) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (pageProperties != null) data.putAll(pageProperties);
        if (tags != null && !tags.isEmpty()) data.put("tags", tags);
        if (data.isEmpty()) return "";
        try {
            String yaml = YAML.writeValueAsString(data).strip();
            return "---\n" + yaml + "\n---\n";
        } catch (Exception e) {
            return "";
        }
    }
}
