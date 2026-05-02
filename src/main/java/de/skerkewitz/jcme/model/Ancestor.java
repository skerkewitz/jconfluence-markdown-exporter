package de.skerkewitz.jcme.model;

import de.skerkewitz.jcme.api.BaseUrl;

import java.util.List;

public record Ancestor(
        BaseUrl baseUrl,
        PageId id,
        String title,
        Space space,
        List<Ancestor> ancestors,
        Version version
) implements Document {

    public Ancestor {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }
}
