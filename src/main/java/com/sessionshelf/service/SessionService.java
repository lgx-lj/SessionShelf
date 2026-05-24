package com.sessionshelf.service;

import com.sessionshelf.dao.SessionDAO;
import com.sessionshelf.dao.SessionFolderRelDAO;
import com.sessionshelf.dao.TagDAO;
import com.sessionshelf.model.Session;
import com.sessionshelf.model.enums.SourceType;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 会话业务服务
 * 封装会话相关的业务逻辑
 */
public class SessionService {

    private final SessionDAO sessionDAO;
    private final SessionFolderRelDAO sessionFolderRelDAO;
    private final TagDAO tagDAO;

    public SessionService() {
        this.sessionDAO = new SessionDAO();
        this.sessionFolderRelDAO = new SessionFolderRelDAO();
        this.tagDAO = new TagDAO();
    }

    /**
     * 保存会话
     */
    public void saveSession(Session session) throws SQLException {
        sessionDAO.save(session);
    }

    /**
     * 批量保存会话
     */
    public void saveSessions(List<Session> sessions) throws SQLException {
        if (sessions != null && !sessions.isEmpty()) {
            sessionDAO.saveAll(sessions);
        }
    }

    /**
     * 获取所有会话
     */
    public List<Session> getAllSessions() throws SQLException {
        return sessionDAO.findAll();
    }

    /**
     * 根据 ID 获取会话
     */
    public Optional<Session> getSessionById(String sessionId) throws SQLException {
        return sessionDAO.findById(sessionId);
    }

    /**
     * 按数据源类型筛选会话
     */
    public List<Session> getSessionsBySourceType(SourceType sourceType) throws SQLException {
        return sessionDAO.findBySourceType(sourceType);
    }

    /**
     * 获取指定文件夹下的会话
     */
    public List<Session> getSessionsByFolderId(String folderId) throws SQLException {
        return sessionDAO.findByFolderId(folderId);
    }

    /**
     * 获取指定标签的会话
     */
    public List<Session> getSessionsByTagId(String tagId) throws SQLException {
        return sessionDAO.findByTagId(tagId);
    }

    /**
     * 全局搜索会话
     */
    public List<Session> searchSessions(String keyword) throws SQLException {
        return sessionDAO.search(keyword);
    }

    /**
     * 将会话移动到文件夹
     */
    public void moveSessionToFolder(String sessionId, String folderId) throws SQLException {
        sessionFolderRelDAO.moveToFolder(sessionId, folderId);
    }

    /**
     * 将会话添加到文件夹（支持多目录）
     */
    public void addSessionToFolder(String sessionId, String folderId) throws SQLException {
        sessionFolderRelDAO.addToFolder(sessionId, folderId);
    }

    /**
     * 将会话从文件夹移除
     */
    public void removeSessionFromFolder(String sessionId, String folderId) throws SQLException {
        sessionFolderRelDAO.removeFromFolder(sessionId, folderId);
    }

    /**
     * 获取会话所属的文件夹 ID 列表
     */
    public List<String> getSessionFolderIds(String sessionId) throws SQLException {
        return sessionFolderRelDAO.getFolderIdsBySession(sessionId);
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
     * 删除会话
     */
    public void deleteSession(String sessionId) throws SQLException {
        // 先清除关联关系
        sessionFolderRelDAO.clearSessionFolders(sessionId);
        tagDAO.clearSessionTags(sessionId);
        // 再删除会话
        sessionDAO.delete(sessionId);
    }
}
