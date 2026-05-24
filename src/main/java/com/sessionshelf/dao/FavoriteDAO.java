package com.sessionshelf.dao;

import com.sessionshelf.model.Session;
import com.sessionshelf.model.enums.SourceType;

import java.sql.*;
import java.util.*;

/**
 * 收藏会话数据访问对象
 * 操作 session_favorite 表
 */
public class FavoriteDAO {

    private final Connection connection;

    public FavoriteDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public void addFavorite(String sessionId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO session_favorite (session_id) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        }
    }

    public void removeFavorite(String sessionId) throws SQLException {
        String sql = "DELETE FROM session_favorite WHERE session_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        }
    }

    public boolean isFavorite(String sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM session_favorite WHERE session_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public Set<String> getAllFavoriteIds() throws SQLException {
        String sql = "SELECT session_id FROM session_favorite";
        Set<String> ids = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getString("session_id"));
            }
        }
        return ids;
    }

    public List<Session> getFavoriteSessions() throws SQLException {
        String sql = """
            SELECT s.* FROM session_base s
            INNER JOIN session_favorite f ON s.session_id = f.session_id
            ORDER BY s.create_time DESC
            """;
        List<Session> sessions = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Session session = new Session();
                session.setSessionId(rs.getString("session_id"));
                session.setSourceType(SourceType.fromCode(rs.getInt("source_type")));
                session.setOriginalUniqueId(rs.getString("original_unique_id"));
                session.setTitle(rs.getString("title"));
                session.setModelName(rs.getString("model_name"));
                Timestamp timestamp = rs.getTimestamp("create_time");
                if (timestamp != null) {
                    session.setCreateTime(timestamp.toLocalDateTime());
                }
                session.setContentPreview(rs.getString("content_preview"));
                session.setSourceFilePath(rs.getString("source_file_path"));
                session.setWorkingDirectory(rs.getString("working_directory"));
                sessions.add(session);
            }
        }
        return sessions;
    }
}
