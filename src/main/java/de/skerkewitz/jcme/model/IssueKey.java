package de.skerkewitz.jcme.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Jira issue key (e.g. {@code "PROJ-123"}).
 *
 * <p>Issue keys are pulled out of HTML attributes ({@code data-jira-key}) and arbitrary
 * link hrefs in Confluence pages, so the value can be missing or malformed; callers
 * should prefer {@link #tryParse(String)} when the source is untrusted.
 */
public record IssueKey(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");

    public IssueKey {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Jira issue key: " + value);
        }
    }

    @JsonCreator
    public static IssueKey of(String value) {
        return new IssueKey(value);
    }

    public static Optional<IssueKey> tryParse(String value) {
        try {
            return Optional.of(new IssueKey(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
