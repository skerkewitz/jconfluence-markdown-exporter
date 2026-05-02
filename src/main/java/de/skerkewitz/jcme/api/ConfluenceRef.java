package de.skerkewitz.jcme.api;

import de.skerkewitz.jcme.model.PageId;
import de.skerkewitz.jcme.model.SpaceKey;

import java.util.Optional;

/**
 * A reference parsed out of a Confluence URL path. Mirrors the Python
 * {@code ConfluenceRef} pydantic model.
 */
public record ConfluenceRef(SpaceKey spaceKey, PageId pageId, String pageTitle) {

    public Optional<PageId> pageIdOpt() {
        return Optional.ofNullable(pageId);
    }

    public Optional<String> pageTitleOpt() {
        return Optional.ofNullable(pageTitle);
    }
}
