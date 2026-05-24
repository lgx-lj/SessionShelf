package com.sessionshelf.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Claude Code 数据源解析器
 * 支持多路径扫描，在每个扫描路径下查找 .claude/projects/ 目录
 */
public class ClaudeCodeParser implements SessionParser {

    private List<String> scanPaths = new ArrayList<>();

    public ClaudeCodeParser() {
        // 默认扫描用户主目录
        scanPaths.add(System.getProperty("user.home"));
    }

    /**
     * 设置扫描路径列表
     */
    public void setScanPaths(List<String> paths) {
        this.scanPaths = paths != null ? paths : new ArrayList<>();
    }

    /**
     * 添加扫描路径
     */
    public void addScanPath(String path) {
        if (path != null && !path.isEmpty()) {
            scanPaths.add(path);
        }
    }

    @Override
    public List<Session> parseAll() {
        List<Session> sessions = new ArrayList<>();

        for (String scanPath : scanPaths) {
            // 在每个扫描路径下查找 .claude/projects/ 目录
            String claudeDir = scanPath + File.separator + ".claude" + File.separator + "projects";
            File dir = new File(claudeDir);

            if (dir.exists() && dir.isDirectory()) {
                System.out.println("扫描 Claude Code 目录: " + claudeDir);
                scanDirectory(dir, sessions);
            }

            // 也检查直接路径（用户可能直接配置了 .claude/projects/ 路径）
            File directDir = new File(scanPath);
            if (directDir.exists() && directDir.isDirectory() && scanPath.endsWith("projects")) {
                scanDirectory(directDir, sessions);
            }
        }

        System.out.println("Claude Code 扫描完成，共 " + sessions.size() + " 个会话");
        return sessions;
    }

    @Override
    public List<Session> parseIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();

        for (String scanPath : scanPaths) {
            String claudeDir = scanPath + File.separator + ".claude" + File.separator + "projects";
            File dir = new File(claudeDir);

            if (dir.exists() && dir.isDirectory()) {
                scanDirectoryIncremental(dir, sessions, lastSyncTime);
            }
        }

        return sessions;
    }

    @Override
    public String readFullContent(Session session) {
        if (session == null || session.getSourceFilePath() == null) return "";

        StringBuilder content = new StringBuilder();
        File file = new File(session.getSourceFilePath());
        if (!file.exists()) return "";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    String type = getJsonString(json, "type");

                    if ("user".equals(type) || "assistant".equals(type)) {
                        String message = extractMessageContent(json);
                        if (!message.isEmpty()) {
                            String roleDisplay = "user".equals(type) ? "用户" : "AI";
                            content.append("[").append(roleDisplay).append("]\n");
                            content.append(sanitizeText(message)).append("\n\n");
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            System.err.println("读取失败: " + session.getSourceFilePath());
        }
        return content.toString();
    }

    @Override
    public boolean isAvailable() {
        for (String path : scanPaths) {
            String claudeDir = path + File.separator + ".claude" + File.separator + "projects";
            if (new File(claudeDir).exists()) return true;
        }
        return false;
    }

    private void scanDirectory(File dir, List<Session> sessions) {
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, sessions);
            } else if (file.getName().endsWith(".jsonl")) {
                try {
                    Session session = parseSessionFile(file);
                    if (session != null) sessions.add(session);
                } catch (Exception e) {
                    System.err.println("解析失败: " + file.getAbsolutePath());
                }
            }
        }
    }

    private void scanDirectoryIncremental(File dir, List<Session> sessions, long lastSyncTime) {
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryIncremental(file, sessions, lastSyncTime);
            } else if (file.getName().endsWith(".jsonl")) {
                try {
                    long lastModified = Files.getLastModifiedTime(file.toPath()).toMillis();
                    if (lastModified > lastSyncTime) {
                        Session session = parseSessionFile(file);
                        if (session != null) sessions.add(session);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private Session parseSessionFile(File file) throws IOException, NoSuchAlgorithmException {
        Session session = new Session();
        session.setSourceType(SourceType.CLAUDE_CODE);
        session.setSourceFilePath(file.getAbsolutePath());
        session.setSessionId(generateId(file.getAbsolutePath()));
        session.setOriginalUniqueId(file.getName().replace(".jsonl", ""));

        // 从路径提取项目名
        String path = file.getAbsolutePath();
        String projectName = extractProjectName(path);
        session.setTitle(projectName + " - " + file.getName().replace(".jsonl", ""));

        // 扫描前 25 行提取元数据
        boolean foundCwd = false;
        boolean foundModel = false;
        boolean foundTime = false;
        boolean foundSessionId = false;
        int lineCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null && lineCount < 25) {
                lineCount++;
                line = line.trim();
                if (line.isEmpty() || !line.startsWith("{")) continue;

                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();

                    // 提取 cwd（真实项目工作目录）
                    if (!foundCwd) {
                        String cwd = getJsonString(json, "cwd");
                        if (!cwd.isEmpty()) {
                            cwd = cwd.replace("\\\\", "\\").replace("/", "\\");
                            session.setWorkingDirectory(cwd);
                            foundCwd = true;
                        }
                    }

                    // 提取 Claude Code 原始 sessionId（用于 claude --resume）
                    if (!foundSessionId) {
                        String sid = getJsonString(json, "sessionId");
                        if (!sid.isEmpty()) {
                            session.setOriginalUniqueId(sid);
                            foundSessionId = true;
                        }
                    }

                    // 提取 model（可能在顶层或 message 对象内）
                    if (!foundModel) {
                        String model = getJsonString(json, "model");
                        if (model.isEmpty() && json.has("message") && json.get("message").isJsonObject()) {
                            model = getJsonString(json.getAsJsonObject("message"), "model");
                        }
                        if (!model.isEmpty()) {
                            session.setModelName(model);
                            foundModel = true;
                        }
                    }

                    // 提取 timestamp
                    if (!foundTime) {
                        long ts = getJsonLong(json, "timestamp");
                        if (ts == 0) ts = getJsonLong(json, "created_at");
                        if (ts > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(ts), ZoneId.systemDefault()));
                            foundTime = true;
                        }
                    }

                    // 四个字段都找到就提前退出
                    if (foundCwd && foundModel && foundTime && foundSessionId) break;

                } catch (Exception ignored) {
                    // 跳过解析失败的行
                }
            }
        }

        // 回退：如果没找到 cwd，从文件路径中提取项目路径
        if (!foundCwd) {
            session.setWorkingDirectory(extractWorkingDirFromPath(path));
        }

        if (session.getCreateTime() == null) {
            session.setCreateTime(LocalDateTime.ofInstant(
                Files.getLastModifiedTime(file.toPath()).toInstant(), ZoneId.systemDefault()));
        }

        session.setContentPreview(extractPreview(file));
        return session;
    }

    private String extractProjectName(String path) {
        String normalized = path.replace("\\", "/");
        int idx = normalized.indexOf("/.claude/projects/");
        if (idx > 0) {
            String projectPath = normalized.substring(0, idx);
            String[] parts = projectPath.split("/");
            return parts.length > 0 ? parts[parts.length - 1] : "未知项目";
        }
        return "未知项目";
    }

    /**
     * 从文件路径中提取工作目录（回退方案）
     * 解码 .claude/projects/ 下的编码目录名
     * 例：D--SoftwareCoding-workspace-LGX-Project → D:\SoftwareCoding\workspace\LGX\Project
     */
    private String extractWorkingDirFromPath(String path) {
        String normalized = path.replace("\\", "/");
        int idx = normalized.indexOf("/.claude/projects/");
        if (idx > 0) {
            String afterProjects = normalized.substring(idx + "/.claude/projects/".length());
            int lastSlash = afterProjects.lastIndexOf('/');
            String encodedDir = lastSlash > 0 ? afterProjects.substring(0, lastSlash) : afterProjects;

            if (!encodedDir.isEmpty()) {
                // 解码：第一个 "--" → ":\", 后续 "-" → "\"
                // replaceFirst 的替换串中 \\ 表示一个字面反斜杠
                // replace 是字面替换，"\\" 也是一个字面反斜杠
                String decoded = encodedDir.replaceFirst("--", ":\\\\").replace("-", "\\");
                return decoded;
            }
        }
        return null;
    }

    /**
     * 清理文本：只保留正常可读字符，删除所有控制字符和装饰符号
     */
    private String sanitizeText(String text) {
        if (text == null) return "";
        // 白名单：只保留以下字符，其他全部删除
        // - 常规 ASCII（空格到~）
        // - 中文 CJK（一-鿿）
        // - 中文标点（　-〿）
        // - 全角字符（＀-￯）
        // - 常用 Unicode 符号（ -⁯ 中的空格类）
        // - 换行 \n 回车 \r 制表 \t
        String cleaned = text.replaceAll("[^\\x20-\\x7E\\n\\r\\t\\u4e00-\\u9fff\\u3000-\\u303f\\uff01-\\uff5e\\uff65-\\uffef]", "");
        // 去除连续多余空行
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned;
    }

    /**
     * 清洗角色行：去掉行首的所有特殊符号前缀，只保留「用户」或「AI」
     * 例如 "♦♦♦♦♦ 用户" → "用户"，"★★ AI" → "AI"
     */
    private String cleanRoleLine(String line) {
        if (line == null) return "";
        // 去掉行首所有非中文、非英文字母字符
        return line.replaceAll("^[^\\u4e00-\\u9fa5a-zA-Z]+", "").trim();
    }

    private String extractPreview(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    // 用户消息：顶层 type=user，内容在 message.content
                    if ("user".equals(getJsonString(json, "type"))) {
                        String msg = extractMessageContent(json);
                        if (!msg.isEmpty()) {
                            String clean = sanitizePreview(sanitizeText(msg));
                            return clean.length() > 200 ? clean.substring(0, 200) + "..." : clean;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    /**
     * 从消息 JSON 中提取文本内容
     * 用户消息：message.content（可能是字符串或数组）
     * AI 消息：message.content（数组，取 type=text 的项）
     */
    private String extractMessageContent(JsonObject json) {
        // 优先从 message.content 提取
        if (json.has("message") && json.get("message").isJsonObject()) {
            JsonObject msg = json.getAsJsonObject("message");
            return extractContentFromElement(msg.get("content"));
        }
        // 兼容顶层 content
        if (json.has("content")) {
            return extractContentFromElement(json.get("content"));
        }
        return "";
    }

    private String extractContentFromElement(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return "";
        if (elem.isJsonPrimitive()) return elem.getAsString();
        if (elem.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement e : elem.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    JsonObject obj = e.getAsJsonObject();
                    String type = getJsonString(obj, "type");
                    if ("text".equals(type)) {
                        sb.append(getJsonString(obj, "text"));
                    }
                } else if (e.isJsonPrimitive()) {
                    sb.append(e.getAsString());
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * 清理预览文本：去除控制字符、工具调用残留等
     */
    private String sanitizePreview(String text) {
        if (text == null) return "";
        // 去除控制字符（保留换行和空格）
        String cleaned = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        // 去除多余空白
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String generateId(String path) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(path.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return "claude_" + sb;
    }

    private String getJsonString(JsonObject json, String key) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsString() : "";
    }

    private long getJsonLong(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try { return json.get(key).getAsLong(); } catch (Exception e) { return 0; }
        }
        return 0;
    }
}
