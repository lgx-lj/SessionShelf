package com.sessionshelf.dao;

import com.sessionshelf.model.Folder;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文件夹数据访问对象
 * 提供 folder 表的 CRUD 操作
 */
public class FolderDAO {

    private final Connection connection;

    public FolderDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /**
     * 插入文件夹
     */
    public void save(Folder folder) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO folder (folder_id, folder_name, parent_folder_id, sort_order)
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folder.getFolderId());
            pstmt.setString(2, folder.getFolderName());
            pstmt.setString(3, folder.getParentFolderId());
            pstmt.setInt(4, folder.getSortOrder());
            pstmt.executeUpdate();
        }
    }

    /**
     * 查询所有文件夹
     */
    public List<Folder> findAll() throws SQLException {
        String sql = "SELECT * FROM folder ORDER BY sort_order";
        List<Folder> folders = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                folders.add(mapResultSet(rs));
            }
        }
        return folders;
    }

    /**
     * 根据父文件夹 ID 查询子文件夹
     */
    public List<Folder> findByParentId(String parentFolderId) throws SQLException {
        String sql = "SELECT * FROM folder WHERE parent_folder_id = ? ORDER BY sort_order";
        List<Folder> folders = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            if (parentFolderId == null) {
                pstmt.setNull(1, Types.VARCHAR);
            } else {
                pstmt.setString(1, parentFolderId);
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                folders.add(mapResultSet(rs));
            }
        }
        return folders;
    }

    /**
     * 查询根文件夹（parent_folder_id 为 NULL）
     */
    public List<Folder> findRootFolders() throws SQLException {
        return findByParentId(null);
    }

    /**
     * 根据 ID 查询文件夹
     */
    public Optional<Folder> findById(String folderId) throws SQLException {
        String sql = "SELECT * FROM folder WHERE folder_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        }
    }

    /**
     * 重命名文件夹
     */
    public void rename(String folderId, String newName) throws SQLException {
        String sql = "UPDATE folder SET folder_name = ? WHERE folder_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, folderId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 删除文件夹
     */
    public void delete(String folderId) throws SQLException {
        String sql = "DELETE FROM folder WHERE folder_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 更新文件夹排序
     */
    public void updateSortOrder(String folderId, int sortOrder) throws SQLException {
        String sql = "UPDATE folder SET sort_order = ? WHERE folder_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, sortOrder);
            pstmt.setString(2, folderId);
            pstmt.executeUpdate();
        }
    }

    /**
     * 检查文件夹是否有子文件夹
     */
    public boolean hasChildren(String folderId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM folder WHERE parent_folder_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, folderId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * 将 ResultSet 映射为 Folder 对象
     */
    private Folder mapResultSet(ResultSet rs) throws SQLException {
        Folder folder = new Folder();
        folder.setFolderId(rs.getString("folder_id"));
        folder.setFolderName(rs.getString("folder_name"));
        folder.setParentFolderId(rs.getString("parent_folder_id"));
        folder.setSortOrder(rs.getInt("sort_order"));
        return folder;
    }
}
