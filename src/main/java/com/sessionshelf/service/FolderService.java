package com.sessionshelf.service;

import com.sessionshelf.dao.FolderDAO;
import com.sessionshelf.model.Folder;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文件夹业务服务
 * 封装文件夹管理的业务逻辑
 */
public class FolderService {

    private final FolderDAO folderDAO;

    public FolderService() {
        this.folderDAO = new FolderDAO();
    }

    /**
     * 创建新文件夹
     */
    public Folder createFolder(String folderName, String parentFolderId) throws SQLException {
        Folder folder = new Folder();
        folder.setFolderId(UUID.randomUUID().toString());
        folder.setFolderName(folderName);
        folder.setParentFolderId(parentFolderId);
        folder.setSortOrder(calculateNextSortOrder(parentFolderId));
        folderDAO.save(folder);
        return folder;
    }

    /**
     * 重命名文件夹
     */
    public void renameFolder(String folderId, String newName) throws SQLException {
        // 不允许重命名默认文件夹
        if ("default".equals(folderId)) {
            throw new IllegalArgumentException("不能重命名默认文件夹「未归档」");
        }
        folderDAO.rename(folderId, newName);
    }

    /**
     * 删除文件夹
     */
    public void deleteFolder(String folderId) throws SQLException {
        // 不允许删除默认文件夹
        if ("default".equals(folderId)) {
            throw new IllegalArgumentException("不能删除默认文件夹「未归档」");
        }

        // 检查是否有子文件夹
        if (folderDAO.hasChildren(folderId)) {
            throw new IllegalStateException("请先删除子文件夹");
        }

        folderDAO.delete(folderId);
    }

    /**
     * 获取所有文件夹
     */
    public List<Folder> getAllFolders() throws SQLException {
        return folderDAO.findAll();
    }

    /**
     * 获取根文件夹列表
     */
    public List<Folder> getRootFolders() throws SQLException {
        return folderDAO.findRootFolders();
    }

    /**
     * 获取子文件夹列表
     */
    public List<Folder> getChildFolders(String parentFolderId) throws SQLException {
        return folderDAO.findByParentId(parentFolderId);
    }

    /**
     * 根据 ID 获取文件夹
     */
    public Optional<Folder> getFolderById(String folderId) throws SQLException {
        return folderDAO.findById(folderId);
    }

    /**
     * 更新文件夹排序
     */
    public void updateFolderOrder(String folderId, int newOrder) throws SQLException {
        folderDAO.updateSortOrder(folderId, newOrder);
    }

    /**
     * 计算下一个排序号
     */
    private int calculateNextSortOrder(String parentFolderId) throws SQLException {
        List<Folder> siblings;
        if (parentFolderId == null) {
            siblings = folderDAO.findRootFolders();
        } else {
            siblings = folderDAO.findByParentId(parentFolderId);
        }
        return siblings.isEmpty() ? 0 : siblings.stream()
                .mapToInt(Folder::getSortOrder)
                .max()
                .orElse(0) + 1;
    }
}
