package com.sessionshelf.service;

import com.sessionshelf.dao.TagDAO;
import com.sessionshelf.model.Tag;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 标签业务服务
 * 封装标签管理的业务逻辑
 */
public class TagService {

    private final TagDAO tagDAO;

    public TagService() {
        this.tagDAO = new TagDAO();
    }

    /**
     * 创建新标签
     */
    public Tag createTag(String tagName) throws SQLException {
        Tag tag = new Tag();
        tag.setTagId(UUID.randomUUID().toString());
        tag.setTagName(tagName);
        tagDAO.save(tag);
        return tag;
    }

    /**
     * 重命名标签
     */
    public void renameTag(String tagId, String newName) throws SQLException {
        tagDAO.rename(tagId, newName);
    }

    /**
     * 删除标签
     */
    public void deleteTag(String tagId) throws SQLException {
        tagDAO.delete(tagId);
    }

    /**
     * 获取所有标签
     */
    public List<Tag> getAllTags() throws SQLException {
        return tagDAO.findAll();
    }

    /**
     * 根据 ID 获取标签
     */
    public Optional<Tag> getTagById(String tagId) throws SQLException {
        return tagDAO.findById(tagId);
    }

    /**
     * 获取会话关联的标签
     */
    public List<Tag> getTagsBySessionId(String sessionId) throws SQLException {
        return tagDAO.findBySessionId(sessionId);
    }

    /**
     * 为会话添加标签
     */
    public void addTagToSession(String sessionId, String tagId) throws SQLException {
        tagDAO.addTagToSession(sessionId, tagId);
    }

    /**
     * 移除会话的标签
     */
    public void removeTagFromSession(String sessionId, String tagId) throws SQLException {
        tagDAO.removeTagFromSession(sessionId, tagId);
    }

    /**
     * 清除会话的所有标签
     */
    public void clearSessionTags(String sessionId) throws SQLException {
        tagDAO.clearSessionTags(sessionId);
    }
}
