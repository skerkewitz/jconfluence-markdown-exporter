package de.skerkewitz.jcme.model;

import de.skerkewitz.jcme.api.BaseUrl;

import java.util.List;

public record Descendant(
        BaseUrl baseUrl,
        PageId id,
        String title,
        Space space,
        List<Ancestor> ancestors,
        Version version
) implements ExportablePage {

    public Descendant {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }
}
