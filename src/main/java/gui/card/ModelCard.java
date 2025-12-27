package gui.card;

import core.entity.Model;
import core.util.ModelDAO;
import gui.dialog.EditModelDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        content.putClientProperty("FlatLaf.style", """
            arc: 18;
            background: #ffffff;
            border: 1,1,1,1,#e3e3e3;
        """);

        JLabel title = new JLabel(model.getNickname().isEmpty() ? model.getModelName() : model.getNickname());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        title.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel("<html><body style='width:280px'>" + model.getModelName() + "</body></html>");
        description.setForeground(new Color(120, 120, 120));
        description.setFont(description.getFont().deriveFont(13f));
        description.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);


        content.add(title);
        content.add(description);

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
                    background: #f5f7fa;
                    border: 1,1,1,1,#cfd8dc;
                """);
                content.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                content.putClientProperty("FlatLaf.style", """
                    arc: 18;
                    background: #ffffff;
                    border: 1,1,1,1,#e3e3e3;
                """);
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
            item.setBackground(Color.WHITE);
            item.setForeground(Color.DARK_GRAY);
            item.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));

            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { item.setBackground(new Color(220, 235, 255)); }
                @Override
                public void mouseExited(MouseEvent e) { item.setBackground(Color.WHITE); }
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
}
