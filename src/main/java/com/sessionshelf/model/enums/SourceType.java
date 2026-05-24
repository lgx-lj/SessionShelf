package com.sessionshelf.model.enums;

/**
 * 数据源类型枚举
 * 标识会话来源于哪个 AI 工具
 */
public enum SourceType {
    CLAUDE_CODE(1, "Claude Code"),
    OPENAI_CODEX(2, "OpenAI Codex"),
    CC_SWITCH(3, "CC Switch");

    private final int code;
    private final String displayName;

    SourceType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据代码获取枚举值
     */
    public static SourceType fromCode(int code) {
        for (SourceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的数据源类型代码: " + code);
    }
}
