package de.skerkewitz.jcme;

import ch.qos.logback.classic.Logger;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.config.LogLevel;
import org.slf4j.LoggerFactory;

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
            apply(LogLevel.INFO);
        }
    }

    /** Apply the given log level. */
    public static void apply(LogLevel level) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(level.toSlf4j());
    }
}
