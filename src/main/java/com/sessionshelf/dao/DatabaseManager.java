package com.sessionshelf.dao;

import java.sql.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 数据库管理器 - 单例模式
 * 负责 SQLite 数据库连接管理和表结构初始化
 */
public class DatabaseManager {

    private static DatabaseManager instance;
    private Connection connection;
    private static final String DB_DIR = "SessionShelf";
    private static final String DB_NAME = "session_shelf.db";

    private DatabaseManager() {}

    /**
     * 获取单例实例
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * 初始化数据库连接并创建表结构
     */
    public void initialize() throws SQLException {
        String dbPath = getDatabasePath();
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        createTables();
    }

    /**
     * 获取数据库文件路径
     * Windows: C:\Users\{username}\SessionShelf\session_shelf.db
     * macOS: /Users/{username}/SessionShelf/session_shelf.db
     */
    private String getDatabasePath() {
        String userHome = System.getProperty("user.home");
        Path dbDir = Paths.get(userHome, DB_DIR);
        // 确保目录存在
        dbDir.toFile().mkdirs();
        return dbDir.resolve(DB_NAME).toString();
    }

    /**
     * 创建所有数据表
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 统一会话基础表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS session_base (
                    session_id TEXT PRIMARY KEY,
                    source_type INTEGER NOT NULL,
                    original_unique_id TEXT,
                    title TEXT,
                    model_name TEXT,
                    create_time TIMESTAMP,
                    content_preview TEXT,
                    source_file_path TEXT
                )
            """);

            // 文件夹目录表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS folder (
                    folder_id TEXT PRIMARY KEY,
                    folder_name TEXT NOT NULL,
                    parent_folder_id TEXT,
                    sort_order INTEGER DEFAULT 0
                )
            """);

            // 会话-目录关联表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS session_folder_rel (
                    session_id TEXT,
                    folder_id TEXT,
                    PRIMARY KEY(session_id, folder_id)
                )
            """);

            // 自定义标签表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tag (
                    tag_id TEXT PRIMARY KEY,
                    tag_name TEXT UNIQUE NOT NULL
                )
            """);

            // 会话-标签关联表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS session_tag_rel (
                    session_id TEXT,
                    tag_id TEXT,
                    PRIMARY KEY(session_id, tag_id)
                )
            """);

            // 创建默认"未归档"文件夹
            stmt.execute("""
                INSERT OR IGNORE INTO folder (folder_id, folder_name, parent_folder_id, sort_order)
                VALUES ('default', '未归档', NULL, 0)
            """);

            // 兼容旧数据库：添加 working_directory 列
            try {
                stmt.execute("ALTER TABLE session_base ADD COLUMN working_directory TEXT");
            } catch (SQLException ignored) {}

            // 兼容旧数据库：添加 last_resume_time 列
            try {
                stmt.execute("ALTER TABLE session_base ADD COLUMN last_resume_time TIMESTAMP");
            } catch (SQLException ignored) {}


            // 收藏会话表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS session_favorite (
                    session_id TEXT PRIMARY KEY
                )
            """);

            // 收藏项目表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS project_favorite (
                    project_name TEXT PRIMARY KEY
                )
            """);
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
