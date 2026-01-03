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
 * Multi-chat window that allows sending messages to multiple models simultaneously.
 * Each model gets its own panel with streaming responses handled concurrently.
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
    // Use fixed thread pool to prevent resource exhaustion
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);
    private volatile boolean isWindowActive = true;
    
    // Per-session locks for fine-grained synchronization (thread-safe map)
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

        // Initialize chat panels for each model
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

            // Create or get session for this model
            ChatSession session = createOrGetSession(model);
            
            ModelChatPanel panel = new ModelChatPanel(model, service, session);
            chatPanels.put(model.getUuid(), panel);
            modelsContainer.add(panel);
        }
        
        // Install wheel forwarding from model panels to main scroll pane
        // This allows scrolling the main container when mouse is over model panels
        for (ModelChatPanel panel : chatPanels.values()) {
            installWheelForwardingToMain(panel);
        }
    }
    
    /**
     * Forwards mouse wheel events from model panels to the main scroll pane
     * when the main scroll pane needs scrolling and the mouse is not over a chat message scroll area
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
        // Create a new session for multi-chat
        return chatDAO.createSession(model.getUuid(), "多模型对话");
    }

    private JPanel buildMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.putClientProperty("FlatLaf.style", "background:$Panel.background");

        // Create header
        JPanel header = buildHeader();
        mainPanel.add(header, BorderLayout.NORTH);

        // Create scrollable container for model chat panels
        modelsContainer = new JPanel();
        modelsContainer.setLayout(new GridLayout(1, models.size(), 10, 0));
        modelsContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        modelsScrollPane = new JScrollPane(modelsContainer);
        JScrollPane scrollPane = modelsScrollPane;
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // Enable mouse wheel scrolling with proper speed
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
        
        // Bind Enter to send message, Shift+Enter for new line
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

        // Send to all models concurrently via executor
        // Each sendMessageAsync call creates an independent CompletableFuture
        // that runs on the executor service, ensuring true parallel execution
        for (ModelChatPanel panel : chatPanels.values()) {
            panel.sendMessageAsync(userMessage, executorService);
        }
        
        // Re-enable input immediately to allow sending more messages
        // The async tasks run independently in background
        setInputEnabled(true);
    }

    private void setInputEnabled(boolean enabled) {
        btnSend.setEnabled(enabled);
        inputArea.setEnabled(enabled);
    }

    private void cleanup() {
        isWindowActive = false;
        
        // Shutdown all OpenAIService instances to release HTTP client resources
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
     * Panel for a single model's chat within the multi-chat window
     */
    private class ModelChatPanel extends JPanel {
        private final Model model;
        // Package-private to allow cleanup from outer class
        final OpenAIService openAIService;
        private final ChatSession session;
        private final JPanel messagePanel;
        private JScrollPane messageScroll;
        // currentAssistantMessage is only accessed during a single message send operation
        // Thread safety: Only one message is processed at a time per panel, and all UI
        // updates go through SwingUtilities.invokeLater() which serializes on EDT
        private JEditorPane currentAssistantMessage = null;

        public ModelChatPanel(Model model, OpenAIService service, ChatSession session) {
            this.model = model;
            this.openAIService = service;
            this.session = session;

            setLayout(new BorderLayout());
            setBorder(new LineBorder(UIManager.getColor("Component.borderColor"), 1, true));

            // Add header with model name
            add(buildModelHeader(), BorderLayout.NORTH);

            // Add message panel
            messagePanel = new JPanel();
            messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
            messagePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
            messagePanel.putClientProperty("FlatLaf.style", "background:$EditorPane.background");

            messageScroll = new JScrollPane(messagePanel);
            messageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            messageScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            messageScroll.setBorder(BorderFactory.createEmptyBorder());
            messageScroll.getVerticalScrollBar().setUnitIncrement(16);
            
            // Enable mouse wheel scrolling with proper speed
            messageScroll.getVerticalScrollBar().setBlockIncrement(50);
            messageScroll.setWheelScrollingEnabled(true);
            
            messageScroll.getViewport().setOpaque(false);
            messageScroll.setOpaque(false);

            // Ensure mouse wheel over any part of this panel scrolls the message area
            installWheelForwarding(this, messageScroll);

            add(messageScroll, BorderLayout.CENTER);

            // Show warning if API not configured
            if (openAIService == null) {
                JLabel warning = new JLabel("<html><center>API未配置<br/>无法发送消息</center></html>");
                warning.setForeground(new Color(200, 150, 0));
                warning.setHorizontalAlignment(SwingConstants.CENTER);
                messagePanel.add(warning);
            }

            // Load existing messages
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
            // Read operations don't need synchronization as they're safe for concurrent reads
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
            // Submit to executor for true concurrent execution
            CompletableFuture.runAsync(() -> sendMessageInternal(userMessage), executor);
        }

        private void sendMessageInternal(String userMessage) {
            if (!isWindowActive || openAIService == null) {
                return;
            }

            // Save user message to database (synchronized per session, not globally)
            Object sessionLock = sessionLocks.computeIfAbsent(session.getUuid(), k -> new Object());
            synchronized (sessionLock) {
                chatDAO.addMessage(session.getUuid(), "user", userMessage, System.currentTimeMillis());
            }

            // Cache the message history to avoid repeated DB reads
            List<ChatMessage> currentHistory = chatDAO.listMessages(session.getUuid());
            
            // Add user message to UI immediately
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

            // Prepare API messages from cached history
            List<OpenAIService.ChatMessage> apiMessages = new ArrayList<>();
            for (ChatMessage msg : currentHistory) {
                apiMessages.add(new OpenAIService.ChatMessage(msg.getRole(), msg.getContent()));
            }

            // StringBuilder for streaming updates (each streaming session has its own instance)
            // This is safe because each model panel processes messages independently
            StringBuilder fullResponse = new StringBuilder();
            
            // Throttle UI updates to avoid overwhelming EDT
            // Use AtomicLong for thread-safe timestamp tracking
            AtomicLong lastUpdateTime = new AtomicLong(0);
            final int UPDATE_INTERVAL_MS = 50; // Update UI at most every 50ms
            
            openAIService.chatCompletionStream(apiMessages, new OpenAIService.StreamCallback() {
                @Override
                public void onChunk(String content) {
                    fullResponse.append(content);
                    if (isWindowActive) {
                        long currentTime = System.currentTimeMillis();
                        // Throttle updates to avoid EDT overload (thread-safe with AtomicLong)
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
                    
                    // Save to database in background
                    Object sessionLock = sessionLocks.computeIfAbsent(session.getUuid(), k -> new Object());
                    synchronized (sessionLock) {
                        chatDAO.addMessage(session.getUuid(), "assistant", assistantResponse, System.currentTimeMillis());
                    }

                    // Final UI update with complete response
                    SwingUtilities.invokeLater(() -> {
                        if (!isWindowActive) return;
                        
                        // Final render to ensure we show the complete response
                        // The streaming bubble stays as the final message - no need to reload all messages
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
                

                bubble.revalidate();
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
