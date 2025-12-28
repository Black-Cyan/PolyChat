package gui.window;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;

import core.entity.Model;
import core.util.DBUtil;
import core.util.ModelDAO;
import gui.card.ModelCard;
import gui.dialog.NewModelDialog;
import gui.dialog.ImportModelDialog;

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
        setResizable(false);
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

        btnCreate.addActionListener(e -> {
            NewModelDialog dialog = new NewModelDialog(this, modelDAO);
            dialog.setVisible(true);
            refreshModelList();
        });

        btnImport.addActionListener(e -> {
            ImportModelDialog dialog = new ImportModelDialog(this, modelDAO, this::refreshModelList);
            dialog.setVisible(true);
        });

        menu.add(Box.createVerticalStrut(20));
        menu.add(btnCreate);
        menu.add(Box.createVerticalStrut(12));
        menu.add(btnImport);
        menu.add(Box.createVerticalGlue());

        return menu;
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
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);

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

    // ========== 侧边栏按钮 ==========
    private static class SidebarButton extends JButton {
        private static final Color ACCENT = new Color(0x5fafff);
        private static final Color HOVER_FILL = new Color(0x5fafff80, true);
        private static final Color PRESSED_FILL = new Color(0x5fafffb3, true);
        private static final Color BORDER = new Color(0x1D1D1D);
        private static final Color TEXT = new Color(0xdde7ff);
        private static final int ARC = 14;

        public SidebarButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setOpaque(false);
            setForeground(TEXT);
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
            Color fill = null;
            if (m.isPressed()) fill = PRESSED_FILL;
            else if (m.isRollover()) fill = HOVER_FILL;
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
            }
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, ARC, ARC);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
