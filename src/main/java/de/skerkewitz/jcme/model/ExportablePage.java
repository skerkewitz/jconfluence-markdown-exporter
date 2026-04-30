package de.skerkewitz.jcme.model;

/**
 * Marker interface for any document that can be promoted to a {@link Page} for export
 * (i.e. {@link Page} itself or {@link Descendant}). Holds the minimum identifiers the
 * export pipeline needs to dedup, schedule, and skip-unchanged.
 */
public sealed interface ExportablePage extends Document permits Page, Descendant {

    long id();
}
