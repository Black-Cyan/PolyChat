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

    static {
        Path dir = Paths.get(System.getProperty("user.home"), LOG_DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("Failed to create log directory " + dir + ": " + e.getMessage());
        }
        System.setProperty(LOG_DIR_PROPERTY, dir.toString());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingUtil.class);
    private static volatile boolean initialized = false;

    private LoggingUtil() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        logBanner();
        LOGGER.info("Logging initialized. Log directory: {}", System.getProperty(LOG_DIR_PROPERTY));
        initialized = true;
    }

    private static void logBanner() {
        String banner = """
                  ____       _        ____ _           _   
                 |  _ \\ ___ | | ___  / ___| |__   __ _| |_ 
                 | |_) / _ \\| |/ _ \\| |   | '_ \\ / _` | __|
                 |  __/ (_) | |  __/| |___| | | | (_| | |_ 
                 |_|   \\___/|_|\\___| \\____|_| |_|\\__,_|\\__|
                """;
        LOGGER.info("\n{}", banner);
    }
}
