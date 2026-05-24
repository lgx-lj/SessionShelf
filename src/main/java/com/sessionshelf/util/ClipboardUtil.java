package com.sessionshelf.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * 剪贴板工具类
 * 提供文本复制功能
 */
public class ClipboardUtil {

    private ClipboardUtil() {}

    /**
     * 将文本复制到系统剪贴板
     */
    public static void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }
}
