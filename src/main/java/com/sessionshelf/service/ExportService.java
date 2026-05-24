package com.sessionshelf.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sessionshelf.model.Session;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 导出服务
 * 支持将会话导出为 Markdown 和 JSON 格式
 */
public class ExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final SyncService syncService;

    public ExportService(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * 导出为 Markdown 格式
     */
    public File exportToMarkdown(Session session, String outputDir) throws IOException {
        String content = buildMarkdownContent(session);
        String fileName = sanitizeFileName(session.getTitle()) + "_" +
                         LocalDateTime.now().format(DATE_FORMATTER) + ".md";
        File outputFile = new File(outputDir, fileName);
        writeFile(outputFile, content);
        return outputFile;
    }

    /**
     * 导出为 JSON 格式
     */
    public File exportToJson(Session session, String outputDir) throws IOException {
        String content = buildJsonContent(session);
        String fileName = sanitizeFileName(session.getTitle()) + "_" +
                         LocalDateTime.now().format(DATE_FORMATTER) + ".json";
        File outputFile = new File(outputDir, fileName);
        writeFile(outputFile, content);
        return outputFile;
    }

    /**
     * 批量导出为 Markdown
     */
    public File exportBatchToMarkdown(java.util.List<Session> sessions, String outputDir) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("# AI 会话导出\n\n");
        content.append("导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        content.append("共计 ").append(sessions.size()).append(" 个会话\n\n");
        content.append("---\n\n");

        for (Session session : sessions) {
            content.append(buildMarkdownContent(session));
            content.append("\n\n---\n\n");
        }

        String fileName = "批量导出_" + LocalDateTime.now().format(DATE_FORMATTER) + ".md";
        File outputFile = new File(outputDir, fileName);
        writeFile(outputFile, content.toString());
        return outputFile;
    }

    /**
     * 构建 Markdown 内容
     */
    private String buildMarkdownContent(Session session) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(session.getTitle() != null ? session.getTitle() : "未命名会话").append("\n\n");

        // 元数据
        md.append("## 会话信息\n\n");
        md.append("- **来源**: ").append(session.getSourceType() != null ? session.getSourceType().getDisplayName() : "未知").append("\n");
        md.append("- **模型**: ").append(session.getModelName() != null ? session.getModelName() : "未知").append("\n");
        md.append("- **创建时间**: ").append(session.getCreateTime() != null ? session.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "未知").append("\n\n");

        // 对话内容
        md.append("## 对话内容\n\n");
        String fullContent = syncService.readSessionContent(session);
        if (fullContent != null && !fullContent.isEmpty()) {
            md.append(fullContent);
        } else {
            md.append("*无法读取完整对话内容*\n");
        }

        return md.toString();
    }

    /**
     * 构建 JSON 内容
     */
    private String buildJsonContent(Session session) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject json = new JsonObject();

        // 基本信息
        json.addProperty("sessionId", session.getSessionId());
        json.addProperty("sourceType", session.getSourceType() != null ? session.getSourceType().getDisplayName() : "");
        json.addProperty("title", session.getTitle());
        json.addProperty("modelName", session.getModelName());
        json.addProperty("createTime", session.getCreateTime() != null ? session.getCreateTime().toString() : "");
        json.addProperty("sourceFilePath", session.getSourceFilePath());

        // 对话内容
        String fullContent = syncService.readSessionContent(session);
        json.addProperty("content", fullContent);

        return gson.toJson(json);
    }

    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "未命名";
        }
        // 移除或替换非法字符
        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_")
                      .replaceAll("\\s+", "_")
                      .substring(0, Math.min(fileName.length(), 50));
    }

    /**
     * 写入文件
     */
    private void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
