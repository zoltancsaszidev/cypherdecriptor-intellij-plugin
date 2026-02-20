package com.csaszi.cipherdecryptor.settings;

import java.util.Objects;

/**
 * Represents a single KeyStore configuration.
 * The keystoreFileName is used to match against project resources.
 */
public class KeyStoreConfig {
    
    private String keystoreFileName = "";
    private String keystoreLocation = "";
    private String alias = "";
    private String password = "";
    private String secret = "";

    public KeyStoreConfig() {
    }

    public KeyStoreConfig(String keystoreFileName, String keystoreLocation, String alias, String password, String secret) {
        this.keystoreFileName = keystoreFileName;
        this.keystoreLocation = keystoreLocation;
        this.alias = alias;
        this.password = password;
        this.secret = secret;
    }

    public KeyStoreConfig copy() {
        return new KeyStoreConfig(keystoreFileName, keystoreLocation, alias, password, secret);
    }

    public String getKeystoreFileName() {
        return keystoreFileName;
    }

    public void setKeystoreFileName(String keystoreFileName) {
        this.keystoreFileName = keystoreFileName;
    }

    public String getKeystoreLocation() {
        return keystoreLocation;
    }

    public void setKeystoreLocation(String keystoreLocation) {
        this.keystoreLocation = keystoreLocation;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean isConfigured() {
        return keystoreLocation != null && !keystoreLocation.isEmpty()
                && alias != null && !alias.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyStoreConfig that = (KeyStoreConfig) o;
        return Objects.equals(keystoreFileName, that.keystoreFileName) &&
               Objects.equals(keystoreLocation, that.keystoreLocation) &&
               Objects.equals(alias, that.alias) &&
               Objects.equals(password, that.password) &&
               Objects.equals(secret, that.secret);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keystoreFileName, keystoreLocation, alias, password, secret);
    }
}
