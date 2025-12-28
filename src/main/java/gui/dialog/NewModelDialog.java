package gui.dialog;

import core.util.ModelDAO;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;

public class NewModelDialog extends JDialog {

    private JTextField tfModelName;
    private JTextField tfNickname;
    private JTextField tfBaseUrl;
    private JTextField tfApiKey;

    public NewModelDialog(Frame parent, ModelDAO modelDAO) {
        super(parent, "新建模型", true);

        setSize(480, 340);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout(12, 12));

        // 主面板
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.putClientProperty(FlatClientProperties.STYLE, "background: #2b2d30; arc: 12;");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        Color labelColor = new Color(0x9aa0aa);

        // ==== 输入框工厂方法 ====
        java.util.function.Supplier<JTextField> createField = () -> {
            JTextField tf = new JTextField();
            tf.putClientProperty(FlatClientProperties.STYLE, """
                arc: 10;
                background: #242529;
                foreground: #ffffff;
                caretColor: #ffffff;
            """);
            tf.setSelectionColor(new Color(0x4c88ff40, true));
            tf.setSelectedTextColor(Color.WHITE);
            return tf;
        };

        // 模型名称
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblModelName = new JLabel("模型名称:");
        lblModelName.setForeground(labelColor);
        panel.add(lblModelName, gbc);
        gbc.gridx = 1;
        tfModelName = createField.get();
        panel.add(tfModelName, gbc);

        // 昵称
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel lblNickname = new JLabel("昵称:");
        lblNickname.setForeground(labelColor);
        panel.add(lblNickname, gbc);
        gbc.gridx = 1;
        tfNickname = createField.get();
        panel.add(tfNickname, gbc);

        // Base URL
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblBaseUrl = new JLabel("Base URL:");
        lblBaseUrl.setForeground(labelColor);
        panel.add(lblBaseUrl, gbc);
        gbc.gridx = 1;
        tfBaseUrl = createField.get();
        panel.add(tfBaseUrl, gbc);

        // API Key
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel lblApiKey = new JLabel("API Key:");
        lblApiKey.setForeground(labelColor);
        panel.add(lblApiKey, gbc);
        gbc.gridx = 1;
        tfApiKey = createField.get();
        panel.add(tfApiKey, gbc);

        add(panel, BorderLayout.CENTER);

        // 底部按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnPanel.setOpaque(false);

        JButton btnOk = new JButton("确定");
        JButton btnCancel = new JButton("取消");

        // 按钮样式统一
        btnOk.putClientProperty(FlatClientProperties.STYLE, """
            background: #4c88ff;
            foreground: #ffffff;
            arc: 12;
            hoverBackground: #5fafff;
            pressedBackground: #2c6ae0;
        """);

        btnCancel.putClientProperty(FlatClientProperties.STYLE, """
            background: #242529;
            foreground: #9aa0aa;
            arc: 12;
            hoverBackground: #2c2f33;
            pressedBackground: #1f2124;
        """);

        btnPanel.add(btnCancel);
        btnPanel.add(btnOk);
        add(btnPanel, BorderLayout.SOUTH);

        // 按钮事件
        btnOk.addActionListener(e -> {
            String modelName = tfModelName.getText().trim();
            String nickname = tfNickname.getText().trim();
            String baseUrl = tfBaseUrl.getText().trim();
            String apiKey = tfApiKey.getText().trim();

            if (modelName.isEmpty() || baseUrl.isEmpty()) {
                JOptionPane.showMessageDialog(this, "模型名称和 Base URL 不能为空");
                return;
            }

            modelDAO.addModel(baseUrl, apiKey, modelName, nickname);
            dispose();
        });

        btnCancel.addActionListener(e -> dispose());
    }
}
