package core.util;

import core.entity.Model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModelDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelDAO.class);
    private final Connection conn;

    public ModelDAO(Connection conn) {
        this.conn = conn;
        createTable();
    }

    private void createTable() {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Model (
                    uuid TEXT PRIMARY KEY,
                    base_url TEXT NOT NULL,
                    api_key TEXT,
                    model_name TEXT NOT NULL,
                    nickname TEXT,
                    deleted INTEGER DEFAULT 0
                )
            """);
        } catch (SQLException e) {
            LOGGER.error("Failed to create Model table", e);
        }
    }

    // 添加模型
    public void addModel(String baseUrl, String apiKey, String modelName, String nickname) {
        String uuid = UUID.randomUUID().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO Model(uuid, base_url, api_key, model_name, nickname) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid);
            ps.setString(2, baseUrl);
            ps.setString(3, apiKey);
            ps.setString(4, modelName);
            ps.setString(5, nickname);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to add model {}", modelName, e);
        }
    }

    // 查询所有模型（不返回 api_key）
    public List<Model> getAllModels() {
        List<Model> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, base_url, model_name, nickname FROM Model WHERE deleted = 0");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Model(
                        rs.getString("uuid"),
                        rs.getString("base_url"),
                        null,
                        rs.getString("model_name"),
                        rs.getString("nickname")
                ));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query models", e);
        }
        return list;
    }

    // 更新模型基本信息（不更新 api_key）
    public void updateModelInfo(String uuid, String baseUrl, String modelName, String nickname) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE Model SET base_url = ?, model_name = ?, nickname = ? WHERE uuid = ?")) {
            ps.setString(1, baseUrl);
            ps.setString(2, modelName);
            ps.setString(3, nickname);
            ps.setString(4, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to update model info for {}", uuid, e);
        }
    }

    // 单独更新 api_key
    public void updateApiKey(String uuid, String apiKey) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE Model SET api_key = ? WHERE uuid = ?")) {
            ps.setString(1, apiKey);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to update apiKey for {}", uuid, e);
        }
    }

    // 删除模型 (逻辑删除)
    public void deleteModel(String uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE Model SET deleted = 1 WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete model {}", uuid, e);
        }
    }

    // 通过 uuid 获取模型
    public Model getModel(String uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, base_url, api_key, model_name, nickname FROM Model WHERE uuid = ? AND deleted = 0")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Model(
                            rs.getString("uuid"),
                            rs.getString("base_url"),
                            rs.getString("api_key"),
                            rs.getString("model_name"),
                            rs.getString("nickname")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch model {}", uuid, e);
        }
        return null;
    }
}
