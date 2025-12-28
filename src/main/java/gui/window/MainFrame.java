package gui.window;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;

import core.entity.Model;
import core.util.DBUtil;
import core.util.ModelDAO;
import gui.card.ModelCard;
import gui.dialog.NewModelDialog;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class MainFrame extends JFrame {

    private CardLayout cardLayout;
    private JPanel contentPanel;
    private JPanel modelListPanel;
    private JScrollPane scrollPane;
    private ModelDAO modelDAO;

    public MainFrame() {
        setTitle("PolyChat");
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 初始化数据库 DAO
        try {
            Connection conn = DBUtil.getConnection();
            modelDAO = new ModelDAO(conn);
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "数据库连接失败！", "错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        add(createSideMenu(), BorderLayout.WEST);
        add(createContentPanel(), BorderLayout.CENTER);
    }

    // ========== 侧边菜单 ==========
    private JPanel createSideMenu() {
        JPanel menu = new JPanel();
        menu.setPreferredSize(new Dimension(200, 0));
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        menu.putClientProperty("FlatLaf.style", "background: darken(@background, 6%);");

        JButton btnCreate = new SidebarButton("创建模型");
        JButton btnImport = new SidebarButton("导入模型");

        styleMenuButton(btnCreate);
        styleMenuButton(btnImport);

        btnCreate.addActionListener(e -> {
            NewModelDialog dialog = new NewModelDialog(this, modelDAO);
            dialog.setVisible(true);
            refreshModelList();
        });

        btnImport.addActionListener(e -> {
            // TODO: 打开导入模型逻辑
            JOptionPane.showMessageDialog(this, "这里弹出导入模型窗口");
            refreshModelList();
        });

        menu.add(Box.createVerticalStrut(20));
        menu.add(btnCreate);
        menu.add(Box.createVerticalStrut(12));
        menu.add(btnImport);
        menu.add(Box.createVerticalGlue());

        return menu;
    }

    private void styleMenuButton(JButton button) {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(10);
        button.setMaximumSize(new Dimension(200, 46));
        button.setPreferredSize(new Dimension(200, 46));
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(4, 4, 4, 4));
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        button.putClientProperty("JButton.buttonType", "roundRect");


    }

    // ========== 内容面板 ==========
    private JPanel createContentPanel() {
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        contentPanel.add(createModelSelectPage(), "model_select");

        cardLayout.show(contentPanel, "model_select");
        return contentPanel;
    }

    private JScrollPane createModelSelectPage() {
        modelListPanel = new JPanel();
        modelListPanel.setLayout(new BoxLayout(modelListPanel, BoxLayout.Y_AXIS));
        modelListPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        modelListPanel.putClientProperty("FlatLaf.style", "background: @background;");

        scrollPane = new JScrollPane(modelListPanel);
        scrollPane.putClientProperty("FlatLaf.style", "border: null; background: @background;");
        scrollPane.setBorder(null);

        refreshModelList();

        return scrollPane;
    }

    // ========== 刷新模型列表 ==========
    public void refreshModelList() {
        if (modelListPanel == null) return;
        modelListPanel.removeAll();

        List<Model> models = modelDAO.getAllModels();
        for (Model m : models) {
            ModelCard card = new ModelCard(this, m, modelDAO);
            modelListPanel.add(card);
            modelListPanel.add(Box.createVerticalStrut(10));
        }

        modelListPanel.revalidate();
        modelListPanel.repaint();
    }

    // ========== 主函数 ==========
    public static void main(String[] args) {
        FlatArcDarkIJTheme.setup();

        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 14);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.width", 10);

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    //========== 侧边栏按钮 ==========
    private static class SidebarButton extends JButton {
        private static final Color BASE = new Color(0x3d7bfd);
        private static final Color HOVER = new Color(0x4c88ff);
        private static final Color PRESSED = new Color(0x2c6ae0);
        private static final int ARC = 14;

        SidebarButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setOpaque(false);
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(4, 4, 4, 4));
            setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
            setHorizontalAlignment(SwingConstants.LEFT);
            setIconTextGap(10);
            setMaximumSize(new Dimension(200, 46));
            setPreferredSize(new Dimension(200, 46));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel m = getModel();
            Color fill = BASE;
            if (m.isPressed()) fill = PRESSED;
            else if (m.isRollover()) fill = HOVER;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
