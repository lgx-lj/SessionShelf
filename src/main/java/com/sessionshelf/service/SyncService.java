package com.sessionshelf.service;

import com.sessionshelf.model.Session;
import com.sessionshelf.parser.SessionParser;
import com.sessionshelf.parser.ClaudeCodeParser;
import com.sessionshelf.parser.CodexParser;
import com.sessionshelf.parser.CCSwitchParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据同步服务
 * 负责从各个数据源同步会话到本地数据库
 */
public class SyncService {

    private final SessionService sessionService;
    private final List<SessionParser> parsers;
    private final ClaudeCodeParser claudeCodeParser;
    private long lastSyncTime;

    public SyncService() {
        this.sessionService = new SessionService();
        this.parsers = new ArrayList<>();
        this.lastSyncTime = 0;

        // 初始化解析器
        claudeCodeParser = new ClaudeCodeParser();
        parsers.add(claudeCodeParser);
        parsers.add(new CodexParser());
    }

    /**
     * 设置扫描路径（传递给 ClaudeCodeParser）
     */
    public void setScanPaths(List<String> paths) {
        claudeCodeParser.setScanPaths(paths);
    }

    /**
     * 添加 CC Switch 数据源
     */
    public void addCCSwitchSource(String dbPath) {
        boolean exists = parsers.stream().anyMatch(p -> p instanceof CCSwitchParser);
        if (!exists) {
            parsers.add(new CCSwitchParser(dbPath));
        }
    }

    /**
     * 全量同步所有数据源
     */
    public int syncAll() {
        int totalSynced = 0;

        for (SessionParser parser : parsers) {
            if (parser.isAvailable()) {
                try {
                    List<Session> sessions = parser.parseAll();
                    if (sessions != null && !sessions.isEmpty()) {
                        sessionService.saveSessions(sessions);
                        totalSynced += sessions.size();
                        System.out.println("同步 " + sessions.size() + " 条会话");
                    }
                } catch (Exception e) {
                    System.err.println("同步失败: " + e.getMessage());
                }
            }
        }

        lastSyncTime = System.currentTimeMillis();
        return totalSynced;
    }

    /**
     * 增量同步
     */
    public int syncIncremental() {
        int totalSynced = 0;

        for (SessionParser parser : parsers) {
            if (parser.isAvailable()) {
                try {
                    List<Session> sessions = parser.parseIncremental(lastSyncTime);
                    if (sessions != null && !sessions.isEmpty()) {
                        sessionService.saveSessions(sessions);
                        totalSynced += sessions.size();
                    }
                } catch (Exception e) {
                    System.err.println("增量同步失败: " + e.getMessage());
                }
            }
        }

        lastSyncTime = System.currentTimeMillis();
        return totalSynced;
    }

    /**
     * 读取会话完整内容
     */
    public String readSessionContent(Session session) {
        for (SessionParser parser : parsers) {
            if (parser.isAvailable()) {
                try {
                    String content = parser.readFullContent(session);
                    if (content != null && !content.isEmpty()) return content;
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    /**
     * 数据源状态
     */
    public String checkDataSourceStatus() {
        StringBuilder status = new StringBuilder();
        for (SessionParser parser : parsers) {
            if (parser instanceof ClaudeCodeParser) {
                status.append("Claude Code: ").append(parser.isAvailable() ? "可用" : "不可用").append("\n");
            } else if (parser instanceof CodexParser) {
                status.append("Codex: ").append(parser.isAvailable() ? "可用" : "不可用").append("\n");
            } else if (parser instanceof CCSwitchParser) {
                status.append("CC Switch: ").append(parser.isAvailable() ? "可用" : "不可用").append("\n");
            }
        }
        return status.toString();
    }
}
