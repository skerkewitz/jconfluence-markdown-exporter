package de.skerkewitz.jcme.api.exceptions;

/** Raised when a Jira API response indicates an authentication failure (Seraph header). */
public class JiraAuthenticationException extends RuntimeException {

    public JiraAuthenticationException(String message) {
        super(message);
    }
}
