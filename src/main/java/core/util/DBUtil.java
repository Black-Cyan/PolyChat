package core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBUtil {
    private static final String DB_FILENAME = "polychat.db";
    private static final Path DB_PATH = initDbPath();

    private static Path initDbPath() {
        Path dir = Paths.get(System.getProperty("user.home"), ".polychat");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create database directory: " + dir, e);
        }
        return dir.resolve(DB_FILENAME);
    }

    public static Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + DB_PATH.toAbsolutePath();
        Connection conn = DriverManager.getConnection(url);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }
}
