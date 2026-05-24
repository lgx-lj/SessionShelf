package com.sessionshelf.parser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sessionshelf.model.Session;
import com.sessionshelf.model.enums.SourceType;
import com.sessionshelf.util.PathUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Codex 数据源解析器
 * 读取用户目录/.codex/ 下的 SQLite 数据库和 .jsonl 会话文件
 */
public class CodexParser implements SessionParser {

    private static final String CODEX_DB_PATH = PathUtil.getCodexDbPath();
    private static final String CODEX_SESSIONS_DIR = PathUtil.getCodexSessionsPath();

    @Override
    public List<Session> parseAll() {
        List<Session> sessions = new ArrayList<>();
        if (!isAvailable()) {
            return sessions;
        }

        // 优先从 SQLite 数据库读取
        if (PathUtil.exists(CODEX_DB_PATH)) {
            sessions.addAll(parseFromDatabase());
        }

        // 补充从 JSONL 文件读取（如果数据库读取失败或不完整）
        if (sessions.isEmpty() && PathUtil.exists(CODEX_SESSIONS_DIR)) {
            sessions.addAll(parseFromJsonlFiles());
        }

        return sessions;
    }

    @Override
    public List<Session> parseIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();
        if (!isAvailable()) {
            return sessions;
        }

        // 从数据库增量读取
        if (PathUtil.exists(CODEX_DB_PATH)) {
            sessions.addAll(parseFromDatabaseIncremental(lastSyncTime));
        }

        // 从 JSONL 文件增量读取
        if (PathUtil.exists(CODEX_SESSIONS_DIR)) {
            sessions.addAll(parseFromJsonlFilesIncremental(lastSyncTime));
        }

        return sessions;
    }

    @Override
    public String readFullContent(Session session) {
        if (session == null || session.getSourceFilePath() == null) {
            return "";
        }

        // 如果来源是 JSONL 文件，直接读取
        if (session.getSourceFilePath().endsWith(".jsonl")) {
            return readJsonlContent(session.getSourceFilePath());
        }

        // 如果来源是数据库，尝试查找对应的 JSONL 文件
        return readContentFromDatabase(session.getOriginalUniqueId());
    }

    @Override
    public boolean isAvailable() {
        return PathUtil.exists(CODEX_DB_PATH) || PathUtil.exists(CODEX_SESSIONS_DIR);
    }

    /**
     * 从 SQLite 数据库解析会话
     */
    private List<Session> parseFromDatabase() {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + CODEX_DB_PATH;

        try (Connection conn = DriverManager.getConnection(url)) {
            String sql = "SELECT * FROM sessions ORDER BY created_at DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.OPENAI_CODEX);

                        String sessionId = rs.getString("id");
                        session.setSessionId("codex_" + sessionId);
                        session.setOriginalUniqueId(sessionId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        // 解析创建时间
                        long createdAt = rs.getLong("created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        // 提取内容预览
                        String content = rs.getString("content");
                        if (content != null && !content.isEmpty()) {
                            session.setContentPreview(content.length() > 200 ?
                                content.substring(0, 200) + "..." : content);
                        }

                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("解析 Codex 数据库记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 Codex 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    /**
     * 从数据库增量解析
     */
    private List<Session> parseFromDatabaseIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + CODEX_DB_PATH;

        try (Connection conn = DriverManager.getConnection(url)) {
            String sql = "SELECT * FROM sessions WHERE created_at > ? ORDER BY created_at DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, lastSyncTime);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.OPENAI_CODEX);

                        String sessionId = rs.getString("id");
                        session.setSessionId("codex_" + sessionId);
                        session.setOriginalUniqueId(sessionId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        long createdAt = rs.getLong("created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        String content = rs.getString("content");
                        if (content != null && !content.isEmpty()) {
                            session.setContentPreview(content.length() > 200 ?
                                content.substring(0, 200) + "..." : content);
                        }

                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("增量解析 Codex 数据库记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 Codex 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    /**
     * 从 JSONL 文件解析会话
     */
    private List<Session> parseFromJsonlFiles() {
        List<Session> sessions = new ArrayList<>();
        File sessionsDir = new File(CODEX_SESSIONS_DIR);

        if (!sessionsDir.exists() || !sessionsDir.isDirectory()) {
            return sessions;
        }

        // 遍历年月日目录
        File[] dateDirs = sessionsDir.listFiles(File::isDirectory);
        if (dateDirs != null) {
            for (File dateDir : dateDirs) {
                File[] jsonlFiles = dateDir.listFiles((d, name) -> name.endsWith(".jsonl"));
                if (jsonlFiles != null) {
                    for (File file : jsonlFiles) {
                        try {
                            Session session = parseJsonlFile(file);
                            if (session != null) {
                                sessions.add(session);
                            }
                        } catch (Exception e) {
                            System.err.println("解析 Codex JSONL 文件失败: " + file.getName());
                        }
                    }
                }
            }
        }

        return sessions;
    }

    /**
     * 从 JSONL 文件增量解析
     */
    private List<Session> parseFromJsonlFilesIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();
        File sessionsDir = new File(CODEX_SESSIONS_DIR);

        if (!sessionsDir.exists() || !sessionsDir.isDirectory()) {
            return sessions;
        }

        File[] dateDirs = sessionsDir.listFiles(File::isDirectory);
        if (dateDirs != null) {
            for (File dateDir : dateDirs) {
                File[] jsonlFiles = dateDir.listFiles((d, name) -> name.endsWith(".jsonl"));
                if (jsonlFiles != null) {
                    for (File file : jsonlFiles) {
                        try {
                            Path filePath = file.toPath();
                            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                            long lastModified = attrs.lastModifiedTime().toMillis();

                            if (lastModified > lastSyncTime) {
                                Session session = parseJsonlFile(file);
                                if (session != null) {
                                    sessions.add(session);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("增量解析 Codex JSONL 文件失败: " + file.getName());
                        }
                    }
                }
            }
        }

        return sessions;
    }

    /**
     * 解析单个 JSONL 文件
     */
    private Session parseJsonlFile(File file) throws IOException, NoSuchAlgorithmException {
        Session session = new Session();
        session.setSourceType(SourceType.OPENAI_CODEX);
        session.setSourceFilePath(file.getAbsolutePath());

        // 生成唯一会话 ID
        session.setSessionId(generateSessionId(file.getAbsolutePath()));
        session.setOriginalUniqueId(file.getName());

        // 读取文件内容提取元数据
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String firstLine = reader.readLine();
            if (firstLine != null && !firstLine.trim().isEmpty()) {
                JsonObject json = JsonParser.parseString(firstLine.trim()).getAsJsonObject();

                // 提取模型
                session.setModelName(getJsonString(json, "model"));

                // 尝试提取工作目录（cwd 字段）
                String wd = getJsonString(json, "cwd");
                if (wd.isEmpty()) wd = getJsonString(json, "workingDirectory");
                if (!wd.isEmpty()) {
                    session.setWorkingDirectory(wd);
                }

                // 提取创建时间
                long timestamp = getJsonLong(json, "created_at");
                if (timestamp == 0) {
                    timestamp = getJsonLong(json, "timestamp");
                }
                if (timestamp > 0) {
                    session.setCreateTime(LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(timestamp),
                        ZoneId.systemDefault()
                    ));
                }
            }
        }

        // 设置默认创建时间
        if (session.getCreateTime() == null) {
            Path filePath = file.toPath();
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            session.setCreateTime(LocalDateTime.ofInstant(
                attrs.creationTime().toInstant(),
                ZoneId.systemDefault()
            ));
        }

        // 提取标题
        session.setTitle(PathUtil.getFileNameWithoutExtension(file.getAbsolutePath()));
        session.setContentPreview(extractFirstUserMessage(file));

        return session;
    }

    /**
     * 提取第一条用户消息作为内容预览
     */
    private String extractFirstUserMessage(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    String role = getJsonString(json, "role");
                    if ("user".equals(role) || "human".equals(role)) {
                        String content = getJsonString(json, "content");
                        if (!content.isEmpty()) {
                            return content.length() > 200 ? content.substring(0, 200) + "..." : content;
                        }
                    }
                } catch (Exception e) {
                    // 跳过解析失败的行
                }
            }
        }
        return "";
    }

    /**
     * 读取 JSONL 文件完整内容
     */
    private String readJsonlContent(String filePath) {
        StringBuilder content = new StringBuilder();
        File file = new File(filePath);

        if (!file.exists()) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    String role = getJsonString(json, "role");
                    String message = getJsonString(json, "content");

                    if (!message.isEmpty()) {
                        String roleDisplay = "user".equals(role) || "human".equals(role) ? "用户" : "AI";
                        content.append("[").append(roleDisplay).append("]\n");
                        content.append(message).append("\n\n");
                    }
                } catch (Exception e) {
                    // 跳过解析失败的行
                }
            }
        } catch (IOException e) {
            System.err.println("读取 Codex 会话文件内容失败: " + filePath);
        }

        return content.toString();
    }

    /**
     * 从数据库读取会话内容
     */
    private String readContentFromDatabase(String sessionId) {
        String url = "jdbc:sqlite:" + CODEX_DB_PATH;

        try (Connection conn = DriverManager.getConnection(url)) {
            String sql = "SELECT content FROM sessions WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, sessionId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("content");
                }
            }
        } catch (SQLException e) {
            System.err.println("从数据库读取会话内容失败: " + e.getMessage());
        }

        return "";
    }

    /**
     * 生成会话 ID
     */
    private String generateSessionId(String filePath) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(filePath.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return "codex_" + sb.toString();
    }

    private String getJsonString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return "";
    }

    private long getJsonLong(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsLong();
        }
        return 0;
    }
}
