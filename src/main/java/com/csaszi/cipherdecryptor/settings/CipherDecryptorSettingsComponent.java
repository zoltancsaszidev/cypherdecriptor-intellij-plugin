package com.csaszi.cipherdecryptor.settings;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumn;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;

public class CipherDecryptorSettingsComponent {

    private final JPanel mainPanel;
    private final JBCheckBox enabledCheckBox;
    private final JBTable configTable;
    private final KeyStoreConfigTableModel tableModel;
    private final JButton addButton;
    private final JButton removeButton;
    private final JButton editButton;
    private final JButton testButton;

    public CipherDecryptorSettingsComponent() {
        enabledCheckBox = new JBCheckBox("Enable cipher decryption hints");

        // Create table model and table
        tableModel = new KeyStoreConfigTableModel();
        configTable = new JBTable(tableModel);
        configTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configTable.setRowHeight(25);
        configTable.getTableHeader().setReorderingAllowed(false);

        // Set column widths
        TableColumn fileNameCol = configTable.getColumnModel().getColumn(0);
        fileNameCol.setPreferredWidth(120);
        TableColumn locationCol = configTable.getColumnModel().getColumn(1);
        locationCol.setPreferredWidth(250);
        TableColumn aliasCol = configTable.getColumnModel().getColumn(2);
        aliasCol.setPreferredWidth(100);
        TableColumn passwordCol = configTable.getColumnModel().getColumn(3);
        passwordCol.setPreferredWidth(80);
        TableColumn secretCol = configTable.getColumnModel().getColumn(4);
        secretCol.setPreferredWidth(80);

        // Create buttons
        addButton = new JButton("+");
        addButton.setToolTipText("Add new keystore configuration");
        addButton.addActionListener(e -> addNewConfig());

        removeButton = new JButton("-");
        removeButton.setToolTipText("Remove selected keystore configuration");
        removeButton.addActionListener(e -> removeSelectedConfig());

        editButton = new JButton("Edit");
        editButton.setToolTipText("Edit selected keystore configuration");
        editButton.addActionListener(e -> editSelectedConfig());

        testButton = new JButton("Test");
        testButton.setToolTipText("Test selected keystore connection");
        testButton.addActionListener(e -> testSelectedConfig());

        // Button panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.add(addButton);
        buttonPanel.add(Box.createVerticalStrut(5));
        buttonPanel.add(removeButton);
        buttonPanel.add(Box.createVerticalStrut(10));
        buttonPanel.add(editButton);
        buttonPanel.add(Box.createVerticalStrut(5));
        buttonPanel.add(testButton);
        buttonPanel.add(Box.createVerticalGlue());

        // Table panel with scroll
        JScrollPane tableScrollPane = new JScrollPane(configTable);
        tableScrollPane.setPreferredSize(new Dimension(600, 200));

        JPanel tablePanel = new JPanel(new BorderLayout(5, 5));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        tablePanel.add(buttonPanel, BorderLayout.EAST);
        tablePanel.setBorder(BorderFactory.createTitledBorder("KeyStore Configurations"));

        // Help text
        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        helpPanel.add(new JBLabel("<html><i>KeyStore Filename is matched against files in your project's classpath (usually src/main/resources).</i></html>"));

        // Build the main panel
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(enabledCheckBox)
                .addSeparator()
                .addComponent(tablePanel)
                .addComponent(helpPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        mainPanel.setBorder(JBUI.Borders.empty(10));
    }

    private void addNewConfig() {
        KeyStoreConfig newConfig = showConfigDialog(null, "Add KeyStore Configuration");
        if (newConfig != null) {
            tableModel.addConfig(newConfig);
        }
    }

    private void removeSelectedConfig() {
        int selectedRow = configTable.getSelectedRow();
        if (selectedRow >= 0) {
            int confirm = JOptionPane.showConfirmDialog(mainPanel,
                    "Are you sure you want to remove this keystore configuration?",
                    "Confirm Removal",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                tableModel.removeConfig(selectedRow);
            }
        } else {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please select a configuration to remove.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void editSelectedConfig() {
        int selectedRow = configTable.getSelectedRow();
        if (selectedRow >= 0) {
            KeyStoreConfig existingConfig = tableModel.getConfig(selectedRow);
            KeyStoreConfig updatedConfig = showConfigDialog(existingConfig, "Edit KeyStore Configuration");
            if (updatedConfig != null) {
                tableModel.removeConfig(selectedRow);
                tableModel.addConfig(updatedConfig);
            }
        } else {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please select a configuration to edit.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private KeyStoreConfig showConfigDialog(KeyStoreConfig existing, String title) {
        // Create form fields
        JBTextField fileNameField = new JBTextField(20);
        TextFieldWithBrowseButton locationField = new TextFieldWithBrowseButton();
        JBTextField aliasField = new JBTextField(20);
        JBPasswordField passwordField = new JBPasswordField();
        JBPasswordField secretField = new JBPasswordField();

        // Setup file chooser
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("jks");
        descriptor.setTitle("Select Java KeyStore File");
        descriptor.setDescription("Choose a .jks file to use for decryption");
        locationField.addActionListener(e -> {
            VirtualFile[] files = FileChooserFactory.getInstance()
                    .createFileChooser(descriptor, null, locationField)
                    .choose(null);
            if (files.length > 0) {
                locationField.setText(files[0].getPath());
                // Auto-fill filename if empty
                if (fileNameField.getText().isEmpty()) {
                    fileNameField.setText(files[0].getName());
                }
            }
        });

        // Pre-populate if editing
        if (existing != null) {
            fileNameField.setText(existing.getKeystoreFileName());
            locationField.setText(existing.getKeystoreLocation());
            aliasField.setText(existing.getAlias());
            passwordField.setText(existing.getPassword());
            secretField.setText(existing.getSecret());
        }

        // Build dialog
        JPanel dialogPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("KeyStore Filename (for matching):"), fileNameField)
                .addLabeledComponent(new JBLabel("KeyStore Location:"), locationField)
                .addLabeledComponent(new JBLabel("Alias:"), aliasField)
                .addLabeledComponent(new JBLabel("Password:"), passwordField)
                .addLabeledComponent(new JBLabel("Secret:"), secretField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        DialogBuilder builder = new DialogBuilder(mainPanel);
        builder.setTitle(title);
        builder.setCenterPanel(dialogPanel);
        builder.addOkAction();
        builder.addCancelAction();

        if (builder.showAndGet()) {
            KeyStoreConfig config = new KeyStoreConfig();
            config.setKeystoreFileName(fileNameField.getText());
            config.setKeystoreLocation(locationField.getText());
            config.setAlias(aliasField.getText());
            config.setPassword(new String(passwordField.getPassword()));
            config.setSecret(new String(secretField.getPassword()));
            return config;
        }
        return null;
    }

    private void testSelectedConfig() {
        int selectedRow = configTable.getSelectedRow();
        if (selectedRow >= 0) {
            KeyStoreConfig config = tableModel.getConfig(selectedRow);
            testKeyStoreConnection(config);
        } else {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please select a configuration to test.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void testKeyStoreConnection(KeyStoreConfig config) {
        try {
            String location = config.getKeystoreLocation();
            String alias = config.getAlias();
            String password = config.getPassword();
            String secret = config.getSecret();

            if (location.isEmpty() || alias.isEmpty()) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Please fill in at least the KeyStore location and alias.",
                        "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Test loading the keystore
            com.csaszi.cipherdecryptor.crypto.KeyStoreDecryptor decryptor =
                    new com.csaszi.cipherdecryptor.crypto.KeyStoreDecryptor(location, alias, password, secret);
            
            if (decryptor.testConnection()) {
                JOptionPane.showMessageDialog(mainPanel,
                        "KeyStore connection successful! Key alias found.",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "KeyStore loaded but alias not found.",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Failed to load KeyStore: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return configTable;
    }

    public boolean isEnabled() {
        return enabledCheckBox.isSelected();
    }

    public void setEnabled(boolean enabled) {
        enabledCheckBox.setSelected(enabled);
    }

    @NotNull
    public List<KeyStoreConfig> getKeyStoreConfigs() {
        return tableModel.getConfigs();
    }

    public void setKeyStoreConfigs(List<KeyStoreConfig> configs) {
        tableModel.setConfigs(configs);
    }
}
