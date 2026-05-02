package de.skerkewitz.jcme.config;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Logging verbosity persisted in {@code export.log_level}.
 *
 * <p>Wire format remains an upper-case string ({@code "INFO"}, {@code "DEBUG"}, ...) for
 * backwards compatibility with existing config files; {@link #from(String)} accepts any
 * casing and treats {@code "WARN"} / {@code "WARNING"} as the same level.
 */
public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR;

    @JsonCreator
    public static LogLevel from(String name) {
        if (name == null || name.isEmpty()) return INFO;
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "TRACE" -> TRACE;
            case "DEBUG" -> DEBUG;
            case "WARN", "WARNING" -> WARN;
            case "ERROR" -> ERROR;
            default -> INFO;
        };
    }

    @JsonValue
    public String wireValue() {
        return name();
    }

    public Level toSlf4j() {
        return switch (this) {
            case TRACE -> Level.TRACE;
            case DEBUG -> Level.DEBUG;
            case INFO  -> Level.INFO;
            case WARN  -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
    }
}
