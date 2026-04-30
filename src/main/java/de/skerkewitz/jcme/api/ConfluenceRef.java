package de.skerkewitz.jcme.api;

import java.util.Optional;

/**
 * A reference parsed out of a Confluence URL path. Mirrors the Python
 * {@code ConfluenceRef} pydantic model.
 */
public record ConfluenceRef(String spaceKey, Long pageId, String pageTitle) {

    public Optional<Long> pageIdOpt() {
        return Optional.ofNullable(pageId);
    }

    public Optional<String> pageTitleOpt() {
        return Optional.ofNullable(pageTitle);
    }
}
