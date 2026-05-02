package de.skerkewitz.jcme.model;

import de.skerkewitz.jcme.api.BaseUrl;

import java.util.List;

/**
 * Common interface for any document-like Confluence entity (page, descendant, ancestor,
 * attachment). Mirrors the Python {@code Document} pydantic base class.
 */
public interface Document {
    BaseUrl baseUrl();
    String title();
    Space space();
    List<Ancestor> ancestors();
    Version version();
}
