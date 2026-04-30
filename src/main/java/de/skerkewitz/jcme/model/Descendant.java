package de.skerkewitz.jcme.model;

import java.util.List;

public record Descendant(
        String baseUrl,
        long id,
        String title,
        Space space,
        List<Ancestor> ancestors,
        Version version
) implements ExportablePage {

    public Descendant {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }
}
