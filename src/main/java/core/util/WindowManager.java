package core.util;

import gui.window.ChatWindow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages open ChatWindow instances to prevent duplicate windows for the same model
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
     * Register a chat window for a model
     * @param modelUuid The model UUID
     * @param window The chat window
     */
    public void registerWindow(String modelUuid, ChatWindow window) {
        openWindows.put(modelUuid, window);
    }

    /**
     * Unregister a chat window when it closes
     * @param modelUuid The model UUID
     */
    public void unregisterWindow(String modelUuid) {
        openWindows.remove(modelUuid);
    }

    /**
     * Get the open window for a model, if it exists
     * @param modelUuid The model UUID
     * @return The chat window, or null if not open
     */
    public ChatWindow getWindow(String modelUuid) {
        return openWindows.get(modelUuid);
    }

    /**
     * Check if a window is open for a model
     * @param modelUuid The model UUID
     * @return true if a window is open, false otherwise
     * @implNote There's a small race condition where the window could be closed between
     *           the check and subsequent operations. Callers should handle this gracefully.
     */
    public boolean hasWindow(String modelUuid) {
        ChatWindow window = openWindows.get(modelUuid);
        return window != null && window.isVisible();
    }

    /**
     * Focus the window for a model if it's open
     * @param modelUuid The model UUID
     * @return true if the window was focused, false if no window was open
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
