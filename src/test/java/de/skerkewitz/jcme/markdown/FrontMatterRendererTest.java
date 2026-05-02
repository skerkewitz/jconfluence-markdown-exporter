package de.skerkewitz.jcme.markdown;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrontMatterRendererTest {

    @Test
    void empty_inputs_return_empty_block() {
        assertThat(FrontMatterRenderer.render(null, null)).isEmpty();
        assertThat(FrontMatterRenderer.render(Map.of(), List.of())).isEmpty();
    }

    @Test
    void renders_only_tags_when_no_properties() {
        String out = FrontMatterRenderer.render(null, List.of("#foo", "#bar"));
        assertThat(out).startsWith("---\n").endsWith("---\n");
        assertThat(out).contains("tags:");
        // Jackson's MINIMIZE_QUOTES uses double-quotes for tokens starting with '#'
        // (which is a YAML comment marker).
        assertThat(out).contains("\"#foo\"").contains("\"#bar\"");
    }

    @Test
    void renders_only_properties_when_no_tags() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("status", "draft");
        props.put("priority", "high");

        String out = FrontMatterRenderer.render(props, null);

        assertThat(out).startsWith("---\n").endsWith("---\n");
        assertThat(out).contains("status: draft");
        assertThat(out).contains("priority: high");
        assertThat(out).doesNotContain("tags:");
    }

    @Test
    void merges_properties_and_tags() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("owner", "alice");

        String out = FrontMatterRenderer.render(props, List.of("#release"));

        assertThat(out).contains("owner: alice");
        assertThat(out).contains("tags:");
        assertThat(out).contains("\"#release\"");
    }

    @Test
    void preserves_property_insertion_order() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("zeta", "1");
        props.put("alpha", "2");
        props.put("middle", "3");

        String out = FrontMatterRenderer.render(props, List.of());

        int zPos = out.indexOf("zeta:");
        int aPos = out.indexOf("alpha:");
        int mPos = out.indexOf("middle:");
        assertThat(zPos).isLessThan(aPos);
        assertThat(aPos).isLessThan(mPos);
    }
}
