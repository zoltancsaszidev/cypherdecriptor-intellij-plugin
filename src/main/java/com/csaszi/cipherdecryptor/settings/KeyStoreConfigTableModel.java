package com.csaszi.cipherdecryptor.settings;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

/**
 * Table model for displaying and editing KeyStore configurations.
 */
public class KeyStoreConfigTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {
            "KeyStore Filename", "Location", "Alias", "Password", "Secret"
    };

    private static final Class<?>[] COLUMN_CLASSES = {
            String.class, String.class, String.class, String.class, String.class
    };

    private final List<KeyStoreConfig> configs = new ArrayList<>();

    @Override
    public int getRowCount() {
        return configs.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        KeyStoreConfig config = configs.get(rowIndex);
        switch (columnIndex) {
            case 0: return config.getKeystoreFileName();
            case 1: return config.getKeystoreLocation();
            case 2: return config.getAlias();
            case 3: return maskPassword(config.getPassword());
            case 4: return maskPassword(config.getSecret());
            default: return null;
        }
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        KeyStoreConfig config = configs.get(rowIndex);
        String strValue = value != null ? value.toString() : "";
        
        switch (columnIndex) {
            case 0: config.setKeystoreFileName(strValue); break;
            case 1: config.setKeystoreLocation(strValue); break;
            case 2: config.setAlias(strValue); break;
            case 3: 
                // Only update if not the masked value
                if (!strValue.equals(maskPassword(config.getPassword()))) {
                    config.setPassword(strValue);
                }
                break;
            case 4:
                // Only update if not the masked value
                if (!strValue.equals(maskPassword(config.getSecret()))) {
                    config.setSecret(strValue);
                }
                break;
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    private String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        return "••••••••";
    }

    public void addConfig(KeyStoreConfig config) {
        configs.add(config);
        fireTableRowsInserted(configs.size() - 1, configs.size() - 1);
    }

    public void removeConfig(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < configs.size()) {
            configs.remove(rowIndex);
            fireTableRowsDeleted(rowIndex, rowIndex);
        }
    }

    public KeyStoreConfig getConfig(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < configs.size()) {
            return configs.get(rowIndex);
        }
        return null;
    }

    public List<KeyStoreConfig> getConfigs() {
        return new ArrayList<>(configs);
    }

    public void setConfigs(List<KeyStoreConfig> newConfigs) {
        configs.clear();
        if (newConfigs != null) {
            for (KeyStoreConfig config : newConfigs) {
                configs.add(config.copy());
            }
        }
        fireTableDataChanged();
    }

    public void clear() {
        configs.clear();
        fireTableDataChanged();
    }
}
