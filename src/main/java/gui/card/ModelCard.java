package gui.card;

import core.entity.Model;
import core.util.ModelDAO;
import gui.dialog.EditModelDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.border.LineBorder;

public class ModelCard extends JPanel {

    private final JPanel content;
    private final Model model;
    private final ModelDAO modelDAO;
    private final Frame parent;

    public ModelCard(Frame parent, Model model, ModelDAO modelDAO) {
        this.parent = parent;
        this.model = model;
        this.modelDAO = modelDAO;

        setLayout(new BorderLayout());
        setOpaque(false);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        content = new JPanel();
        content.setLayout(new BorderLayout(0, 10));
        content.putClientProperty("FlatLaf.style", """
            arc: 18;
            background: darken(@background, 3%);
        """);
        setCardBorder(new Color(0x3c3f41));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        JLabel icon = new JLabel(UIManager.getIcon("FileView.computerIcon"));
        icon.putClientProperty("FlatLaf.style", "foreground: #4c88ff;");

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(model.getNickname().isEmpty() ? model.getModelName() : model.getNickname());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JLabel subtitle = new JLabel(model.getModelName());
        subtitle.setForeground(UIManager.getColor("Component.infoForeground"));
        subtitle.setFont(subtitle.getFont().deriveFont(13f));

        titleBox.add(title);
        titleBox.add(subtitle);

        header.add(icon, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        JLabel description = new JLabel("Base URL: " + model.getBaseUrl());
        description.setForeground(UIManager.getColor("Component.infoForeground"));
        description.setFont(description.getFont().deriveFont(13f));
        body.add(description, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        footer.setOpaque(false);
        JLabel hint = new JLabel("单击打开 · 右键更多");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setFont(hint.getFont().deriveFont(12f));
        footer.add(hint);

        content.add(header, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);

        add(content, BorderLayout.CENTER);

        addHoverEffect();
        addRightClickMenu();
    }

    private void addHoverEffect() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                content.putClientProperty("FlatLaf.style", """
                    arc: 18;
                    background: darken(@background, 6%);
                 """);
                setCardBorder(new Color(0x505458));
                content.revalidate();
                content.repaint();
             }

             @Override
             public void mouseExited(MouseEvent e) {
                 content.putClientProperty("FlatLaf.style", """
                     arc: 18;
                     background: darken(@background, 3%);
                  """);
                 setCardBorder(new Color(0x3c3f41));
                 content.revalidate();
                 content.repaint();
             }

            @Override
            public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(
                        ModelCard.this,
                        "打开模型：" + model.getModelName()
                );
            }
        });
    }

    private void addRightClickMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem editItem = new JMenuItem("编辑", UIManager.getIcon("FileView.fileIcon"));
        JMenuItem deleteItem = new JMenuItem("删除", UIManager.getIcon("TabbedPane.closeIcon"));
        JMenuItem exportItem = new JMenuItem("导出", UIManager.getIcon("FileView.floppyDriveIcon"));

        JMenuItem[] items = { editItem, deleteItem, exportItem };
        for (JMenuItem item : items) {
            item.setHorizontalAlignment(SwingConstants.CENTER);
            item.setOpaque(true);
            item.setBackground(UIManager.getColor("PopupMenu.background"));
            item.setForeground(UIManager.getColor("PopupMenu.foreground"));
            item.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { item.setBackground(darken(UIManager.getColor("PopupMenu.background"), 0.05f)); }
                @Override
                public void mouseExited(MouseEvent e) { item.setBackground(UIManager.getColor("PopupMenu.background")); }
            });

            popupMenu.add(item);
        }

        // 绑定功能
        editItem.addActionListener(e -> {
            EditModelDialog dialog = new EditModelDialog(parent, modelDAO, model);
            dialog.setVisible(true);
        });

        deleteItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    parent,
                    "确认删除模型 " + model.getModelName() + " 吗？",
                    "删除确认",
                    JOptionPane.YES_NO_OPTION
            );
            if(confirm == JOptionPane.YES_OPTION) {
                modelDAO.deleteModel(model.getUuid());
                JOptionPane.showMessageDialog(parent, "删除成功！");
                Container parentContainer = ModelCard.this.getParent();
                if(parentContainer != null) {
                    parentContainer.remove(ModelCard.this);
                    parentContainer.revalidate();
                    parentContainer.repaint();
                }
            }
        });

        content.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) popupMenu.show(e.getComponent(), e.getX(), e.getY()); }
            @Override
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) popupMenu.show(e.getComponent(), e.getX(), e.getY()); }
        });
    }

    private Color darken(Color color, float fraction) {
        int r = Math.max((int) (color.getRed() * (1 - fraction)), 0);
        int g = Math.max((int) (color.getGreen() * (1 - fraction)), 0);
        int b = Math.max((int) (color.getBlue() * (1 - fraction)), 0);
        return new Color(r, g, b, color.getAlpha());
    }

    private void setCardBorder(Color lineColor) {
        content.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(lineColor, 1, true),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));
    }
}
