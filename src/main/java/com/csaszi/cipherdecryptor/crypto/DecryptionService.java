package com.csaszi.cipherdecryptor.crypto;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.csaszi.cipherdecryptor.settings.CipherDecryptorSettings;
import com.csaszi.cipherdecryptor.settings.KeyStoreConfig;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Service that provides decryption functionality for the plugin.
 */
@Service(Service.Level.APP)
public final class DecryptionService {

    private final ConcurrentHashMap<String, String> decryptionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeyStoreDecryptor> decryptorCache = new ConcurrentHashMap<>();
    private String lastConfigHash;

    public static DecryptionService getInstance() {
        return ApplicationManager.getApplication().getService(DecryptionService.class);
    }

    /**
     * Decrypt a cipher value using project context to find the appropriate keystore.
     * 
     * @param cipherValue The value starting with {cipher}
     * @param project The project context for finding keystores on classpath
     * @return The decrypted value or an error message
     */
    @Nullable
    public String decrypt(String cipherValue, @Nullable Project project) {
        if (!KeyStoreDecryptor.isCipherValue(cipherValue)) {
            return null;
        }

        CipherDecryptorSettings settings = CipherDecryptorSettings.getInstance();
        if (!settings.isEnabled() || !settings.isConfigured()) {
            return null;
        }

        // Check if configuration changed
        String configHash = getConfigHash(settings);
        if (!configHash.equals(lastConfigHash)) {
            decryptorCache.clear();
            decryptionCache.clear();
            lastConfigHash = configHash;
        }

        // Find appropriate keystore config
        KeyStoreConfig config = findKeystoreConfig(settings, project);
        if (config == null) {
            return "[Error: No matching keystore config found]";
        }

        // Create cache key that includes the config
        String cacheKey = config.getKeystoreLocation() + "|" + cipherValue;
        
        // Check cache first
        String cached = decryptionCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            KeyStoreDecryptor decryptor = getOrCreateDecryptor(config);
            String decrypted = decryptor.decryptIfCipher(cipherValue);
            decryptionCache.put(cacheKey, decrypted);
            return decrypted;
        } catch (Exception e) {
            String error = "[Error: " + e.getMessage() + "]";
            decryptionCache.put(cacheKey, error);
            return error;
        }
    }

    /**
     * Legacy decrypt method (uses first available config).
     */
    @Nullable
    public String decrypt(String cipherValue) {
        return decrypt(cipherValue, null);
    }

    /**
     * Find the appropriate keystore config for the given project.
     */
    @Nullable
    private KeyStoreConfig findKeystoreConfig(CipherDecryptorSettings settings, @Nullable Project project) {
        List<KeyStoreConfig> configs = settings.getKeyStoreConfigs();
        
        if (configs.isEmpty()) {
            return null;
        }

        // If only one config or no project context, use first config
        if (configs.size() == 1 || project == null) {
            return configs.get(0);
        }

        // Try to find a matching keystore file on the project classpath
        for (KeyStoreConfig config : configs) {
            if (config.getKeystoreFileName() != null && !config.getKeystoreFileName().isEmpty()) {
                if (isKeystoreOnClasspath(project, config.getKeystoreFileName())) {
                    return config;
                }
            }
        }

        // Fallback to first config if no match found
        return configs.get(0);
    }

    /**
     * Check if a keystore file exists on the project's classpath.
     */
    private boolean isKeystoreOnClasspath(@NotNull Project project, @NotNull String keystoreFileName) {
        VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        
        for (VirtualFile sourceRoot : sourceRoots) {
            VirtualFile found = VfsUtil.findRelativeFile(sourceRoot, keystoreFileName);
            if (found != null && found.exists()) {
                return true;
            }
            
            // Also check in resources folders
            if (sourceRoot.getName().equals("resources") || 
                sourceRoot.getPath().contains("/resources")) {
                found = sourceRoot.findChild(keystoreFileName);
                if (found != null && found.exists()) {
                    return true;
                }
            }
        }
        
        // Recursive search in source roots
        for (VirtualFile sourceRoot : sourceRoots) {
            if (findFileRecursively(sourceRoot, keystoreFileName) != null) {
                return true;
            }
        }
        
        return false;
    }

    @Nullable
    private VirtualFile findFileRecursively(VirtualFile dir, String fileName) {
        VirtualFile found = dir.findChild(fileName);
        if (found != null) {
            return found;
        }
        
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                found = findFileRecursively(child, fileName);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }

    private KeyStoreDecryptor getOrCreateDecryptor(KeyStoreConfig config) throws Exception {
        String key = config.getKeystoreLocation() + "|" + config.getAlias();
        
        KeyStoreDecryptor decryptor = decryptorCache.get(key);
        if (decryptor == null) {
            decryptor = new KeyStoreDecryptor(
                    config.getKeystoreLocation(),
                    config.getAlias(),
                    config.getPassword(),
                    config.getSecret()
            );
            decryptorCache.put(key, decryptor);
        }
        return decryptor;
    }

    /**
     * Clear the decryption cache.
     */
    public void clearCache() {
        decryptionCache.clear();
        decryptorCache.clear();
        lastConfigHash = null;
    }

    private String getConfigHash(CipherDecryptorSettings settings) {
        StringBuilder hash = new StringBuilder();
        for (KeyStoreConfig config : settings.getKeyStoreConfigs()) {
            hash.append(config.getKeystoreLocation()).append("|")
                .append(config.getAlias()).append("|")
                .append(config.getPassword().hashCode()).append("|")
                .append(config.getSecret().hashCode()).append(";");
        }
        return hash.toString();
    }

    /**
     * Check if a value is a cipher value.
     */
    public boolean isCipherValue(String value) {
        return KeyStoreDecryptor.isCipherValue(value);
    }
}
