package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageIdTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void value_factory_holds_long() {
        PageId id = PageId.of(12345L);
        assertThat(id.value()).isEqualTo(12345L);
    }

    @Test
    void parse_string_to_pageid() {
        assertThat(PageId.parse("42")).isEqualTo(PageId.of(42L));
    }

    @Test
    void parse_rejects_zero_and_negative() {
        assertThatThrownBy(() -> PageId.parse("0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageId.parse("-7"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageId(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_empty_and_null() {
        assertThatThrownBy(() -> PageId.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageId.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void to_string_is_decimal_long() {
        assertThat(PageId.of(42L).toString()).isEqualTo("42");
    }

    @Test
    void equality_based_on_value() {
        assertThat(PageId.of(7L)).isEqualTo(PageId.of(7L));
        assertThat(PageId.of(7L)).isNotEqualTo(PageId.of(8L));
        assertThat(PageId.of(7L).hashCode()).isEqualTo(PageId.of(7L).hashCode());
    }

    @Test
    void serializes_via_json_value_to_raw_long() throws Exception {
        String json = JSON.writeValueAsString(PageId.of(42L));
        assertThat(json).isEqualTo("42");
    }

    @Test
    void deserializes_via_json_creator_from_raw_long() throws Exception {
        PageId id = JSON.readValue("42", PageId.class);
        assertThat(id).isEqualTo(PageId.of(42L));
    }

    @Test
    void wrapped_in_dto_round_trips_unchanged() throws Exception {
        // Confirms wire-format is identical for nested fields like Page.id, Ancestor.id.
        record TinyDto(PageId id, String title) {}
        String json = JSON.writeValueAsString(new TinyDto(PageId.of(123L), "T"));
        assertThat(json).isEqualTo("{\"id\":123,\"title\":\"T\"}");
        TinyDto parsed = JSON.readValue(json, TinyDto.class);
        assertThat(parsed.id()).isEqualTo(PageId.of(123L));
    }
}
