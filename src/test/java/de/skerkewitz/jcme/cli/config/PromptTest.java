package de.skerkewitz.jcme.cli.config;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTest {

    private static Prompt promptWithInput(String input, ByteArrayOutputStream out) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8));
        return new Prompt(null, reader, new PrintStream(out));
    }

    @Test
    void text_returns_input_when_provided() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("hello\n", out);

        String result = p.text("Question", "default");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void text_returns_default_on_empty_input() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("\n", out);

        assertThat(p.text("Q", "fallback")).isEqualTo("fallback");
        assertThat(out.toString()).contains("[fallback]");
    }

    @Test
    void confirm_yes_returns_true() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("y\n", out);
        assertThat(p.confirm("Are you sure?", false)).isTrue();
    }

    @Test
    void confirm_no_returns_false() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("n\n", out);
        assertThat(p.confirm("Sure?", true)).isFalse();
    }

    @Test
    void confirm_empty_uses_default() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("\n\n", out);
        assertThat(p.confirm("a", true)).isTrue();
        assertThat(p.confirm("b", false)).isFalse();
    }

    @Test
    void select_returns_zero_based_index() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("2\n", out);

        int idx = p.select("Pick one", List.of("alpha", "beta", "gamma"));

        assertThat(idx).isEqualTo(1);
    }

    @Test
    void select_reprompts_on_invalid_input() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("bogus\n4\n2\n", out);

        int idx = p.select("Pick", List.of("a", "b", "c"));

        assertThat(idx).isEqualTo(1);
        assertThat(out.toString()).contains("Invalid choice");
    }

    @Test
    void select_returns_minus_one_on_empty_input() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("\n", out);
        assertThat(p.select("x", List.of("a", "b"))).isEqualTo(-1);
    }

    @Test
    void secret_falls_back_to_plain_when_no_console() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Prompt p = promptWithInput("supersecret\n", out);

        String got = p.secret("Token");

        assertThat(got).isEqualTo("supersecret");
        assertThat(out.toString()).contains("(input not masked)");
    }
}
