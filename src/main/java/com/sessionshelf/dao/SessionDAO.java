package com.sessionshelf.dao;

import com.sessionshelf.model.Session;
import com.sessionshelf.model.enums.SourceType;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 会话数据访问对象
 * 提供 session_base 表的 CRUD 操作
 */
public class SessionDAO {

    private final Connection connection;

    public SessionDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /**
     * 插入或更新会话
     */
    public void save(Session session) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO session_base
            (session_id, source_type, original_unique_id, title, model_name, create_time, content_preview, source_file_path, working_directory)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, session.getSessionId());
            pstmt.setInt(2, session.getSourceType().getCode());
            pstmt.setString(3, session.getOriginalUniqueId());
            pstmt.setString(4, session.getTitle());
            pstmt.setString(5, session.getModelName());
            pstmt.setTimestamp(6, Timestamp.valueOf(session.getCreateTime()));
            pstmt.setString(7, session.getContentPreview());
            pstmt.setString(8, session.getSourceFilePath());
            pstmt.setString(9, session.getWorkingDirectory());
            pstmt.executeUpdate();
        }
    }

    /**
     * 批量保存会话
     */
    public void saveAll(List<Session> sessions) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO session_base
            (session_id, source_type, original_unique_id, title, model_name, create_time, content_preview, source_file_path, working_directory)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (Session session : sessions) {
                pstmt.setString(1, session.getSessionId());
                pstmt.setInt(2, session.getSourceType().getCode());
                pstmt.setString(3, session.getOriginalUniqueId());
                pstmt.setString(4, session.getTitle());
                pstmt.setString(5, session.getModelName());
                pstmt.setTimestamp(6, Timestamp.valueOf(session.getCreateTime()));
                pstmt.setString(7, session.getContentPreview());
                pstmt.setString(8, session.getSourceFilePath());
                pstmt.setString(9, session.getWorkingDirectory());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            connection.commit();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * 根据 ID 查询会话
     */
    public Optional<Session> findById(String sessionId) throws SQLException {
        String sql = "SELECT * FROM session_base WHERE session_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        }
    }

    /**
     * 查询所有会话
     */
    public List<Session> findAll() throws SQLException {
        String sql = "SELECT * FROM session_base ORDER BY create_time DESC";
        List<Session> sessions = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                sessions.add(mapResultSet(rs));
            }
        }
        return sessions;
    }

    /**
     * 根据数据源类型查询会话
     */
    public List<Session> findBySourceType(SourceType sourceType) throws SQLException {
        String sql = "SELECT * FROM session_base WHERE source_type = ? ORDER BY create_time DESC";
        List<Session> sessions = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, sourceType.getCode());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sessions.add(mapResultSet(rs));
            }
        }
        return sessions;
    }

    /**
     * 根据文件夹 ID 查询会话
     */
    public List<Session> findByFolderId(String folderId) throws SQLException {
        String sql = """
            SELECT s.* FROM session_base s
            INNER JOIN session_folder_rel r ON s.session_id = r.session_id
            WHERE r.folder_id = ?
            ORDER BY s.create_time DESC
        """;
        List<Session> sessions = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sessions.add(mapResultSet(rs));
            }
        }
        return sessions;
    }

    /**
     * 根据标签 ID 查询会话
     */
    public List<Session> findByTagId(String tagId) throws SQLException {
        String sql = """
            SELECT s.* FROM session_base s
            INNER JOIN session_tag_rel r ON s.session_id = r.session_id
            WHERE r.tag_id = ?
            ORDER BY s.create_time DESC
        """;
        List<Session> sessions = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tagId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sessions.add(mapResultSet(rs));
            }
        }
        return sessions;
    }

    /**
     * 全文搜索会话（标题和内容）
     */
    public List<Session> search(String keyword) throws SQLException {
        String sql = """
            SELECT * FROM session_base
            WHERE title LIKE ? OR content_preview LIKE ?
            ORDER BY create_time DESC
        """;
        List<Session> sessions = new ArrayList<>();
        String searchPattern = "%" + keyword + "%";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sessions.add(mapResultSet(rs));
            }
        }
        return sessions;
    }

    /**
     * 删除会话
     */
    public void delete(String sessionId) throws SQLException {
        String sql = "DELETE FROM session_base WHERE session_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 将 ResultSet 映射为 Session 对象
     */
    private Session mapResultSet(ResultSet rs) throws SQLException {
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
        return session;
    }
}
