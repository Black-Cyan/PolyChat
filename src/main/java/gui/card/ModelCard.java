package gui.card;

import core.entity.Model;
import core.util.ModelDAO;
import core.util.ChatDAO;
import core.util.WindowManager;
import gui.dialog.EditModelDialog;
import gui.dialog.ExportModelDialog;
import gui.window.ChatWindow;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ModelCard extends JPanel {

    private final JPanel content;
    private final Model model;
    private final ModelDAO modelDAO;
    private final Frame parent;
    private final ChatDAO chatDAO;
    private final Runnable refreshCallback;
    private JCheckBox selectCheckbox;

    public ModelCard(Frame parent, Model model, ModelDAO modelDAO) {
        this(parent, model, modelDAO, null, null);
    }

    public ModelCard(Frame parent, Model model, ModelDAO modelDAO, ChatDAO chatDAO) {
        this(parent, model, modelDAO, chatDAO, null);
    }

    public ModelCard(Frame parent,
                     Model model,
                     ModelDAO modelDAO,
                     ChatDAO chatDAO,
                     Runnable refreshCallback) {

        this.parent = parent;
        this.model = model;
        this.modelDAO = modelDAO;
        this.chatDAO = chatDAO;
        this.refreshCallback = refreshCallback;

        setLayout(new BorderLayout());
        setOpaque(false);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        content = new JPanel(new BorderLayout(0, 12));
        content.putClientProperty("FlatLaf.style",
                "arc:12;" +
                        "background:darken($Panel.background,3%)");

        setCardBorder(UIManager.getColor("Component.borderColor"));

        // ================= Header =================

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        // Add selection checkbox
        selectCheckbox = new JCheckBox();
        selectCheckbox.setOpaque(false);
        selectCheckbox.setFocusPainted(false);

        JLabel icon = new JLabel(UIManager.getIcon("FileView.computerIcon"));
        icon.setForeground(UIManager.getColor("Component.accentColor"));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(
                model.getNickname().isEmpty()
                        ? model.getModelName()
                        : model.getNickname());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JLabel subtitle = new JLabel(model.getModelName());
        subtitle.setForeground(
                UIManager.getColor("Label.disabledForeground"));
        subtitle.setFont(subtitle.getFont().deriveFont(13f));

        titleBox.add(title);
        titleBox.add(subtitle);

        // Create a panel for icon and titles
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(icon, BorderLayout.WEST);
        centerPanel.add(titleBox, BorderLayout.CENTER);

        header.add(selectCheckbox, BorderLayout.WEST);
        header.add(centerPanel, BorderLayout.CENTER);

        // ================= Body =================

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);

        JLabel description =
                new JLabel("Base URL: " + model.getBaseUrl());
        description.setForeground(
                UIManager.getColor("Label.disabledForeground"));
        description.setFont(
                description.getFont().deriveFont(13f));

        body.add(description, BorderLayout.CENTER);

        content.add(header, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);

        add(content, BorderLayout.CENTER);

        addHoverEffect();
        addRightClickMenu();
    }

    // ================= Hover & Click =================

    private void addHoverEffect() {
        MouseAdapter hover = new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                content.putClientProperty("FlatLaf.style",
                        "arc:12;" +
                                "background:darken($Panel.background,6%)");
                setCardBorder(
                        UIManager.getColor("Component.focusColor"));
                content.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                content.putClientProperty("FlatLaf.style",
                        "arc:12;" +
                                "background:darken($Panel.background,3%)");
                setCardBorder(
                        UIManager.getColor("Component.borderColor"));
                content.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                
                // Check if click was on checkbox - if so, don't open chat window
                if (selectCheckbox != null && 
                    selectCheckbox.contains(SwingUtilities.convertPoint(
                        (Component) e.getSource(), e.getPoint(), selectCheckbox))) {
                    return;
                }
                
                if (chatDAO != null && modelDAO != null) {
                    WindowManager wm = WindowManager.getInstance();
                    if (wm.hasWindow(model.getUuid())) {
                        wm.focusWindow(model.getUuid());
                    } else {
                        ChatWindow chatWindow = new ChatWindow(model, chatDAO, modelDAO);
                        chatWindow.setVisible(true);
                    }
                }
            }
        };

        addMouseListener(hover);
        content.addMouseListener(hover);
    }

    // ================= 右键菜单 =================

    private void addRightClickMenu() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem edit = new JMenuItem("编辑", loadIcon("/images/edit.png"));
        JMenuItem delete = new JMenuItem("删除", loadIcon("/images/delete.png"));
        JMenuItem export = new JMenuItem("导出", loadIcon("/images/export.png"));

        for (JMenuItem item : new JMenuItem[]{edit, delete, export}) {
            item.setBorder(
                    BorderFactory.createEmptyBorder(6, 16, 6, 16));
            popup.add(item);
        }

        edit.addActionListener(e -> {
            EditModelDialog dialog =
                    new EditModelDialog(parent, modelDAO, model);
            dialog.setVisible(true);
            if (dialog.isChangesMade() && refreshCallback != null) {
                refreshCallback.run();
            }
        });

        delete.addActionListener(e -> {
            // 创建 JOptionPane
            JOptionPane optionPane = new JOptionPane(
                    "确认删除模型 " + model.getModelName()
                            + " 吗？\n这将清空该模型的所有聊天记录且无法恢复，该操作不可撤销。",
                    JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.YES_NO_OPTION
            );
            
            // 创建对话框
            JDialog dialog = optionPane.createDialog(parent, "删除确认");
            
            // 为对话框添加组件监听器，确保在对话框完全显示后再添加键盘监听
            dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentShown(java.awt.event.ComponentEvent e) {
                    // 强制获取焦点
                    dialog.requestFocus();
                    
                    // 添加键盘监听
                    dialog.addKeyListener(new java.awt.event.KeyAdapter() {
                        @Override
                        public void keyPressed(java.awt.event.KeyEvent e) {
                            char key = Character.toLowerCase(e.getKeyChar());
                            if (key == 'y') {
                                // 模拟点击YES按钮
                                optionPane.setValue(JOptionPane.YES_OPTION);
                                dialog.dispose();
                            } else if (key == 'n') {
                                // 模拟点击NO按钮
                                optionPane.setValue(JOptionPane.NO_OPTION);
                                dialog.dispose();
                            }
                        }
                    });
                }
            });
            
            // 显示对话框
            dialog.setVisible(true);
            
            // 获取用户选择
            Object value = optionPane.getValue();
            if (value != null && value.equals(JOptionPane.YES_OPTION)) {
                modelDAO.deleteModel(model.getUuid());
                if (refreshCallback != null) refreshCallback.run();
            }
        });

        export.addActionListener(e -> {
            ExportModelDialog dialog =
                    new ExportModelDialog(parent, modelDAO, model);
            dialog.setVisible(true);
        });

        content.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger())
                    popup.show(e.getComponent(), e.getX(), e.getY());
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                    popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    // ================= Border =================

    private void setCardBorder(Color color) {
        content.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));
    }

    private ImageIcon loadIcon(String path) {
        java.net.URL url = getClass().getResource(path);
        return url != null ? new ImageIcon(url) : null;
    }

    /**
     * Check if this model card is selected for multi-chat
     * @return true if selected, false otherwise
     */
    public boolean isSelected() {
        return selectCheckbox != null && selectCheckbox.isSelected();
    }

    /**
     * Set the selection state of this model card
     * @param selected true to select, false to deselect
     */
    public void setSelected(boolean selected) {
        if (selectCheckbox != null) {
            selectCheckbox.setSelected(selected);
        }
    }

    /**
     * Get the model associated with this card
     * @return the model
     */
    public Model getModel() {
        return model;
    }
}
