package de.skerkewitz.jcme;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Programmatically apply the {@code export.log_level} from the effective configuration
 * to the Logback root logger.
 *
 * <p>Logback is loaded statically from {@code logback.xml} (default level: INFO). This
 * helper bumps the level at runtime so users can flip to {@code DEBUG} either via
 * {@code jcme config set export.log_level=DEBUG} or {@code JCME_EXPORT__LOG_LEVEL=DEBUG}.
 */
public final class Logging {

    private Logging() {}

    /** Apply the level from the on-disk + env-var-overlaid config. */
    public static void initFromConfig() {
        try {
            AppConfig settings = new ConfigStore().loadEffective();
            apply(settings.export().logLevel());
        } catch (Exception e) {
            // Don't let logging-config failures break the CLI.
            apply("INFO");
        }
    }

    /** Apply the given log level (INFO, DEBUG, WARNING/WARN, ERROR). */
    public static void apply(String levelName) {
        Level level = parse(levelName);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }

    private static Level parse(String name) {
        if (name == null) return Level.INFO;
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "DEBUG" -> Level.DEBUG;
            case "TRACE" -> Level.TRACE;
            case "WARN", "WARNING" -> Level.WARN;
            case "ERROR" -> Level.ERROR;
            default -> Level.INFO;
        };
    }
}
