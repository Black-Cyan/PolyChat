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

    private static volatile boolean initialized = false;
    private static volatile Logger logger;

    private LoggingUtil() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        Path dir = Paths.get(System.getProperty("user.home"), LOG_DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create log directory " + dir, e);
        }
        System.setProperty(LOG_DIR_PROPERTY, dir.toString());

        logger = LoggerFactory.getLogger(LoggingUtil.class);
        logBanner();
        logger.info("Logging initialized. Log directory: {}", dir.toAbsolutePath());
        initialized = true;
    }

    private static void logBanner() {
        if (logger == null) {
            return;
        }
        String banner = """
                  ____       _        ____ _           _   
                 |  _ \\ ___ | | ___  / ___| |__   __ _| |_ 
                 | |_) / _ \\| |/ _ \\| |   | '_ \\ / _` | __|
                 |  __/ (_) | |  __/| |___| | | | (_| | |_ 
                 |_|   \\___/|_|\\___| \\____|_| |_|\\__,_|\\__|
                """;
        logger.info("\n{}", banner);
    }
}
