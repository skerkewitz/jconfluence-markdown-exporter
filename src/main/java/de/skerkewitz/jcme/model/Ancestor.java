package de.skerkewitz.jcme.model;

import java.util.List;

public record Ancestor(
        String baseUrl,
        long id,
        String title,
        Space space,
        List<Ancestor> ancestors,
        Version version
) implements Document {

    public Ancestor {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }
}
