package gui.window;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;

import core.entity.Model;
import core.util.DBUtil;
import core.util.ModelDAO;
import core.util.ChatDAO;
import gui.card.ModelCard;
import gui.dialog.NewModelDialog;
import gui.dialog.ImportModelDialog;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class MainWindow extends JFrame {

    private CardLayout cardLayout;
    private JPanel contentPanel;
    private JPanel modelListPanel;
    private JScrollPane scrollPane;
    private ModelDAO modelDAO;
    private ChatDAO chatDAO;

    public MainWindow() {
        setTitle("PolyChat");
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        try {
            Connection conn = DBUtil.getConnection();
            modelDAO = new ModelDAO(conn);
            chatDAO = new ChatDAO(conn);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    this, "数据库连接失败！",
                    "错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        add(createSideMenu(), BorderLayout.WEST);
        add(createContentPanel(), BorderLayout.CENTER);
    }

    // ===================== 侧边栏 =====================

    private JPanel createSideMenu() {
        JPanel menu = new JPanel();
        menu.setPreferredSize(new Dimension(220, 0));
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        menu.putClientProperty("FlatLaf.style",
                "background:darken($Panel.background,5%);" +
                        "border:0,0,0,1,$Component.borderColor");

        JButton btnCreate = new SidebarButton("创建模型");
        JButton btnImport = new SidebarButton("导入模型");

        btnCreate.addActionListener(e -> {
            NewModelDialog dialog =
                    new NewModelDialog(this, modelDAO);
            dialog.setVisible(true);
            refreshModelList();
        });

        btnImport.addActionListener(e -> {
            ImportModelDialog dialog =
                    new ImportModelDialog(
                            this, modelDAO, this::refreshModelList);
            dialog.setVisible(true);
        });

        menu.add(Box.createVerticalStrut(20));
        menu.add(btnCreate);
        menu.add(Box.createVerticalStrut(10));
        menu.add(btnImport);
        menu.add(Box.createVerticalGlue());

        return menu;
    }

    // ===================== 内容区 =====================

    private JPanel createContentPanel() {
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.putClientProperty(
                "FlatLaf.style", "background:$Panel.background");

        contentPanel.add(createModelSelectPage(), "model_select");
        cardLayout.show(contentPanel, "model_select");

        return contentPanel;
    }

    private JScrollPane createModelSelectPage() {
        modelListPanel = new JPanel();
        modelListPanel.setLayout(
                new BoxLayout(modelListPanel, BoxLayout.Y_AXIS));
        modelListPanel.setBorder(
                BorderFactory.createEmptyBorder(20, 24, 20, 24));
        modelListPanel.putClientProperty(
                "FlatLaf.style", "background:$Panel.background");

        scrollPane = new JScrollPane(modelListPanel);
        scrollPane.setBorder(null);
        scrollPane.putClientProperty(
                "FlatLaf.style", "border:null; background:$Panel.background");
        scrollPane.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);

        refreshModelList();
        return scrollPane;
    }

    // ===================== 数据刷新 =====================

    public void refreshModelList() {
        if (modelListPanel == null) return;
        modelListPanel.removeAll();

        List<Model> models = modelDAO.getAllModels();
        for (Model m : models) {
            ModelCard card =
                    new ModelCard(this, m, modelDAO, chatDAO, this::refreshModelList);
            modelListPanel.add(card);
            modelListPanel.add(Box.createVerticalStrut(12));
        }

        modelListPanel.revalidate();
        modelListPanel.repaint();
    }

    // ===================== Main =====================

    public static void main(String[] args) {
        FlatArcDarkIJTheme.setup();

        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.width", 10);

        SwingUtilities.invokeLater(
                () -> new MainWindow().setVisible(true));
    }

    // ===================== 侧边栏按钮 =====================

    private static class SidebarButton extends JButton {

        private static final Color TEXT =
                UIManager.getColor("Label.foreground");
        private static final Color BORDER =
                UIManager.getColor("Component.borderColor");

        private static final Color HOVER =
                new Color(255, 255, 255, 22);
        private static final Color PRESSED =
                new Color(255, 255, 255, 40);

        private static final int ARC = 12;

        public SidebarButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
            setHorizontalAlignment(SwingConstants.LEFT);
            setMaximumSize(new Dimension(220, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            ButtonModel m = getModel();
            if (m.isPressed()) {
                g2.setColor(PRESSED);
                g2.fillRoundRect(
                        0, 0, getWidth(), getHeight(), ARC, ARC);
            } else if (m.isRollover()) {
                g2.setColor(HOVER);
                g2.fillRoundRect(
                        0, 0, getWidth(), getHeight(), ARC, ARC);
            }

            g2.setColor(BORDER);
            g2.drawRoundRect(
                    0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.dispose();

            super.paintComponent(g);
        }
    }
}
