package core.util;

import core.entity.ChatMessage;
import core.entity.ChatSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatDAO {
    private final Connection conn;

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
                    FOREIGN KEY(session_uuid) REFERENCES ChatSession(session_uuid) ON DELETE CASCADE
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
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
            e.printStackTrace();
            return null;
        }
    }

    public List<ChatSession> listSessions(String modelUuid) {
        List<ChatSession> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT session_uuid, model_uuid, title, created_at FROM ChatSession WHERE model_uuid = ? ORDER BY created_at DESC")) {
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
            e.printStackTrace();
        }
        return list;
    }

    public List<ChatMessage> listMessages(String sessionUuid) {
        List<ChatMessage> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT message_uuid, role, content, timestamp FROM ChatMessage WHERE session_uuid = ? ORDER BY timestamp")) {
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    public void deleteSession(String sessionUuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM ChatSession WHERE session_uuid = ?")) {
            ps.setString(1, sessionUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateSessionTitle(String sessionUuid, String title) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ChatSession SET title = ? WHERE session_uuid = ?")) {
            ps.setString(1, title);
            ps.setString(2, sessionUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
