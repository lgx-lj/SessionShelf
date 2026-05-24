package com.sessionshelf.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;

/**
 * 路径工具类
 * 处理跨平台的文件路径问题
 */
public class PathUtil {

    private PathUtil() {}

    /**
     * 获取用户主目录
     */
    public static String getUserHome() {
        return System.getProperty("user.home");
    }

    /**
     * 获取 Claude Code 数据目录
     * 路径: 用户主目录/.claude/projects/
     */
    public static String getClaudeCodePath() {
        return Paths.get(getUserHome(), ".claude", "projects").toString();
    }

    /**
     * 获取 OpenAI Codex 数据库路径
     * 路径: 用户主目录/.codex/state_5.sqlite
     */
    public static String getCodexDbPath() {
        return Paths.get(getUserHome(), ".codex", "state_5.sqlite").toString();
    }

    /**
     * 获取 OpenAI Codex 会话文件目录
     * 路径: 用户主目录/.codex/sessions/
     */
    public static String getCodexSessionsPath() {
        return Paths.get(getUserHome(), ".codex", "sessions").toString();
    }

    /**
     * 检查文件或目录是否存在
     */
    public static boolean exists(String path) {
        return new File(path).exists();
    }

    /**
     * 获取文件扩展名
     */
    public static String getExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot >= 0) {
            return filePath.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 判断是否为 JSONL 文件
     */
    public static boolean isJsonlFile(String filePath) {
        return "jsonl".equals(getExtension(filePath));
    }

    /**
     * 获取文件名（不含扩展名）
     */
    public static String getFileNameWithoutExtension(String filePath) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
}
