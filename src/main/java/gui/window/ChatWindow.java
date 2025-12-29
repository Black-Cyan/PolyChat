package gui.window;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;

import core.entity.ChatMessage;
import core.entity.ChatSession;
import core.entity.Model;
import core.util.ChatDAO;
import core.util.WindowManager;
import core.util.ModelDAO;
import core.service.OpenAIService;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChatWindow extends JFrame {

    // ===================== 基础配置 =====================

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

    private final Model model;
    private final ChatDAO chatDAO;
    private final ModelDAO modelDAO;
    private OpenAIService openAIService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile boolean isWindowActive = true;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("MM-dd HH:mm");

    private final DefaultListModel<ChatSession> sessionListModel =
            new DefaultListModel<>();
    private final JList<ChatSession> sessionList =
            new JList<>(sessionListModel);

    private final Parser mdParser;
    private final HtmlRenderer mdRenderer;

    private final JPanel messagePanel = new JPanel();
    private JScrollPane messageScroll;
    private final JTextArea inputArea = new JTextArea(3, 40);
    private JButton btnSend;

    private ChatSession currentSession;
    private JEditorPane currentAssistantMessage = null;

    // ===================== 构造函数 =====================

    public ChatWindow(Model model, ChatDAO chatDAO, ModelDAO modelDAO) {
        this.model = model;
        this.chatDAO = chatDAO;
        this.modelDAO = modelDAO;

        MutableDataSet mdOptions = new MutableDataSet();
        mdParser = Parser.builder(mdOptions).build();
        mdRenderer = HtmlRenderer.builder(mdOptions).build();

        // Get full model with API key
        Model fullModel = modelDAO.getModel(model.getUuid());
        if (fullModel != null && fullModel.getApiKey() != null) {
            this.openAIService = new OpenAIService(
                    fullModel.getBaseUrl(),
                    fullModel.getApiKey(),
                    fullModel.getModelName()
            );
        }

        setTitle("Chat - " +
                (model.getNickname().isEmpty()
                        ? model.getModelName()
                        : model.getNickname()));

        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        add(buildSessionPane(), BorderLayout.WEST);
        add(buildChatPane(), BorderLayout.CENTER);
        
        // Show warning banner if API is not configured
        if (openAIService == null) {
            JPanel warningPanel = getWarningPanel();
            add(warningPanel, BorderLayout.NORTH);
            
            // Disable input area
            inputArea.setEnabled(false);
            btnSend.setEnabled(false);
        }

        // keep bubble widths in sync with viewport size
        messageScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                updateBubbleWidths();
            }
        });

        loadSessions();

        // Register this window with the WindowManager
        WindowManager.getInstance().registerWindow(model.getUuid(), this);

        // Unregister and cleanup when closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                isWindowActive = false;
                WindowManager.getInstance().unregisterWindow(model.getUuid());
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
        });
    }

    private static @NotNull JPanel getWarningPanel() {
        JPanel warningPanel = new JPanel(new BorderLayout());
        warningPanel.setBackground(new Color(255, 200, 0, 30));
        warningPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        new Color(255, 200, 0)),
                new EmptyBorder(8, 12, 8, 12)
        ));
        JLabel warningLabel = new JLabel("API配置未完成，无法发送消息。请在编辑模型中配置API Key。");
        warningLabel.setForeground(new Color(200, 150, 0));
        warningPanel.add(warningLabel, BorderLayout.CENTER);
        return warningPanel;
    }

    // ===================== 左侧会话栏 =====================

    private Component buildSessionPane() {
        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(280, 0));
        left.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(
                        0, 0, 0, 1,
                        UIManager.getColor("Component.borderColor")),
                new EmptyBorder(12, 12, 12, 12)
        ));
        left.putClientProperty("FlatLaf.style",
                "background:darken($Panel.background,2%)");

        JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
        actions.setOpaque(false);
        JButton btnNew = new JButton("新建");
        btnNew.putClientProperty("JButton.buttonType", "default");
        btnNew.addActionListener(this::onNewSession);

        JButton btnDelete = new JButton("删除");
        btnDelete.putClientProperty("JButton.buttonType", "borderless");
        btnDelete.addActionListener(e -> onDeleteSession());

        actions.add(btnNew);
        actions.add(btnDelete);
        left.add(actions, BorderLayout.NORTH);

        sessionList.setCellRenderer(new SessionRenderer());
        sessionList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        sessionList.setFixedCellHeight(50);
        sessionList.setOpaque(false);
        sessionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                currentSession = sessionList.getSelectedValue();
                loadMessages();
            }
        });

        JScrollPane sp = new JScrollPane(sessionList);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);

        left.add(sp, BorderLayout.CENTER);
        return left;
    }

    // ===================== 中央聊天区 =====================

    private Component buildChatPane() {
        JPanel center = new JPanel(new BorderLayout());
        center.putClientProperty("FlatLaf.style",
                "background:$Panel.background");

        center.add(buildChatHeader(), BorderLayout.NORTH);

        messagePanel.setLayout(
                new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBorder(
                new EmptyBorder(16, 16, 16, 16));
        messagePanel.putClientProperty("FlatLaf.style",
                "background:$EditorPane.background");

        messageScroll = new JScrollPane(messagePanel);
        messageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        messageScroll.setBorder(BorderFactory.createEmptyBorder());
        messageScroll.getVerticalScrollBar().setUnitIncrement(18);
        messageScroll.getViewport().setOpaque(false);
        messageScroll.setOpaque(false);

        center.add(messageScroll, BorderLayout.CENTER);
        center.add(buildInputBox(), BorderLayout.SOUTH);

        return center;
    }

    private JComponent buildChatHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(
                        0, 0, 1, 0,
                        UIManager.getColor("Component.borderColor")),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel title = new JLabel(
                model.getNickname().isEmpty()
                        ? model.getModelName()
                        : model.getNickname());
        title.setFont(title.getFont().deriveFont(
                Font.BOLD, 16f));

        JLabel subtitle = new JLabel(model.getModelName());
        subtitle.setForeground(
                UIManager.getColor("Label.disabledForeground"));

        JPanel titles = new JPanel();
        titles.setLayout(
                new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(subtitle);

        header.add(titles, BorderLayout.CENTER);

        return header;
    }

    private void onDeleteSession() {
        if (currentSession == null) {
            JOptionPane.showMessageDialog(this, "请选择要删除的会话");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "确认删除当前会话并清空聊天记录？",
                "删除会话",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            chatDAO.deleteSession(currentSession.getUuid());
            loadSessions();
            if (sessionListModel.isEmpty()) {
                currentSession = null;
            } else {
                sessionList.setSelectedIndex(0);
            }
            messagePanel.removeAll();
            messagePanel.revalidate();
            messagePanel.repaint();
        }
    }

    // ===================== 输入区 =====================

    private Component buildInputBox() {
        JPanel inputBox = new JPanel(new BorderLayout());
        inputBox.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(
                        1, 0, 0, 0,
                        UIManager.getColor("Component.borderColor")),
                new EmptyBorder(8, 8, 8, 8)
        ));

        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.putClientProperty("FlatLaf.style",
                "background:$EditorPane.background;" +
                        "border:0,0,0,0;" +
                        "font:+1");

        JScrollPane inputScroll =
                new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());

        btnSend = new JButton("Send");
        btnSend.putClientProperty(
                "JButton.buttonType", "default");
        btnSend.addActionListener(this::onSend);

        JPanel sendBox = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 0, 0));
        sendBox.setOpaque(false);
        sendBox.add(btnSend);

        inputBox.add(inputScroll, BorderLayout.CENTER);
        inputBox.add(sendBox, BorderLayout.SOUTH);

        return inputBox;
    }

    // ===================== 逻辑 =====================

    private void onNewSession(ActionEvent e) {
        ChatSession session =
                chatDAO.createSession(
                        model.getUuid(), "新聊天");
        if (session != null) {
            sessionListModel.add(0, session);
            sessionList.setSelectedIndex(0);
        }
    }

    private void loadSessions() {
        sessionListModel.clear();
        List<ChatSession> sessions =
                chatDAO.listSessions(model.getUuid());
        for (ChatSession s : sessions) {
            sessionListModel.addElement(s);
        }
        if (!sessions.isEmpty()) {
            sessionList.setSelectedIndex(0);
        }
    }

    private void loadMessages() {
        messagePanel.removeAll();
        if (currentSession == null) {
            repaint();
            return;
        }
        for (ChatMessage msg :
                chatDAO.listMessages(currentSession.getUuid())) {
            addMessageBubble(msg);
        }
        messagePanel.add(Box.createVerticalGlue());
        revalidate();
        SwingUtilities.invokeLater(() ->
                messageScroll.getVerticalScrollBar()
                        .setValue(Integer.MAX_VALUE));
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

        line.setBorder(new EmptyBorder(6, 6, 6, 6));
        messagePanel.add(line);
        messagePanel.add(Box.createVerticalStrut(6));
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
        String body = "<html><head><style>" +
                "body{margin:0;padding:0;font-family:" + UIManager.getFont("Label.font").getFamily() + ";overflow-wrap:break-word;word-wrap:break-word;word-break:break-word;}" +
                "p{margin:0 0 4px 0;}" +
                "ul,ol{margin:0 0 4px 18px;}" +
                "pre{margin:4px 0;padding:6px;background:" + toRgb(UIManager.getColor("Panel.background")) + ";border-radius:6px;white-space:pre-wrap;word-wrap:break-word;overflow-wrap:break-word;}" +
                "code{font-family:monospace;}" +
                "</style></head><body>" + html + "</body></html>";
        pane.setText(body);
        pane.setCaretPosition(0);
    }

    private static String toRgb(Color c) {
        if (c == null) return "#f0f0f0";
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private int getBubbleMaxWidth() {
        if (messageScroll != null && messageScroll.getViewport() != null) {
            int vw = messageScroll.getViewport().getWidth();
            if (vw > 0) {
                return Math.max(220, vw - 60);
            }
        }
        return 800;
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

        line.setBorder(new EmptyBorder(6, 6, 6, 6));
        messagePanel.add(line);
        messagePanel.add(Box.createVerticalStrut(6));

        messagePanel.revalidate();
        SwingUtilities.invokeLater(() ->
                messageScroll.getVerticalScrollBar()
                        .setValue(Integer.MAX_VALUE));
    }

    private void updateBubbleWidths() {
        int maxW = getBubbleMaxWidth();
        for (Component comp : messagePanel.getComponents()) {
            if (comp instanceof JPanel line) {
                for (Component child : line.getComponents()) {
                    if (child instanceof JPanel bubble) {
                        Component center = ((BorderLayout) bubble.getLayout()).getLayoutComponent(BorderLayout.CENTER);
                        if (center instanceof JEditorPane pane) {
                            applyBubbleWidth(bubble, pane, maxW);
                        }
                    }
                }
            }
        }
        messagePanel.revalidate();
        messagePanel.repaint();
    }

    private void applyBubbleWidth(JPanel bubble, JEditorPane content) {
        applyBubbleWidth(bubble, content, getBubbleMaxWidth());
    }

    private void applyBubbleWidth(JPanel bubble, JEditorPane content, int maxW) {
        bubble.setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));
        int contentW = Math.max(180, maxW - 24); // subtract bubble padding

        content.setSize(new Dimension(contentW, Integer.MAX_VALUE));

        // Use View to calculate precise height for the given width
        View view = content.getUI().getRootView(content);
        if (view != null) {
            view.setSize(contentW, Integer.MAX_VALUE);
            int prefH = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
            content.setPreferredSize(new Dimension(contentW, prefH));
        } else {
            // Fallback if View is not ready
            Dimension pref = content.getPreferredSize();
            pref.width = contentW;
            content.setPreferredSize(pref);
        }

        bubble.revalidate();
    }

    private void generateTitle(ChatSession session, String userMsg, String assistantMsg) {
        executorService.submit(() -> {
            try {
                List<OpenAIService.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIService.ChatMessage("system", "You are a helpful assistant. Generate a short, concise title (max 10 words) for the following conversation. Do not use quotes."));
                messages.add(new OpenAIService.ChatMessage("user", "User: " + userMsg + "\nAssistant: " + assistantMsg));

                String title = openAIService.chatCompletion(messages);
                if (title != null && !title.isEmpty()) {
                    title = title.trim().replace("\"", "");
                    chatDAO.updateSessionTitle(session.getUuid(), title);
                    session.setTitle(title);

                    SwingUtilities.invokeLater(() -> {
                        sessionList.repaint();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void onSend(ActionEvent e) {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        // Auto-create a session if none is selected
        if (currentSession == null) {
            ChatSession session = chatDAO.createSession(model.getUuid(), "新聊天");
            if (session != null) {
                sessionListModel.add(0, session);
                sessionList.setSelectedIndex(0);
                currentSession = session;
            } else {
                JOptionPane.showMessageDialog(this, "创建会话失败，无法发送");
                return;
            }
        }

        if (openAIService == null) {
            JOptionPane.showMessageDialog(this,
                    "API配置未完成，无法发送消息",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable send button and clear input
        setInputEnabled(false);
        String userMessage = text;
        inputArea.setText("");

        // Save user message to database first
        chatDAO.addMessage(
                currentSession.getUuid(),
                "user",
                userMessage,
                System.currentTimeMillis()
        );

        // Load the persisted user message from database
        List<ChatMessage> currentHistory = chatDAO.listMessages(currentSession.getUuid());
        ChatMessage userMsg = currentHistory.getLast();
        
        // Add user message to UI using the database ID
        addMessageBubble(userMsg);
        messagePanel.revalidate();
        SwingUtilities.invokeLater(() ->
                messageScroll.getVerticalScrollBar()
                        .setValue(Integer.MAX_VALUE));

        // Prepare message history for API call
        List<core.service.OpenAIService.ChatMessage> apiMessages = new ArrayList<>();
        for (ChatMessage msg : currentHistory) {
            apiMessages.add(new core.service.OpenAIService.ChatMessage(
                    msg.getRole(),
                    msg.getContent()
            ));
        }

        // Create assistant message bubble for streaming
        createStreamingAssistantBubble();

        // Call OpenAI API in background thread using executor
        executorService.submit(() -> {
            StringBuilder fullResponse = new StringBuilder();

            openAIService.chatCompletionStream(apiMessages, new OpenAIService.StreamCallback() {
                @Override
                public void onChunk(String content) {
                    fullResponse.append(content);
                    if (isWindowActive) {
                        SwingUtilities.invokeLater(() -> {
                            if (currentAssistantMessage != null && isWindowActive) {
                                renderMarkdown(currentAssistantMessage, fullResponse.toString());
                            }
                        });
                    }
                }

                @Override
                public void onComplete() {
                    if (!isWindowActive) return;
                    
                    String assistantResponse = fullResponse.toString();

                    // Save assistant message to database
                    chatDAO.addMessage(
                            currentSession.getUuid(),
                            "assistant",
                            assistantResponse,
                            System.currentTimeMillis()
                    );

                    // Check if we need to generate title (first exchange)
                    List<ChatMessage> msgs = chatDAO.listMessages(currentSession.getUuid());
                    if (msgs.size() == 2) {
                         generateTitle(currentSession, userMessage, assistantResponse);
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (!isWindowActive) return;
                        
                        // Remove streaming bubble and reload all messages from database
                        if (currentAssistantMessage != null) {
                            Container parent = currentAssistantMessage.getParent();
                            if (parent != null) {
                                Container grandParent = parent.getParent();
                                if (grandParent == messagePanel) {
                                    messagePanel.remove(grandParent);
                                }
                            }
                        }
                        currentAssistantMessage = null;
                        
                        // Reload messages from database to show the persisted assistant message
                        loadMessages();
                        
                        setInputEnabled(true);
                        inputArea.requestFocus();
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
                        
                        setInputEnabled(true);
                        JOptionPane.showMessageDialog(
                                ChatWindow.this,
                                "发送失败: " + ex.getMessage(),
                                "错误",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                }
            });
        });
    }

    private void setInputEnabled(boolean enabled) {
        btnSend.setEnabled(enabled);
        inputArea.setEnabled(enabled);
    }

    // ===================== Renderer =====================

    private static class SessionRenderer extends JPanel implements ListCellRenderer<ChatSession> {
        private final JLabel titleLabel = new JLabel();
        private final JLabel timeLabel = new JLabel();

        public SessionRenderer() {
            super();
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(8, 14, 8, 14));

            titleLabel.setOpaque(false);
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            timeLabel.setOpaque(false);
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            timeLabel.setFont(UIManager.getFont("Label.font").deriveFont(11.0f));

            add(titleLabel);
            add(Box.createVerticalStrut(4));
            add(timeLabel);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ChatSession> list,
                ChatSession value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            if (isSelected) {
                setBackground(UIManager.getColor("List.selectionBackground"));
                titleLabel.setForeground(UIManager.getColor("List.selectionForeground"));
                timeLabel.setForeground(UIManager.getColor("List.selectionForeground"));
            } else {
                setBackground(new Color(0, 0, 0, 0));
                titleLabel.setForeground(UIManager.getColor("List.foreground"));
                timeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            }

            String title = (value.getTitle() == null || value.getTitle().isEmpty())
                    ? "新对话"
                    : value.getTitle();

            titleLabel.setText(title);
            timeLabel.setText(TIME_FMT.format(value.getCreatedAt()));

            return this;
        }
    }
}
