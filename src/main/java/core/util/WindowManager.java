package core.util;

import gui.window.ChatWindow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理打开的聊天窗口实例，以防止同一模型出现重复窗口
 */
public class WindowManager {
    private static final WindowManager instance = new WindowManager();
    private final Map<String, ChatWindow> openWindows = new ConcurrentHashMap<>();

    private WindowManager() {
    }

    public static WindowManager getInstance() {
        return instance;
    }

    /**
     * 为模型注册一个聊天窗口
     * @param modelUuid 模型 UUID
     * @param window 聊天窗口
     */
    public void registerWindow(String modelUuid, ChatWindow window) {
        openWindows.put(modelUuid, window);
    }

    /**
     * 在聊天窗口关闭时注销它
     * @param modelUuid 模型 UUID
     */
    public void unregisterWindow(String modelUuid) {
        openWindows.remove(modelUuid);
    }

    /**
     * 获取模型的打开窗口（如果存在）
     * @param modelUuid 模型 UUID
     * @return 聊天窗口，如果未打开则返回 null
     */
    public ChatWindow getWindow(String modelUuid) {
        return openWindows.get(modelUuid);
    }

    /**
     * 检查模型是否打开了窗口
     * @param modelUuid 模型 UUID
     * @return 如果窗口已打开返回 true，否则返回 false
     * @implNote 在检查和后续操作之间，窗口可能会关闭，存在小的竞争条件。调用者应优雅地处理这种情况。
     */
    public boolean hasWindow(String modelUuid) {
        ChatWindow window = openWindows.get(modelUuid);
        return window != null && window.isVisible();
    }

    /**
     * 如果模型的窗口已打开，则聚焦该窗口
     * @param modelUuid 模型 UUID
     * @return 如果窗口被聚焦返回 true，如果没有打开的窗口返回 false
     */
    public boolean focusWindow(String modelUuid) {
        final boolean[] focused = {false};
        openWindows.computeIfPresent(modelUuid, (id, window) -> {
            if (window != null && window.isVisible()) {
                window.toFront();
                window.requestFocus();
                focused[0] = true;
            }
            return window;
        });
        return focused[0];
    }
}
