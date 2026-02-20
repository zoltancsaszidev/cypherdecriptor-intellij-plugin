package com.csaszi.cipherdecryptor.settings;

import java.util.List;

import javax.swing.JComponent;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.options.Configurable;

public class CipherDecryptorConfigurable implements Configurable {

    private CipherDecryptorSettingsComponent settingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Cipher Decryptor";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return settingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        settingsComponent = new CipherDecryptorSettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        CipherDecryptorSettings settings = CipherDecryptorSettings.getInstance();
        
        if (settingsComponent.isEnabled() != settings.isEnabled()) {
            return true;
        }

        List<KeyStoreConfig> currentConfigs = settingsComponent.getKeyStoreConfigs();
        List<KeyStoreConfig> savedConfigs = settings.getKeyStoreConfigs();

        if (currentConfigs.size() != savedConfigs.size()) {
            return true;
        }

        for (int i = 0; i < currentConfigs.size(); i++) {
            if (!currentConfigs.get(i).equals(savedConfigs.get(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void apply() {
        CipherDecryptorSettings settings = CipherDecryptorSettings.getInstance();
        
        settings.setEnabled(settingsComponent.isEnabled());
        settings.setKeyStoreConfigs(settingsComponent.getKeyStoreConfigs());
        
        // Clear decryption cache when settings change
        com.csaszi.cipherdecryptor.crypto.DecryptionService.getInstance().clearCache();
    }

    @Override
    public void reset() {
        CipherDecryptorSettings settings = CipherDecryptorSettings.getInstance();
        
        settingsComponent.setEnabled(settings.isEnabled());
        settingsComponent.setKeyStoreConfigs(settings.getKeyStoreConfigs());
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }
}
