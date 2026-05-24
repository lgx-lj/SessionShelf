package com.sessionshelf.model;

/**
 * 文件夹实体类
 * 对应数据库 folder 表
 */
public class Folder {
    private String folderId;
    private String folderName;
    private String parentFolderId;
    private int sortOrder;

    public Folder() {}

    public Folder(String folderId, String folderName, String parentFolderId, int sortOrder) {
        this.folderId = folderId;
        this.folderName = folderName;
        this.parentFolderId = parentFolderId;
        this.sortOrder = sortOrder;
    }

    // Getters and Setters
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }

    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }

    public String getParentFolderId() { return parentFolderId; }
    public void setParentFolderId(String parentFolderId) { this.parentFolderId = parentFolderId; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
