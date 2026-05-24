package com.sessionshelf.dao;

import com.sessionshelf.model.Tag;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 标签数据访问对象
 * 提供 tag 表的 CRUD 操作
 */
public class TagDAO {

    private final Connection connection;

    public TagDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /**
     * 插入标签
     */
    public void save(Tag tag) throws SQLException {
        String sql = "INSERT OR REPLACE INTO tag (tag_id, tag_name) VALUES (?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tag.getTagId());
            pstmt.setString(2, tag.getTagName());
            pstmt.executeUpdate();
        }
    }

    /**
     * 查询所有标签
     */
    public List<Tag> findAll() throws SQLException {
        String sql = "SELECT * FROM tag ORDER BY tag_name";
        List<Tag> tags = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tags.add(mapResultSet(rs));
            }
        }
        return tags;
    }

    /**
     * 根据 ID 查询标签
     */
    public Optional<Tag> findById(String tagId) throws SQLException {
        String sql = "SELECT * FROM tag WHERE tag_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tagId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        }
    }

    /**
     * 根据会话 ID 查询关联的标签
     */
    public List<Tag> findBySessionId(String sessionId) throws SQLException {
        String sql = "SELECT t.* FROM tag t " +
                "INNER JOIN session_tag_rel r ON t.tag_id = r.tag_id " +
                "WHERE r.session_id = ? " +
                "ORDER BY t.tag_name";
        List<Tag> tags = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                tags.add(mapResultSet(rs));
            }
        }
        return tags;
    }

    /**
     * 重命名标签
     */
    public void rename(String tagId, String newName) throws SQLException {
        String sql = "UPDATE tag SET tag_name = ? WHERE tag_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, tagId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 删除标签
     */
    public void delete(String tagId) throws SQLException {
        String sql = "DELETE FROM tag WHERE tag_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tagId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 为会话添加标签关联
     */
    public void addTagToSession(String sessionId, String tagId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO session_tag_rel (session_id, tag_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setString(2, tagId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 移除会话的标签关联
     */
    public void removeTagFromSession(String sessionId, String tagId) throws SQLException {
        String sql = "DELETE FROM session_tag_rel WHERE session_id = ? AND tag_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setString(2, tagId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 清除会话的所有标签关联
     */
    public void clearSessionTags(String sessionId) throws SQLException {
        String sql = "DELETE FROM session_tag_rel WHERE session_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 将 ResultSet 映射为 Tag 对象
     */
    private Tag mapResultSet(ResultSet rs) throws SQLException {
        Tag tag = new Tag();
        tag.setTagId(rs.getString("tag_id"));
        tag.setTagName(rs.getString("tag_name"));
        return tag;
    }
}
