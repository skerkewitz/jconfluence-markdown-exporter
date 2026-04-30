package de.skerkewitz.jcme.api.exceptions;

/** Generic API failure (HTTP non-2xx after retries, network error, parse error). */
public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String url;

    public ApiException(String message, int statusCode, String url) {
        super(message);
        this.statusCode = statusCode;
        this.url = url;
    }

    public ApiException(String message, int statusCode, String url, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.url = url;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.url = null;
    }

    public int statusCode() {
        return statusCode;
    }

    public String url() {
        return url;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }
}
