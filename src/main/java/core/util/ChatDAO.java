package core.util;

import core.entity.ChatMessage;
import core.entity.ChatSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatDAO {
    private final Connection conn;
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatDAO.class);

    public ChatDAO(Connection conn) {
        this.conn = conn;
        createTables();
    }

    private void createTables() {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ChatSession (
                    session_uuid TEXT PRIMARY KEY,
                    model_uuid TEXT NOT NULL,
                    title TEXT,
                    created_at INTEGER NOT NULL,
                    deleted INTEGER DEFAULT 0,
                    FOREIGN KEY(model_uuid) REFERENCES Model(uuid) ON DELETE CASCADE
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ChatMessage (
                    message_uuid TEXT PRIMARY KEY,
                    session_uuid TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    deleted INTEGER DEFAULT 0,
                    FOREIGN KEY(session_uuid) REFERENCES ChatSession(session_uuid) ON DELETE CASCADE
                )
            """);
        } catch (SQLException e) {
            LOGGER.error("Failed to create chat tables", e);
        }
    }

    public ChatSession createSession(String modelUuid, String title) {
        String uuid = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ChatSession(session_uuid, model_uuid, title, created_at) VALUES(?,?,?,?)")) {
            ps.setString(1, uuid);
            ps.setString(2, modelUuid);
            ps.setString(3, title);
            ps.setLong(4, now);
            ps.executeUpdate();
            return new ChatSession(uuid, modelUuid, title, now);
        } catch (SQLException e) {
            LOGGER.error("Failed to create chat session for model {}", modelUuid, e);
            return null;
        }
    }

    public List<ChatSession> listSessions(String modelUuid) {
        List<ChatSession> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT session_uuid, model_uuid, title, created_at FROM ChatSession WHERE model_uuid = ? AND deleted = 0 ORDER BY created_at DESC")) {
            ps.setString(1, modelUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ChatSession(
                            rs.getString("session_uuid"),
                            rs.getString("model_uuid"),
                            rs.getString("title"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to list chat sessions for model {}", modelUuid, e);
        }
        return list;
    }

    public List<ChatMessage> listMessages(String sessionUuid) {
        List<ChatMessage> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT message_uuid, role, content, timestamp FROM ChatMessage WHERE session_uuid = ? AND deleted = 0 ORDER BY timestamp")) {
            ps.setString(1, sessionUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ChatMessage(
                            rs.getString("message_uuid"),
                            rs.getString("role"),
                            rs.getString("content"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to list messages for session {}", sessionUuid, e);
        }
        return list;
    }

    public void addMessage(String sessionUuid, String role, String content, long timestamp) {
        String uuid = UUID.randomUUID().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ChatMessage(message_uuid, session_uuid, role, content, timestamp) VALUES(?,?,?,?,?)")) {
            ps.setString(1, uuid);
            ps.setString(2, sessionUuid);
            ps.setString(3, role);
            ps.setString(4, content);
            ps.setLong(5, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to add message to session {}", sessionUuid, e);
        }
    }

    public void deleteSession(String sessionUuid) {
        // Logical deletion - set deleted flag to 1
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ChatSession SET deleted = 1 WHERE session_uuid = ?")) {
            ps.setString(1, sessionUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete session {}", sessionUuid, e);
        }
        // Also logically delete all messages in this session
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ChatMessage SET deleted = 1 WHERE session_uuid = ?")) {
            ps.setString(1, sessionUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete messages for session {}", sessionUuid, e);
        }
    }

    public void updateSessionTitle(String sessionUuid, String title) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ChatSession SET title = ? WHERE session_uuid = ?")) {
            ps.setString(1, title);
            ps.setString(2, sessionUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to update title for session {}", sessionUuid, e);
        }
    }
}
