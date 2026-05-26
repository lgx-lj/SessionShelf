package com.sessionshelf.dao;

import java.sql.*;
import java.util.*;

/**
 * 收藏项目数据访问对象
 * project_name 为工作目录的最后一级文件夹名
 */
public class FavoriteProjectDAO {

    private final Connection connection;

    public FavoriteProjectDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    public void add(String projectName) throws SQLException {
        String sql = "INSERT OR IGNORE INTO project_favorite (project_name) VALUES (?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            pstmt.executeUpdate();
        }
    }

    public void remove(String projectName) throws SQLException {
        String sql = "DELETE FROM project_favorite WHERE project_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            pstmt.executeUpdate();
        }
    }

    public boolean isFavorite(String projectName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM project_favorite WHERE project_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public Set<String> getAll() throws SQLException {
        String sql = "SELECT project_name FROM project_favorite";
        Set<String> names = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) names.add(rs.getString("project_name"));
        }
        return names;
    }
}
