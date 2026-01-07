package gui.window;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import core.entity.ChatMessage;
import core.entity.ChatSession;
import core.entity.Model;
import core.service.OpenAIService;
import core.util.ChatDAO;
import core.util.ModelDAO;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多聊天窗口，允许同时向多个模型发送消息。
 * 每个模型都有自己的面板，并并发处理流式响应。
 */
public class MultiChatWindow extends JFrame {

    static {
        FlatArcDarkIJTheme.setup();
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
    }

    private final List<Model> models;
    private final ChatDAO chatDAO;
    private final ModelDAO modelDAO;
    private final Map<String, ModelChatPanel> chatPanels = new HashMap<>();
    // 使用固定线程池以防止资源耗尽
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);
    private volatile boolean isWindowActive = true;
    
    // 每个会话的锁，用于细粒度同步（线程安全映射）
    private final Map<String, Object> sessionLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private final Parser mdParser;
    private final HtmlRenderer mdRenderer;

    private final JTextArea inputArea = new JTextArea(5, 40);
    private JButton btnSend;
    private JPanel modelsContainer;
    private JScrollPane modelsScrollPane;

    public MultiChatWindow(List<Model> models, ChatDAO chatDAO, ModelDAO modelDAO) {
        this.models = models;
        this.chatDAO = chatDAO;
        this.modelDAO = modelDAO;

        MutableDataSet mdOptions = new MutableDataSet();
        mdParser = Parser.builder(mdOptions).build();
        mdRenderer = HtmlRenderer.builder(mdOptions).build();

        setTitle("Multi-Chat - " + models.size() + " models");
        setSize(1400, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildInputPanel(), BorderLayout.SOUTH);

        // 为每个模型初始化聊天面板
        initializeChatPanels();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    private void initializeChatPanels() {
        for (Model model : models) {
            Model fullModel = modelDAO.getModel(model.getUuid());
            OpenAIService service = null;
            if (fullModel != null && fullModel.getApiKey() != null) {
                service = new OpenAIService(
                        fullModel.getBaseUrl(),
                        fullModel.getApiKey(),
                        fullModel.getModelName()
                );
            }

            // 为此模型创建或获取会话
            ChatSession session = createOrGetSession(model);
            
            ModelChatPanel panel = new ModelChatPanel(model, service, session);
            chatPanels.put(model.getUuid(), panel);
            modelsContainer.add(panel);
        }
        
        // 安装从模型面板到主滚动窗格的滚轮转发
        // 这允许在鼠标位于模型面板上方时滚动主容器
        for (ModelChatPanel panel : chatPanels.values()) {
            installWheelForwardingToMain(panel);
        }
    }
    
    /**
     * 当主滚动窗格需要滚动并且鼠标不在聊天消息滚动区域上方时，
     * 将鼠标滚轮事件从模型面板转发到主滚动窗格
     */
    private void installWheelForwardingToMain(Component comp) {
        if (comp == null || modelsScrollPane == null) return;
        
        MouseWheelListener forwarder = e -> {

            Component source = e.getComponent();
            boolean isOverChatScroll = false;

            while (source != null && source != comp) {
                if (source instanceof JScrollPane) {

                    if (source.getParent() instanceof ModelChatPanel) {
                        isOverChatScroll = true;
                        break;
                    }
                }
                source = source.getParent();
            }
            
       
            if (!isOverChatScroll) {
                JScrollBar hsb = modelsScrollPane.getHorizontalScrollBar();
                JScrollBar vsb = modelsScrollPane.getVerticalScrollBar();
                
                boolean canScrollHorizontally = hsb.isVisible() && hsb.getMaximum() > hsb.getVisibleAmount();
                boolean canScrollVertically = vsb.isVisible() && vsb.getMaximum() > vsb.getVisibleAmount();
                
                if (canScrollHorizontally || canScrollVertically) {
                    modelsScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(comp, e, modelsScrollPane));
                    e.consume();
                }
            }
        };
        comp.addMouseWheelListener(forwarder);
    }

    private ChatSession createOrGetSession(Model model) {
        // 为多模型对话创建一个新会话
        return chatDAO.createSession(model.getUuid(), "多模型对话");
    }

    private JPanel buildMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.putClientProperty("FlatLaf.style", "background:$Panel.background");

        // 创建头部
        JPanel header = buildHeader();
        mainPanel.add(header, BorderLayout.NORTH);

        // 为模型聊天面板创建可滚动容器
        modelsContainer = new JPanel();
        modelsContainer.setLayout(new GridLayout(1, models.size(), 10, 0));
        modelsContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        modelsScrollPane = new JScrollPane(modelsContainer);
        JScrollPane scrollPane = modelsScrollPane;
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // 以适当的速度启用鼠标滚轮滚动
        scrollPane.getVerticalScrollBar().setBlockIncrement(50);
        scrollPane.setWheelScrollingEnabled(true);

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        UIManager.getColor("Component.borderColor")),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel title = new JLabel("Multi-Chat: " + models.size() + " Models");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JLabel subtitle = new JLabel("Sending messages to all models simultaneously");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);

        header.add(titles, BorderLayout.CENTER);

        return header;
    }

    private JPanel buildInputPanel() {
        JPanel inputBox = new JPanel(new BorderLayout());
        inputBox.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        UIManager.getColor("Component.borderColor")),
                new EmptyBorder(8, 8, 8, 8)
        ));

        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.putClientProperty("FlatLaf.style",
                "background:$EditorPane.background;" +
                        "border:0,0,0,0;" +
                        "font:+1");
        
        // 绑定 Enter 发送消息，Shift+Enter 换行
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send-message");
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        inputArea.getActionMap().put("send-message", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onSendToAll();
            }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());

        btnSend = new JButton("Send to All");
        btnSend.putClientProperty("JButton.buttonType", "default");
        btnSend.addActionListener(e -> onSendToAll());

        JPanel sendBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        sendBox.setOpaque(false);
        sendBox.add(btnSend);

        inputBox.add(inputScroll, BorderLayout.CENTER);
        inputBox.add(sendBox, BorderLayout.SOUTH);

        return inputBox;
    }

    private void onSendToAll() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        setInputEnabled(false);
        String userMessage = text;
        inputArea.setText("");

        // 通过执行程序并发向所有模型发送
        // 每个 sendMessageAsync 调用都会创建一个独立的 CompletableFuture
        // 在执行程序服务上运行，确保真正的并行执行
        for (ModelChatPanel panel : chatPanels.values()) {
            panel.sendMessageAsync(userMessage, executorService);
        }
        
        // 立即重新启用输入以允许发送更多消息
        // 异步任务在后台独立运行
        setInputEnabled(true);
    }

    private void setInputEnabled(boolean enabled) {
        btnSend.setEnabled(enabled);
        inputArea.setEnabled(enabled);
    }

    private void cleanup() {
        isWindowActive = false;
        
        // 关闭所有 OpenAIService 实例以释放 HTTP 客户端资源
        for (ModelChatPanel panel : chatPanels.values()) {
            if (panel.openAIService != null) {
                panel.openAIService.shutdown();
            }
        }
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 多聊天窗口中单个模型的聊天面板
     */
    private class ModelChatPanel extends JPanel {
        private final Model model;
        // 包私有以允许从外部类清理
        final OpenAIService openAIService;
        private final ChatSession session;
        private final JPanel messagePanel;
        private JScrollPane messageScroll;
        // currentAssistantMessage 仅在单个消息发送操作期间访问
        // 线程安全：每个面板一次只处理一条消息，所有 UI
        // 更新都通过 SwingUtilities.invokeLater() 在 EDT 上序列化
        private JEditorPane currentAssistantMessage = null;

        public ModelChatPanel(Model model, OpenAIService service, ChatSession session) {
            this.model = model;
            this.openAIService = service;
            this.session = session;

            setLayout(new BorderLayout());
            setBorder(new LineBorder(UIManager.getColor("Component.borderColor"), 1, true));

            // 添加带有模型名称的头部
            add(buildModelHeader(), BorderLayout.NORTH);

            // 添加消息面板
            messagePanel = new JPanel();
            messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
            messagePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            messagePanel.putClientProperty("FlatLaf.style", "background:$EditorPane.background");

            messageScroll = new JScrollPane(messagePanel);
            messageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            messageScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            messageScroll.setBorder(BorderFactory.createEmptyBorder());
            messageScroll.getVerticalScrollBar().setUnitIncrement(16);
            
            // 以适当的速度启用鼠标滚轮滚动
            messageScroll.getVerticalScrollBar().setBlockIncrement(50);
            messageScroll.setWheelScrollingEnabled(true);
            
            messageScroll.getViewport().setOpaque(false);
            messageScroll.setOpaque(false);

            // 确保鼠标在面板任何部分上的滚轮都会滚动消息区域
            installWheelForwarding(this, messageScroll);

            add(messageScroll, BorderLayout.CENTER);

            // 如果 API 未配置，显示警告
            if (openAIService == null) {
                JLabel warning = new JLabel("<html><center>API未配置<br/>无法发送消息</center></html>");
                warning.setForeground(new Color(200, 150, 0));
                warning.setHorizontalAlignment(SwingConstants.CENTER);
                messagePanel.add(warning);
            }

            // 加载现有消息
            loadMessages();
        }

        private JPanel buildModelHeader() {
            JPanel header = new JPanel(new BorderLayout());
            header.setBorder(new CompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0,
                            UIManager.getColor("Component.borderColor")),
                    new EmptyBorder(8, 10, 8, 10)
            ));

            JLabel title = new JLabel(
                    model.getNickname().isEmpty()
                            ? model.getModelName()
                            : model.getNickname());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

            JLabel subtitle = new JLabel(model.getModelName());
            subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
            subtitle.setFont(subtitle.getFont().deriveFont(11f));

            JPanel titles = new JPanel();
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            titles.setOpaque(false);
            titles.add(title);
            titles.add(subtitle);

            header.add(titles, BorderLayout.CENTER);

            return header;
        }

        private void loadMessages() {
            messagePanel.removeAll();
            if (session == null) {
                return;
            }
            // 读取操作不需要同步，因为它们对于并发读取是安全的
            List<ChatMessage> messages = chatDAO.listMessages(session.getUuid());
            for (ChatMessage msg : messages) {
                addMessageBubble(msg);
            }
            messagePanel.add(Box.createVerticalGlue());
            revalidate();
            SwingUtilities.invokeLater(() ->
                    messageScroll.getVerticalScrollBar().setValue(Integer.MAX_VALUE));
        }

        public void sendMessageAsync(String userMessage, ExecutorService executor) {
            // 提交给执行程序以实现真正的并发执行
            CompletableFuture.runAsync(() -> sendMessageInternal(userMessage), executor);
        }

        private void sendMessageInternal(String userMessage) {
            if (!isWindowActive || openAIService == null) {
                return;
            }

            // 将用户消息保存到数据库（按会话同步，而不是全局）
            Object sessionLock = sessionLocks.computeIfAbsent(session.getUuid(), k -> new Object());
            synchronized (sessionLock) {
                chatDAO.addMessage(session.getUuid(), "user", userMessage, System.currentTimeMillis());
            }

            // 缓存消息历史记录以避免重复读取数据库
            List<ChatMessage> currentHistory = chatDAO.listMessages(session.getUuid());
            
            // 立即将用户消息添加到 UI
            SwingUtilities.invokeLater(() -> {
                if (!isWindowActive) return;
                if (!currentHistory.isEmpty()) {
                    ChatMessage userMsg = currentHistory.get(currentHistory.size() - 1);
                    addMessageBubble(userMsg);
                    messagePanel.revalidate();
                    messageScroll.getVerticalScrollBar().setValue(Integer.MAX_VALUE);
                }
                createStreamingAssistantBubble();
            });

            // 从缓存的历史记录准备 API 消息
            List<OpenAIService.ChatMessage> apiMessages = new ArrayList<>();
            for (ChatMessage msg : currentHistory) {
                apiMessages.add(new OpenAIService.ChatMessage(msg.getRole(), msg.getContent()));
            }

            // 用于流式更新的 StringBuilder（每个流式会话都有自己的实例）
            // 这是安全的，因为每个模型面板独立处理消息
            StringBuilder fullResponse = new StringBuilder();
            
            // 限制 UI 更新以避免使 EDT 不堪重负
            // 使用 AtomicLong 进行线程安全的时间戳跟踪
            AtomicLong lastUpdateTime = new AtomicLong(0);
            final int UPDATE_INTERVAL_MS = 50; // 最多每 50 毫秒更新一次 UI

            openAIService.chatCompletionStream(apiMessages, new OpenAIService.StreamCallback() {
                @Override
                public void onChunk(String content) {
                    fullResponse.append(content);
                    if (isWindowActive) {
                        long currentTime = System.currentTimeMillis();
                        // 限制更新以避免 EDT 过载（使用 AtomicLong 线程安全）
                        if (currentTime - lastUpdateTime.get() >= UPDATE_INTERVAL_MS) {
                            lastUpdateTime.set(currentTime);
                            String currentContent = fullResponse.toString();
                            SwingUtilities.invokeLater(() -> {
                                if (currentAssistantMessage != null && isWindowActive) {
                                    renderMarkdown(currentAssistantMessage, currentContent);
                                }
                            });
                        }
                    }
                }

                @Override
                public void onComplete() {
                    if (!isWindowActive) return;

                    String assistantResponse = fullResponse.toString();
                    
                    // 在后台保存到数据库
                    Object sessionLock = sessionLocks.computeIfAbsent(session.getUuid(), k -> new Object());
                    synchronized (sessionLock) {
                        chatDAO.addMessage(session.getUuid(), "assistant", assistantResponse, System.currentTimeMillis());
                    }

                    // 使用完整响应进行最终 UI 更新
                    SwingUtilities.invokeLater(() -> {
                        if (!isWindowActive) return;
                        
                        // 最终渲染以确保我们显示完整的响应
                        // 流式气泡保留为最后一条消息 - 无需重新加载所有消息
                        if (currentAssistantMessage != null) {
                            renderMarkdown(currentAssistantMessage, assistantResponse);
                        }
                        currentAssistantMessage = null;
                    });
                }

                @Override
                public void onError(Exception ex) {
                    if (!isWindowActive) return;

                    SwingUtilities.invokeLater(() -> {
                        if (!isWindowActive) return;
                        if (currentAssistantMessage != null) {
                            renderMarkdown(currentAssistantMessage,
                                    fullResponse.toString() + "\n\n[Error: " + ex.getMessage() + "]");
                        }
                        currentAssistantMessage = null;
                    });
                }
            });
        }

        private void addMessageBubble(ChatMessage msg) {
            boolean isUser = "user".equalsIgnoreCase(msg.getRole());

            JPanel line = new JPanel();
            line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
            line.setOpaque(false);

            JPanel bubble = new JPanel(new BorderLayout());
            bubble.setBackground(isUser
                    ? UIManager.getColor("Button.default.background")
                    : UIManager.getColor("Panel.background"));
            bubble.setBorder(new CompoundBorder(
                    new LineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                    new EmptyBorder(6, 10, 6, 10)
            ));

            JEditorPane textPane = createMarkdownPane(msg.getContent());
            applyBubbleWidth(bubble, textPane);
            bubble.add(textPane, BorderLayout.CENTER);

            if (isUser) {
                line.add(Box.createHorizontalGlue());
                line.add(bubble);
            } else {
                line.add(bubble);
                line.add(Box.createHorizontalGlue());
            }

            line.setBorder(new EmptyBorder(4, 4, 4, 4));
            messagePanel.add(line);
            messagePanel.add(Box.createVerticalStrut(4));
        }

        private void createStreamingAssistantBubble() {
            JPanel line = new JPanel();
            line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
            line.setOpaque(false);

            JPanel bubble = new JPanel(new BorderLayout());
            bubble.setBackground(UIManager.getColor("Panel.background"));
            bubble.setBorder(new CompoundBorder(
                    new LineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                    new EmptyBorder(6, 10, 6, 10)
            ));

            currentAssistantMessage = createMarkdownPane("正在思考...");
            applyBubbleWidth(bubble, currentAssistantMessage);
            bubble.add(currentAssistantMessage, BorderLayout.CENTER);

            line.add(bubble);
            line.add(Box.createHorizontalGlue());

            line.setBorder(new EmptyBorder(4, 4, 4, 4));
            messagePanel.add(line);
            messagePanel.add(Box.createVerticalStrut(4));

            messagePanel.revalidate();
            SwingUtilities.invokeLater(() ->
                    messageScroll.getVerticalScrollBar().setValue(Integer.MAX_VALUE));
        }

        private JEditorPane createMarkdownPane(String content) {
            JEditorPane pane = new JEditorPane();
            pane.setContentType("text/html");
            pane.setEditable(false);
            pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            pane.setOpaque(false);
            pane.setBorder(new EmptyBorder(0, 0, 0, 0));
            renderMarkdown(pane, content);
            return pane;
        }

        private void renderMarkdown(JEditorPane pane, String content) {
            String safe = content == null ? "" : content;
            String html = mdRenderer.render(mdParser.parse(safe));

            Font labelFont = UIManager.getFont("Label.font");
            String fontFamily = labelFont != null ? labelFont.getFamily() : "SansSerif";

            String body = "<html><head><style>" +
                    "body{margin:0;padding:0;font-family:" + fontFamily + ";font-size:12px;overflow-wrap:break-word;word-wrap:break-word;word-break:break-word;}" +
                    "p{margin:0 0 4px 0;}" +
                    "ul,ol{margin:0 0 4px 12px;}" +
                    "pre{margin:4px 0;padding:4px;background:" + toRgb(UIManager.getColor("Panel.background")) + ";border-radius:4px;white-space:pre-wrap;word-wrap:break-word;overflow-wrap:break-word;font-size:11px;}" +
                    "code{font-family:monospace;font-size:11px;}" +
                    "</style></head><body>" + html + "</body></html>";
            pane.setText(body);
            pane.setCaretPosition(0);
            

            if (pane.getParent() instanceof JPanel bubble) {
                applyBubbleWidth(bubble, pane);

                messagePanel.revalidate();
                messagePanel.repaint();

                if (pane == currentAssistantMessage) {
                    SwingUtilities.invokeLater(() -> {
                        messageScroll.getVerticalScrollBar().setValue(Integer.MAX_VALUE);
                    });
                }
            }
        }

        private String toRgb(Color c) {
            if (c == null) return "#f0f0f0";
            return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }

        private void applyBubbleWidth(JPanel bubble, JEditorPane content) {
            int maxW = Math.max(180, getWidth() - 60);
            bubble.setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));

            Insets bubbleInsets = bubble.getBorder() != null ? bubble.getBorder().getBorderInsets(bubble) : new Insets(0, 0, 0, 0);
            int horizontalPadding = bubbleInsets.left + bubbleInsets.right;
            int contentW = Math.max(120, maxW - horizontalPadding);

            content.setSize(new Dimension(contentW, Integer.MAX_VALUE));

            View view = content.getUI().getRootView(content);
            if (view != null) {
                view.setSize(contentW, Integer.MAX_VALUE);
                int prefH = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
                content.setPreferredSize(new Dimension(contentW, prefH));
            } else {
                Dimension pref = content.getPreferredSize();
                pref.width = contentW;
                content.setPreferredSize(pref);
            }

            bubble.revalidate();
        }
    }

    private void installWheelForwarding(Component comp, JScrollPane target) {
        if (comp == null || target == null) return;

        MouseWheelListener forwarder = e -> {
            if (!target.isWheelScrollingEnabled()) return;
            // Create a new MouseWheelEvent with converted coordinates to avoid ClassCastException
            Point p = SwingUtilities.convertPoint(comp, e.getPoint(), target);
            MouseWheelEvent newEvent = new MouseWheelEvent(
                    target,
                    e.getID(),
                    e.getWhen(),
                    e.getModifiersEx(),
                    p.x,
                    p.y,
                    e.getClickCount(),
                    e.isPopupTrigger(),
                    e.getScrollType(),
                    e.getScrollAmount(),
                    e.getWheelRotation()
            );
            target.dispatchEvent(newEvent);
            e.consume();
        };
        comp.addMouseWheelListener(forwarder);

        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                installWheelForwarding(child, target);
            }

            // Add container listener to the view of the scroll pane (messagePanel) to handle dynamic components
            if (target.getViewport() != null && container == target.getViewport().getView()) {
                container.addContainerListener(new java.awt.event.ContainerAdapter() {
                    @Override
                    public void componentAdded(java.awt.event.ContainerEvent e) {
                        installWheelForwarding(e.getChild(), target);
                    }
                });
            }
        }
    }
}
