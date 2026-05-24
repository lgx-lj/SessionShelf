package com.sessionshelf.model;

/**
 * 标签实体类
 * 对应数据库 tag 表
 */
public class Tag {
    private String tagId;
    private String tagName;

    public Tag() {}

    public Tag(String tagId, String tagName) {
        this.tagId = tagId;
        this.tagName = tagName;
    }

    // Getters and Setters
    public String getTagId() { return tagId; }
    public void setTagId(String tagId) { this.tagId = tagId; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
}
