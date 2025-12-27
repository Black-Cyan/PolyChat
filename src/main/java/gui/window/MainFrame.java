package gui.window;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
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
        menu.setPreferredSize(new Dimension(180, 0));
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));

        menu.putClientProperty("FlatLaf.style", "background: #e5f0ff;");

        JButton btnCreate = new JButton("➕ 创建模型");
        JButton btnImport = new JButton("📂 导入模型");

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
        menu.add(Box.createVerticalStrut(10));
        menu.add(btnImport);
        menu.add(Box.createVerticalGlue());

        return menu;
    }

    private void styleMenuButton(JButton button) {
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(160, 40));
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.putClientProperty("FlatLaf.style", """
            background: #0078d7;
            hoverBackground: #0090ff;
            pressedBackground: #005ea6;
            foreground: #ffffff;
            arc: 12;
        """);

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
        FlatLightLaf.setup();

        UIManager.put("Component.focusColor", new Color(0, 120, 215));
        UIManager.put("Button.background", new Color(0, 120, 215));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.hoverBackground", new Color(0, 150, 255));
        UIManager.put("Button.pressedBackground", new Color(0, 100, 200));
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("ScrollBar.thumb", new Color(0, 120, 215));

        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.width", 10);

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
