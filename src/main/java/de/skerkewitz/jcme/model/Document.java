package de.skerkewitz.jcme.model;

import java.util.List;

/**
 * Common interface for any document-like Confluence entity (page, descendant, ancestor,
 * attachment). Mirrors the Python {@code Document} pydantic base class.
 */
public interface Document {
    String baseUrl();
    String title();
    Space space();
    List<Ancestor> ancestors();
    Version version();
}
