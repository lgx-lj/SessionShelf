package com.sessionshelf.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话-文件夹关联数据访问对象
 * 提供 session_folder_rel 表的操作
 */
public class SessionFolderRelDAO {

    private final Connection connection;

    public SessionFolderRelDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /**
     * 将会话添加到文件夹
     */
    public void addToFolder(String sessionId, String folderId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO session_folder_rel (session_id, folder_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setString(2, folderId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 将会话从文件夹移除
     */
    public void removeFromFolder(String sessionId, String folderId) throws SQLException {
        String sql = "DELETE FROM session_folder_rel WHERE session_id = ? AND folder_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setString(2, folderId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 获取会话所属的所有文件夹 ID
     */
    public List<String> getFolderIdsBySession(String sessionId) throws SQLException {
        String sql = "SELECT folder_id FROM session_folder_rel WHERE session_id = ?";
        List<String> folderIds = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                folderIds.add(rs.getString("folder_id"));
            }
        }
        return folderIds;
    }

    /**
     * 获取文件夹下的所有会话 ID
     */
    public List<String> getSessionIdsByFolder(String folderId) throws SQLException {
        String sql = "SELECT session_id FROM session_folder_rel WHERE folder_id = ?";
        List<String> sessionIds = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                sessionIds.add(rs.getString("session_id"));
            }
        }
        return sessionIds;
    }

    /**
     * 清除会话的所有文件夹关联
     */
    public void clearSessionFolders(String sessionId) throws SQLException {
        String sql = "DELETE FROM session_folder_rel WHERE session_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 移动会话到新文件夹（先清除旧关联，再添加新关联）
     */
    public void moveToFolder(String sessionId, String newFolderId) throws SQLException {
        try {
            connection.setAutoCommit(false);
            clearSessionFolders(sessionId);
            addToFolder(sessionId, newFolderId);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
