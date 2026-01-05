package core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LoggingUtil {
    private static final String LOG_DIR_NAME = ".polychat";
    private static final String LOG_DIR_PROPERTY = "polychat.log.dir";

    private static boolean initialized = false;
    private static Logger logger;

    private LoggingUtil() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        // Ensure JNDI lookups are disabled so logback does not require the java.naming module
        System.setProperty("logback.disable.jndi", "true");
        System.setProperty("logback.disableJNDI", "true");
        Path dir = Paths.get(System.getProperty("user.home"), LOG_DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create log directory " + dir + ". Ensure your home directory is writable.", e);
        }
        System.setProperty(LOG_DIR_PROPERTY, dir.toString());

        logger = LoggerFactory.getLogger(LoggingUtil.class);
        logBanner();
        logger.info("Logging initialized. Log directory: {}", dir.toAbsolutePath());
        initialized = true;
    }

    private static void logBanner() {
        String banner = """
____       _        ____ _           _   
|  _ \\ ___ | |_   _ / ___| |__   __ _| |_ 
| |_) / _ \\| | | | | |   | '_ \\ / _` | __|
|  __/ (_) | | |_| | |___| | | | (_| | |_ 
|_|   \\___/|_|\\__, |\\____|_| |_|\\__,_|\\__|
              |___/
                """;
        logger.info("\n{}", banner);
    }
}
