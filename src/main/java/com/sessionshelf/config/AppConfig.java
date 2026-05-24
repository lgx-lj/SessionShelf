package com.sessionshelf.config;

import java.io.*;
import java.util.Properties;

/**
 * 应用程序配置管理
 * 负责读写配置文件
 */
public class AppConfig {

    private static AppConfig instance;
    private Properties properties;
    private static final String CONFIG_DIR = "SessionShelf";
    private static final String CONFIG_FILE = "config.properties";
    private String configPath;

    private AppConfig() {
        properties = new Properties();
        configPath = getConfigFilePath();
        load();
    }

    /**
     * 获取单例实例
     */
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    /**
     * 获取配置文件路径
     */
    private String getConfigFilePath() {
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, CONFIG_FILE).getAbsolutePath();
    }

    /**
     * 加载配置文件
     */
    public void load() {
        File file = new File(configPath);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("加载配置文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 保存配置文件
     */
    public void save() {
        try (FileOutputStream fos = new FileOutputStream(configPath)) {
            properties.store(fos, "SessionShelf Configuration");
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取字符串配置
     */
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * 设置字符串配置
     */
    public void setString(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * 获取整数配置
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 设置整数配置
     */
    public void setInt(String key, int value) {
        properties.setProperty(key, String.valueOf(value));
    }

    /**
     * 获取布尔配置
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    /**
     * 设置布尔配置
     */
    public void setBoolean(String key, boolean value) {
        properties.setProperty(key, String.valueOf(value));
    }

    // 配置项常量
    public static final String CC_SWITCH_DB_PATH = "ccswitch.db.path";
    public static final String WINDOW_WIDTH = "window.width";
    public static final String WINDOW_HEIGHT = "window.height";
    public static final String LAST_SYNC_TIME = "last.sync.time";
}
