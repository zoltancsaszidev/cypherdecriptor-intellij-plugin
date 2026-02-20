package com.csaszi.cipherdecryptor.settings;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.XCollection;

@State(
        name = "com.csaszi.cipherdecryptor.settings.CipherDecryptorSettings",
        storages = @Storage("CipherDecryptorSettings.xml")
)
public class CipherDecryptorSettings implements PersistentStateComponent<CipherDecryptorSettings.State> {

    private State myState = new State();

    public static CipherDecryptorSettings getInstance() {
        return ApplicationManager.getApplication().getService(CipherDecryptorSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
        // Load secure values for each config
        for (int i = 0; i < myState.keyStoreConfigs.size(); i++) {
            KeyStoreConfigState config = myState.keyStoreConfigs.get(i);
            config.password = getSecureValue("keyStorePassword_" + i);
            config.secret = getSecureValue("keyStoreSecret_" + i);
        }
    }

    public boolean isEnabled() {
        return myState.enabled;
    }

    public void setEnabled(boolean enabled) {
        myState.enabled = enabled;
    }

    /**
     * Get all keystore configurations.
     */
    public List<KeyStoreConfig> getKeyStoreConfigs() {
        List<KeyStoreConfig> result = new ArrayList<>();
        for (KeyStoreConfigState configState : myState.keyStoreConfigs) {
            result.add(configState.toKeyStoreConfig());
        }
        return result;
    }

    /**
     * Set all keystore configurations.
     */
    public void setKeyStoreConfigs(List<KeyStoreConfig> configs) {
        // Clear old secure values
        for (int i = 0; i < myState.keyStoreConfigs.size(); i++) {
            clearSecureValue("keyStorePassword_" + i);
            clearSecureValue("keyStoreSecret_" + i);
        }

        myState.keyStoreConfigs.clear();
        for (int i = 0; i < configs.size(); i++) {
            KeyStoreConfig config = configs.get(i);
            KeyStoreConfigState configState = new KeyStoreConfigState();
            configState.keystoreFileName = config.getKeystoreFileName();
            configState.keystoreLocation = config.getKeystoreLocation();
            configState.alias = config.getAlias();
            configState.password = config.getPassword();
            configState.secret = config.getSecret();
            myState.keyStoreConfigs.add(configState);

            // Store secure values
            setSecureValue("keyStorePassword_" + i, config.getPassword());
            setSecureValue("keyStoreSecret_" + i, config.getSecret());
        }
    }

    /**
     * Find a keystore config by filename.
     */
    @Nullable
    public KeyStoreConfig findConfigByFileName(String fileName) {
        for (KeyStoreConfigState configState : myState.keyStoreConfigs) {
            if (configState.keystoreFileName != null && 
                configState.keystoreFileName.equals(fileName)) {
                return configState.toKeyStoreConfig();
            }
        }
        return null;
    }

    /**
     * Get the first configured keystore (for backwards compatibility).
     */
    @Nullable
    public KeyStoreConfig getFirstConfig() {
        if (!myState.keyStoreConfigs.isEmpty()) {
            return myState.keyStoreConfigs.get(0).toKeyStoreConfig();
        }
        return null;
    }

    public boolean isConfigured() {
        return !myState.keyStoreConfigs.isEmpty() && 
               myState.keyStoreConfigs.stream().anyMatch(c -> 
                   c.keystoreLocation != null && !c.keystoreLocation.isEmpty() &&
                   c.alias != null && !c.alias.isEmpty());
    }

    private CredentialAttributes createCredentialAttributes(String key) {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName("CipherDecryptor", key)
        );
    }

    private void setSecureValue(String key, String value) {
        CredentialAttributes attributes = createCredentialAttributes(key);
        Credentials credentials = new Credentials(key, value);
        PasswordSafe.getInstance().set(attributes, credentials);
    }

    private String getSecureValue(String key) {
        CredentialAttributes attributes = createCredentialAttributes(key);
        Credentials credentials = PasswordSafe.getInstance().get(attributes);
        return credentials != null ? credentials.getPasswordAsString() : "";
    }

    private void clearSecureValue(String key) {
        CredentialAttributes attributes = createCredentialAttributes(key);
        PasswordSafe.getInstance().set(attributes, null);
    }

    // Legacy getters for backwards compatibility
    @Deprecated
    public String getKeyStoreLocation() {
        KeyStoreConfig first = getFirstConfig();
        return first != null ? first.getKeystoreLocation() : "";
    }

    @Deprecated
    public String getKeyStoreAlias() {
        KeyStoreConfig first = getFirstConfig();
        return first != null ? first.getAlias() : "";
    }

    @Deprecated
    public String getKeyStorePassword() {
        KeyStoreConfig first = getFirstConfig();
        return first != null ? first.getPassword() : "";
    }

    @Deprecated
    public String getKeyStoreSecret() {
        KeyStoreConfig first = getFirstConfig();
        return first != null ? first.getSecret() : "";
    }

    public static class State {
        public boolean enabled = true;
        
        @XCollection(elementName = "keyStoreConfig")
        public List<KeyStoreConfigState> keyStoreConfigs = new ArrayList<>();
    }

    /**
     * State class for XML serialization of KeyStoreConfig.
     * Password and secret are stored securely via PasswordSafe.
     */
    public static class KeyStoreConfigState {
        public String keystoreFileName = "";
        public String keystoreLocation = "";
        public String alias = "";
        // Transient - stored in PasswordSafe
        public transient String password = "";
        public transient String secret = "";

        public KeyStoreConfig toKeyStoreConfig() {
            KeyStoreConfig config = new KeyStoreConfig();
            config.setKeystoreFileName(keystoreFileName);
            config.setKeystoreLocation(keystoreLocation);
            config.setAlias(alias);
            config.setPassword(password);
            config.setSecret(secret);
            return config;
        }
    }
}
