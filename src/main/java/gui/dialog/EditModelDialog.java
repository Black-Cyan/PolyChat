package gui.dialog;


import core.util.ModelDAO;
import core.entity.Model;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

public class EditModelDialog extends JDialog {

    private JTextField tfModelName;
    private JTextField tfNickname;
    private JTextField tfBaseUrl;

    private final ModelDAO modelDAO;
    private final Model model;

    public EditModelDialog(Frame parent, ModelDAO modelDAO, Model model) {
        super(parent, "编辑模型", true);
        this.modelDAO = modelDAO;
        this.model = model;

        setSize(450, 280);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        // ==================== 主表单 ====================
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 模型名称
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("模型名称:"), gbc);
        tfModelName = new JTextField(model.getModelName());
        gbc.gridx = 1;
        panel.add(tfModelName, gbc);

        // 昵称
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("昵称:"), gbc);
        tfNickname = new JTextField(model.getNickname());
        gbc.gridx = 1;
        panel.add(tfNickname, gbc);

        // Base URL
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Base URL:"), gbc);
        tfBaseUrl = new JTextField(model.getBaseUrl());
        gbc.gridx = 1;
        panel.add(tfBaseUrl, gbc);

        // ==================== API Key 区域 ====================
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("API Key:"), gbc);

        JButton btnUpdateKey = new JButton("更新");
        gbc.gridx = 1;
        panel.add(btnUpdateKey, gbc);

        // 点击更新按钮弹出输入框
        btnUpdateKey.addActionListener(e -> {
            String newKey = JOptionPane.showInputDialog(
                    this,
                    "请输入新的 API Key:",
                    "更新 API Key",
                    JOptionPane.PLAIN_MESSAGE
            );
            if(newKey != null && !newKey.trim().isEmpty()) {
                modelDAO.updateApiKey(model.getUuid(), newKey.trim());
                JOptionPane.showMessageDialog(this, "API Key 更新成功！");
            }
        });

        add(panel, BorderLayout.CENTER);

        // ==================== 底部按钮 ====================
        JPanel btnPanel = new JPanel();
        JButton btnOk = new JButton("保存基础信息");
        JButton btnCancel = new JButton("取消");
        btnPanel.add(btnOk);
        btnPanel.add(btnCancel);
        add(btnPanel, BorderLayout.SOUTH);

        btnOk.addActionListener(e -> {
            String modelName = tfModelName.getText().trim();
            String nickname = tfNickname.getText().trim();
            String baseUrl = tfBaseUrl.getText().trim();

            if(modelName.isEmpty() || baseUrl.isEmpty()) {
                JOptionPane.showMessageDialog(this, "模型名称和 Base URL 不能为空！");
                return;
            }

            modelDAO.updateModelInfo(model.getUuid(), baseUrl, modelName, nickname);
            dispose();
        });

        btnCancel.addActionListener(e -> dispose());
    }
}
