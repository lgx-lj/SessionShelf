package com.sessionshelf.model;

import com.sessionshelf.model.enums.SourceType;
import java.time.LocalDateTime;

/**
 * 会话实体类
 * 对应数据库 session_base 表
 */
public class Session {
    private String sessionId;
    private SourceType sourceType;
    private String originalUniqueId;
    private String title;
    private String modelName;
    private LocalDateTime createTime;
    private String contentPreview;
    private String sourceFilePath;
    private String workingDirectory;
    private LocalDateTime lastResumeTime;

    public Session() {}

    public Session(String sessionId, SourceType sourceType, String originalUniqueId,
                   String title, String modelName, LocalDateTime createTime,
                   String contentPreview, String sourceFilePath) {
        this.sessionId = sessionId;
        this.sourceType = sourceType;
        this.originalUniqueId = originalUniqueId;
        this.title = title;
        this.modelName = modelName;
        this.createTime = createTime;
        this.contentPreview = contentPreview;
        this.sourceFilePath = sourceFilePath;
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getOriginalUniqueId() { return originalUniqueId; }
    public void setOriginalUniqueId(String originalUniqueId) { this.originalUniqueId = originalUniqueId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getContentPreview() { return contentPreview; }
    public void setContentPreview(String contentPreview) { this.contentPreview = contentPreview; }

    public String getSourceFilePath() { return sourceFilePath; }
    public void setSourceFilePath(String sourceFilePath) { this.sourceFilePath = sourceFilePath; }

    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

    public LocalDateTime getLastResumeTime() { return lastResumeTime; }
    public void setLastResumeTime(LocalDateTime lastResumeTime) { this.lastResumeTime = lastResumeTime; }
}
