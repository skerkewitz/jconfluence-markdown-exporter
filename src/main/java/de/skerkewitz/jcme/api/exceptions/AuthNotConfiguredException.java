package de.skerkewitz.jcme.api.exceptions;

/**
 * Raised when no valid authentication is configured for an instance URL.
 *
 * <p>The Python tool extends {@code BaseException} to bypass broad {@code except Exception}
 * handlers in export loops; in Java it remains a {@link RuntimeException} but the export
 * pipeline is responsible for not swallowing it.
 */
public class AuthNotConfiguredException extends RuntimeException {

    private final String url;
    private final String service;

    public AuthNotConfiguredException(String url, String service) {
        super("No valid authentication configured for " + service + " at " + url);
        this.url = url;
        this.service = service;
    }

    public AuthNotConfiguredException(String url, String service, Throwable cause) {
        super("No valid authentication configured for " + service + " at " + url, cause);
        this.url = url;
        this.service = service;
    }

    public String url() {
        return url;
    }

    public String service() {
        return service;
    }
}
