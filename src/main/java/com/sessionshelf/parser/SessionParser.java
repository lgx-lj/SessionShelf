package com.sessionshelf.parser;

import com.sessionshelf.model.Session;
import java.util.List;

/**
 * 会话解析器接口
 * 定义不同数据源的解析规范
 */
public interface SessionParser {

    /**
     * 解析所有会话
     * @return 会话列表
     */
    List<Session> parseAll();

    /**
     * 增量解析新增会话
     * @param lastSyncTime 上次同步时间
     * @return 新增的会话列表
     */
    List<Session> parseIncremental(long lastSyncTime);

    /**
     * 读取单个会话的完整对话内容
     * @param session 会话对象
     * @return 完整对话内容
     */
    String readFullContent(Session session);

    /**
     * 检查数据源是否可用
     * @return true 表示数据源存在且可读
     */
    boolean isAvailable();
}
