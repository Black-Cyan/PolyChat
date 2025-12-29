package gui.window;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;

import core.entity.ChatMessage;
import core.entity.ChatSession;
import core.entity.Model;
import core.util.ChatDAO;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.List;

public class ChatWindow extends JFrame {

    // ===================== 基础配置 =====================

    static {
        FlatArcDarkIJTheme.setup();
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
    }

    private final Model model;
    private final ChatDAO chatDAO;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("MM-dd HH:mm");

    private final DefaultListModel<ChatSession> sessionListModel =
            new DefaultListModel<>();
    private final JList<ChatSession> sessionList =
            new JList<>(sessionListModel);

    private final JPanel messagePanel = new JPanel();
    private JScrollPane messageScroll;
    private final JTextArea inputArea = new JTextArea(3, 40);

    private ChatSession currentSession;

    // ===================== 构造函数 =====================

    public ChatWindow(Model model, ChatDAO chatDAO) {
        this.model = model;
        this.chatDAO = chatDAO;

        setTitle("Chat - " +
                (model.getNickname().isEmpty()
                        ? model.getModelName()
                        : model.getNickname()));

        setSize(1000, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        add(buildSessionPane(), BorderLayout.WEST);
        add(buildChatPane(), BorderLayout.CENTER);

        loadSessions();
    }

    // ===================== 左侧会话栏 =====================

    private Component buildSessionPane() {
        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(260, 0));
        left.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(
                        0, 0, 0, 1,
                        UIManager.getColor("Component.borderColor")),
                new EmptyBorder(8, 8, 8, 8)
        ));
        left.putClientProperty("FlatLaf.style",
                "background:$Panel.background");

        JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
        actions.setOpaque(false);
        JButton btnNew = new JButton("新聊天");
        btnNew.putClientProperty("JButton.buttonType", "default");
        btnNew.addActionListener(this::onNewSession);

        JButton btnDelete = new JButton("删除会话");
        btnDelete.putClientProperty("JButton.buttonType", "toolBar");
        btnDelete.addActionListener(e -> onDeleteSession());

        actions.add(btnNew);
        actions.add(btnDelete);
        left.add(actions, BorderLayout.NORTH);

        sessionList.setCellRenderer(new SessionRenderer());
        sessionList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        sessionList.setFixedCellHeight(44);
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
                new EmptyBorder(12, 12, 12, 12));
        messagePanel.putClientProperty("FlatLaf.style",
                "background:$EditorPane.background");

        messageScroll = new JScrollPane(messagePanel);
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

        JButton btnSend = new JButton("Send");
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
        boolean isUser =
                "user".equalsIgnoreCase(msg.getRole());

        JPanel line = new JPanel();
        line.setLayout(
                new BoxLayout(line, BoxLayout.X_AXIS));
        line.setOpaque(false);

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(isUser
                ? UIManager.getColor(
                "Button.default.background")
                : UIManager.getColor(
                "EditorPane.background"));
        bubble.setBorder(new CompoundBorder(
                new LineBorder(
                        UIManager.getColor(
                                "Component.borderColor"),
                        1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        bubble.setMaximumSize(
                new Dimension(720, Integer.MAX_VALUE));

        JTextArea text =
                new JTextArea(msg.getContent());
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(null);
        text.setFont(text.getFont().deriveFont(14f));

        bubble.add(text, BorderLayout.CENTER);

        if (isUser) {
            line.add(Box.createHorizontalGlue());
            line.add(bubble);
        } else {
            line.add(bubble);
            line.add(Box.createHorizontalGlue());
        }

        line.setBorder(
                new EmptyBorder(4, 4, 4, 4));
        messagePanel.add(line);
        messagePanel.add(
                Box.createVerticalStrut(4));
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

        // TODO: 接 OpenAI
        inputArea.setText("");
    }

    // ===================== Renderer =====================

    private static class SessionRenderer
            extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            JLabel base = (JLabel)
                    super.getListCellRendererComponent(
                            list, value, index,
                            isSelected, cellHasFocus);

            base.setBorder(
                    new EmptyBorder(6, 12, 6, 12));
            base.setOpaque(true);
            base.setBackground(isSelected
                    ? UIManager.getColor(
                    "List.selectionBackground")
                    : UIManager.getColor(
                    "Panel.background"));

            if (value instanceof ChatSession s) {
                base.setText(
                        (s.getTitle() == null ||
                                s.getTitle().isEmpty()
                                ? "New Chat"
                                : s.getTitle())
                                + "   "
                                + TIME_FMT.format(
                                s.getCreatedAt()));
            }
            return base;
        }
    }
}
