package gui.dialog;

import core.util.ModelDAO;

import javax.swing.*;
import java.awt.*;

public class NewModelDialog extends JDialog {

    private JTextField tfModelName;
    private JTextField tfNickname;
    private JTextField tfBaseUrl;
    private JTextField tfApiKey;

    private final ModelDAO modelDAO;

    public NewModelDialog(Frame parent, ModelDAO modelDAO) {
        super(parent, "新建模型", true);
        this.modelDAO = modelDAO;

        setSize(450, 320);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout(12, 12));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.putClientProperty("FlatLaf.style", "background: @background; arc: 12;");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // 模型名称
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("模型名称:"), gbc);
        tfModelName = new JTextField();
        tfModelName.setColumns(25);
        gbc.gridx = 1;
        panel.add(tfModelName, gbc);

        // 昵称
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("昵称:"), gbc);
        tfNickname = new JTextField();
        tfNickname.setColumns(25);
        gbc.gridx = 1;
        panel.add(tfNickname, gbc);

        // Base URL
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Base URL:"), gbc);
        tfBaseUrl = new JTextField();
        tfBaseUrl.setColumns(25);
        gbc.gridx = 1;
        panel.add(tfBaseUrl, gbc);

        // API Key
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("API Key:"), gbc);
        tfApiKey = new JTextField();
        tfApiKey.setColumns(25);
        gbc.gridx = 1;
        panel.add(tfApiKey, gbc);

        add(panel, BorderLayout.CENTER);

        // 底部按钮
        JPanel btnPanel = new JPanel();
        btnPanel.setOpaque(false);

        JButton btnOk = new JButton("确定");
        JButton btnCancel = new JButton("取消");

        btnOk.putClientProperty("FlatLaf.style", "background: #3d7bfd; foreground: #ffffff; arc: 12; hoverBackground: #4c88ff; pressedBackground: #2c6ae0; focusWidth: 0;");
        btnCancel.putClientProperty("FlatLaf.style", "background: darken(@background,5%); foreground: @foreground; arc: 12; hoverBackground: darken(@background,8%);");

        btnPanel.add(btnOk);
        btnPanel.add(btnCancel);

        add(btnPanel, BorderLayout.SOUTH);

        // 按钮事件
        btnOk.addActionListener(e -> {
            String modelName = tfModelName.getText().trim();
            String nickname = tfNickname.getText().trim();
            String baseUrl = tfBaseUrl.getText().trim();
            String apiKey = tfApiKey.getText().trim();

            if(modelName.isEmpty() || baseUrl.isEmpty()) {
                JOptionPane.showMessageDialog(this, "模型名称和Base URL不能为空");
                return;
            }

            modelDAO.addModel(baseUrl, apiKey, modelName, nickname);
            dispose();
        });

        btnCancel.addActionListener(e -> dispose());
    }

}
