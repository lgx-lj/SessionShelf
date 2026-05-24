package com.sessionshelf.parser;

import com.sessionshelf.model.Session;
import com.sessionshelf.model.enums.SourceType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * CC Switch 数据源解析器
 * 读取 CC Switch 本地 SQLite 数据库中的聊天会话记录
 *
 * 注意：由于 CC Switch 的具体数据库结构可能因版本而异，
 * 此解析器采用通用的表结构探测方式，适配常见的会话存储格式。
 */
public class CCSwitchParser implements SessionParser {

    // CC Switch 数据库路径（需要用户配置）
    private String dbPath;

    public CCSwitchParser() {
        this.dbPath = "";
    }

    public CCSwitchParser(String dbPath) {
        this.dbPath = dbPath;
    }

    public void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public List<Session> parseAll() {
        List<Session> sessions = new ArrayList<>();
        if (!isAvailable()) {
            return sessions;
        }

        // 尝试不同的表结构
        sessions.addAll(parseFromChatsTable());
        if (sessions.isEmpty()) {
            sessions.addAll(parseFromConversationsTable());
        }
        if (sessions.isEmpty()) {
            sessions.addAll(parseFromSessionsTable());
        }

        return sessions;
    }

    @Override
    public List<Session> parseIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();
        if (!isAvailable()) {
            return sessions;
        }

        sessions.addAll(parseFromChatsTableIncremental(lastSyncTime));
        if (sessions.isEmpty()) {
            sessions.addAll(parseFromConversationsTableIncremental(lastSyncTime));
        }
        if (sessions.isEmpty()) {
            sessions.addAll(parseFromSessionsTableIncremental(lastSyncTime));
        }

        return sessions;
    }

    @Override
    public String readFullContent(Session session) {
        if (session == null || session.getOriginalUniqueId() == null) {
            return "";
        }

        // 尝试从消息表读取完整对话
        return readMessagesFromDatabase(session.getOriginalUniqueId());
    }

    @Override
    public boolean isAvailable() {
        return dbPath != null && !dbPath.isEmpty() &&
               new java.io.File(dbPath).exists();
    }

    /**
     * 从 chats 表解析（常见结构）
     */
    private List<Session> parseFromChatsTable() {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url)) {
            // 检查表是否存在
            if (!tableExists(conn, "chats")) {
                return sessions;
            }

            String sql = "SELECT id, title, model, created_at, updated_at FROM chats ORDER BY updated_at DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.CC_SWITCH);

                        String chatId = rs.getString("id");
                        session.setSessionId("ccswitch_" + chatId);
                        session.setOriginalUniqueId(chatId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        // 解析时间
                        long createdAt = getTimestamp(rs, "created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        // 提取内容预览
                        session.setContentPreview(extractFirstMessage(conn, chatId));

                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("解析 CC Switch chats 记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 CC Switch 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    /**
     * 从 conversations 表解析（另一种常见结构）
     */
    private List<Session> parseFromConversationsTable() {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (!tableExists(conn, "conversations")) {
                return sessions;
            }

            String sql = "SELECT id, title, model, created_at FROM conversations ORDER BY created_at DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.CC_SWITCH);

                        String convId = rs.getString("id");
                        session.setSessionId("ccswitch_" + convId);
                        session.setOriginalUniqueId(convId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        long createdAt = getTimestamp(rs, "created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        session.setContentPreview(extractFirstMessageFromConversations(conn, convId));

                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("解析 CC Switch conversations 记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 CC Switch 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    /**
     * 从 sessions 表解析
     */
    private List<Session> parseFromSessionsTable() {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (!tableExists(conn, "sessions")) {
                return sessions;
            }

            String sql = "SELECT id, title, model, created_at FROM sessions ORDER BY created_at DESC";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.CC_SWITCH);

                        String sessionId = rs.getString("id");
                        session.setSessionId("ccswitch_" + sessionId);
                        session.setOriginalUniqueId(sessionId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        long createdAt = getTimestamp(rs, "created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("解析 CC Switch sessions 记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 CC Switch 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    // 增量解析方法（带时间过滤）
    private List<Session> parseFromChatsTableIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (!tableExists(conn, "chats")) {
                return sessions;
            }

            String sql = "SELECT id, title, model, created_at, updated_at FROM chats WHERE updated_at > ? ORDER BY updated_at DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, lastSyncTime);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.CC_SWITCH);

                        String chatId = rs.getString("id");
                        session.setSessionId("ccswitch_" + chatId);
                        session.setOriginalUniqueId(chatId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        long createdAt = getTimestamp(rs, "created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        session.setContentPreview(extractFirstMessage(conn, chatId));
                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("增量解析 CC Switch chats 记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 CC Switch 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    private List<Session> parseFromConversationsTableIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (!tableExists(conn, "conversations")) {
                return sessions;
            }

            String sql = "SELECT id, title, model, created_at FROM conversations WHERE created_at > ? ORDER BY created_at DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, lastSyncTime);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.CC_SWITCH);

                        String convId = rs.getString("id");
                        session.setSessionId("ccswitch_" + convId);
                        session.setOriginalUniqueId(convId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        long createdAt = getTimestamp(rs, "created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        session.setContentPreview(extractFirstMessageFromConversations(conn, convId));
                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("增量解析 CC Switch conversations 记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 CC Switch 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    private List<Session> parseFromSessionsTableIncremental(long lastSyncTime) {
        List<Session> sessions = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (!tableExists(conn, "sessions")) {
                return sessions;
            }

            String sql = "SELECT id, title, model, created_at FROM sessions WHERE created_at > ? ORDER BY created_at DESC";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, lastSyncTime);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    try {
                        Session session = new Session();
                        session.setSourceType(SourceType.CC_SWITCH);

                        String sessionId = rs.getString("id");
                        session.setSessionId("ccswitch_" + sessionId);
                        session.setOriginalUniqueId(sessionId);
                        session.setTitle(rs.getString("title"));
                        session.setModelName(rs.getString("model"));

                        long createdAt = getTimestamp(rs, "created_at");
                        if (createdAt > 0) {
                            session.setCreateTime(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(createdAt),
                                ZoneId.systemDefault()
                            ));
                        }

                        sessions.add(session);
                    } catch (Exception e) {
                        System.err.println("增量解析 CC Switch sessions 记录失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("连接 CC Switch 数据库失败: " + e.getMessage());
        }

        return sessions;
    }

    /**
     * 提取第一条消息作为内容预览
     */
    private String extractFirstMessage(Connection conn, String chatId) {
        // 尝试从 messages 表读取
        try {
            String sql = "SELECT content FROM messages WHERE chat_id = ? ORDER BY created_at ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String content = rs.getString("content");
                    if (content != null && !content.isEmpty()) {
                        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    }
                }
            }
        } catch (SQLException e) {
            // 表可能不存在，忽略
        }
        return "";
    }

    private String extractFirstMessageFromConversations(Connection conn, String convId) {
        try {
            String sql = "SELECT content FROM messages WHERE conversation_id = ? ORDER BY created_at ASC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, convId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String content = rs.getString("content");
                    if (content != null && !content.isEmpty()) {
                        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    }
                }
            }
        } catch (SQLException e) {
            // 表可能不存在，忽略
        }
        return "";
    }

    /**
     * 读取完整对话消息
     */
    private String readMessagesFromDatabase(String chatId) {
        StringBuilder content = new StringBuilder();
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url)) {
            // 尝试不同的消息表结构
            String[] messageTables = {"messages", "chat_messages", "conversation_messages"};

            for (String table : messageTables) {
                if (tableExists(conn, table)) {
                    String idColumn = table.equals("chat_messages") ? "chat_id" : "conversation_id";
                    if (!columnExists(conn, table, idColumn)) {
                        idColumn = "session_id";
                    }

                    String sql = String.format("SELECT role, content FROM %s WHERE %s = ? ORDER BY created_at ASC", table, idColumn);

                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, chatId);
                        ResultSet rs = pstmt.executeQuery();

                        while (rs.next()) {
                            String role = rs.getString("role");
                            String message = rs.getString("content");

                            if (message != null && !message.isEmpty()) {
                                String roleDisplay = "user".equals(role) || "human".equals(role) ? "用户" : "AI";
                                content.append("[").append(roleDisplay).append("]\n");
                                content.append(message).append("\n\n");
                            }
                        }

                        if (content.length() > 0) {
                            break;
                        }
                    } catch (SQLException e) {
                        // 列名可能不匹配，继续尝试其他表
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("读取 CC Switch 会话消息失败: " + e.getMessage());
        }

        return content.toString();
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * 检查列是否存在
     */
    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 安全获取时间戳（支持秒和毫秒）
     */
    private long getTimestamp(ResultSet rs, String column) throws SQLException {
        long timestamp = rs.getLong(column);
        // 如果时间戳看起来是秒级（10位），转换为毫秒
        if (timestamp > 0 && timestamp < 10000000000L) {
            timestamp *= 1000;
        }
        return timestamp;
    }
}
