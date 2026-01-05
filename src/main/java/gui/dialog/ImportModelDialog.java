package gui.dialog;

import core.util.ModelDAO;
import core.util.ModelIOUtil;
import core.util.ModelIOUtil.ModelPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import javax.swing.filechooser.FileNameExtensionFilter;

public class ImportModelDialog extends JDialog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportModelDialog.class);

    private final JTextField tfFilePath;

    public ImportModelDialog(Frame parent, ModelDAO modelDAO, Runnable onImported) {
        super(parent, "导入模型", true);

        setSize(520, 200);
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

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("模型文件:"), gbc);

        tfFilePath = new JTextField();
        tfFilePath.setColumns(30);
        gbc.gridx = 1;
        panel.add(tfFilePath, gbc);

        JButton btnBrowse = new JButton("浏览...");
        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(btnBrowse, gbc);

        add(panel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        btnPanel.setOpaque(false);

        JButton btnOk = new JButton("导入");
        JButton btnCancel = new JButton("取消");

        btnOk.putClientProperty("FlatLaf.style", "background: #3d7bfd; foreground: #ffffff; arc: 12; hoverBackground: #4c88ff; pressedBackground: #2c6ae0; focusWidth: 0;");
        btnCancel.putClientProperty("FlatLaf.style", "background: darken(@background,5%); foreground: @foreground; arc: 12; hoverBackground: darken(@background,8%);");

        btnPanel.add(btnOk);
        btnPanel.add(btnCancel);

        add(btnPanel, BorderLayout.SOUTH);

        btnBrowse.addActionListener(e -> openFileChooser());

        btnOk.addActionListener(e -> {
            String path = tfFilePath.getText().trim();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请选择要导入的文件");
                return;
            }
            File file = new File(path);
            if (!file.exists()) {
                JOptionPane.showMessageDialog(this, "文件不存在: " + path);
                return;
            }
            if (!file.getName().toLowerCase().endsWith(".mod")) {
                JOptionPane.showMessageDialog(this, "文件类型必须为 .mod");
                return;
            }
            try {
                ModelPayload payload = ModelIOUtil.readModel(file);
                modelDAO.addModel(payload.baseUrl(), payload.apiKey(), payload.modelName(), payload.nickname());
                JOptionPane.showMessageDialog(this, "导入成功");
                if (onImported != null) onImported.run();
                dispose();
            } catch (Exception ex) {
                LOGGER.error("Failed to import model from {}", path, ex);
                JOptionPane.showMessageDialog(this, "导入失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCancel.addActionListener(e -> dispose());

        presetDefaultPath();
    }

    private void presetDefaultPath() {
        tfFilePath.setText(new File(System.getProperty("user.home"), "model.mod").getAbsolutePath());
    }

    private void openFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Model Files (*.mod)", "mod"));
        chooser.setSelectedFile(new File(tfFilePath.getText()));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            tfFilePath.setText(ensureModExtension(chooser.getSelectedFile()).getAbsolutePath());
        }
    }

    private File ensureModExtension(File file) {
        String name = file.getName();
        if (!name.toLowerCase().endsWith(".mod")) {
            file = new File(file.getParentFile() == null ? new File(".") : file.getParentFile(), name + ".mod");
        }
        return file;
    }
}
