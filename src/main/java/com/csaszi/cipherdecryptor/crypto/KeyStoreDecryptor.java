package com.csaszi.cipherdecryptor.crypto;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Decrypts cipher values using a Java KeyStore.
 * Compatible with Spring Cloud Config Server encryption format.
 * 
 * This implementation matches exactly the behavior of Spring Security's RsaSecretEncryptor
 * and Spring's Encryptors.standard() for AES encryption.
 * 
 * Binary format of encrypted data:
 * - 2 bytes: length of RSA-encrypted secret (big-endian)
 * - N bytes: RSA-encrypted random secret
 * - Remaining: AES-encrypted data (IV prepended)
 */
public class KeyStoreDecryptor {

    // Default salt used by Spring Cloud Config
    private static final String DEFAULT_SALT = "deadbeef";
    
    // AES configuration matching Spring's Encryptors.standard()
    private static final String AES_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int AES_KEY_LENGTH = 256;
    private static final int IV_LENGTH = 16;
    private static final int PBE_ITERATIONS = 1024;
    
    private final String keyStoreLocation;
    private final String keyStoreAlias;
    private final String keyStorePassword;
    private final String keyStoreSecret;
    private KeyStore keyStore;
    private PrivateKey privateKey;

    public KeyStoreDecryptor(String keyStoreLocation, String keyStoreAlias,
                             String keyStorePassword, String keyStoreSecret) {
        this.keyStoreLocation = keyStoreLocation;
        this.keyStoreAlias = keyStoreAlias;
        this.keyStorePassword = keyStorePassword != null ? keyStorePassword : "";
        this.keyStoreSecret = keyStoreSecret != null ? keyStoreSecret : "";
    }

    /**
     * Test if the keystore can be loaded and the alias exists.
     */
    public boolean testConnection() {
        try {
            loadKeyStore();
            return keyStore.containsAlias(keyStoreAlias);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Decrypt a cipher value. The input should be the Base64 encoded value
     * (without the {cipher} prefix).
     */
    public String decrypt(String encryptedValue) throws Exception {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            throw new IllegalArgumentException("Encrypted value cannot be null or empty");
        }

        loadKeyStore();

        // Decode Base64
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedValue);

        // Decrypt using Spring Cloud Config format
        byte[] decrypted = decryptSpringCloudFormat(encryptedBytes);
        
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Decrypt using Spring Cloud Config / Spring Security RsaSecretEncryptor format.
     * 
     * Format:
     * [2 bytes: secret length][N bytes: RSA encrypted secret][remaining: AES encrypted data]
     * 
     * The AES encrypted data is in Spring's Encryptors.standard format:
     * [16 bytes: IV][encrypted content with PKCS5 padding]
     */
    private byte[] decryptSpringCloudFormat(byte[] encryptedBytes) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(encryptedBytes);
        
        // Read 2-byte length header (big-endian)
        int secretLength = readInt(input);
        
        if (secretLength <= 0 || secretLength > encryptedBytes.length - 2) {
            throw new IllegalArgumentException(
                "Invalid secret length in encrypted data: " + secretLength + 
                " (total bytes: " + encryptedBytes.length + ")");
        }
        
        // Read RSA-encrypted secret
        byte[] encryptedSecret = new byte[secretLength];
        int bytesRead = input.read(encryptedSecret);
        if (bytesRead != secretLength) {
            throw new IllegalArgumentException("Could not read encrypted secret");
        }
        
        // Decrypt the secret using RSA
        byte[] randomSecret = decryptWithRsa(encryptedSecret);
        
        // Read remaining bytes (AES encrypted data)
        byte[] aesEncryptedData = new byte[encryptedBytes.length - 2 - secretLength];
        input.read(aesEncryptedData);
        
        // Convert random secret to hex string (as Spring does)
        String hexSecret = bytesToHex(randomSecret);
        
        // Decrypt with AES using Spring's Encryptors.standard format
        return decryptWithAes(aesEncryptedData, hexSecret, DEFAULT_SALT);
    }

    /**
     * Read 2-byte big-endian integer (matching Spring's format).
     */
    private int readInt(ByteArrayInputStream input) {
        byte[] b = new byte[2];
        input.read(b, 0, 2);
        return ((b[0] & 0xFF) << 8) | (b[1] & 0xFF);
    }

    /**
     * Decrypt with RSA using the private key from keystore.
     * Uses RSA with PKCS1 padding (Spring's RsaAlgorithm.DEFAULT).
     */
    private byte[] decryptWithRsa(byte[] encryptedSecret) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedSecret);
    }

    /**
     * Decrypt with AES using Spring's Encryptors.standard format.
     * 
     * Spring's standard encryptor:
     * - Uses PBKDF2WithHmacSHA1 to derive key from password + salt
     * - AES/CBC/PKCS5Padding
     * - IV is prepended to ciphertext
     * - 1024 iterations for key derivation
     * - 256-bit AES key
     */
    private byte[] decryptWithAes(byte[] encryptedData, String password, String hexSalt) throws Exception {
        // Extract IV (first 16 bytes)
        if (encryptedData.length < IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted data too short for IV");
        }
        
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, IV_LENGTH);
        
        byte[] ciphertext = new byte[encryptedData.length - IV_LENGTH];
        System.arraycopy(encryptedData, IV_LENGTH, ciphertext, 0, ciphertext.length);
        
        // Derive AES key using PBKDF2 (matching Spring's implementation)
        byte[] salt = hexToBytes(hexSalt);
        SecretKey aesKey = deriveKey(password, salt);
        
        // Decrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
        
        return cipher.doFinal(ciphertext);
    }

    /**
     * Derive AES key using PBKDF2 (matching Spring Security's implementation).
     */
    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBE_ITERATIONS, AES_KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * Load the keystore and extract the private key.
     */
    private void loadKeyStore() throws Exception {
        if (keyStore != null && privateKey != null) {
            return; // Already loaded
        }

        File keyStoreFile = new File(keyStoreLocation);
        if (!keyStoreFile.exists()) {
            throw new IllegalArgumentException("KeyStore file not found: " + keyStoreLocation);
        }

        keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(keyStoreFile)) {
            keyStore.load(is, keyStorePassword.toCharArray());
        }

        if (!keyStore.containsAlias(keyStoreAlias)) {
            throw new IllegalArgumentException("Alias '" + keyStoreAlias + "' not found in keystore");
        }

        // Get the private key using the secret (key password)
        String keyPassword = keyStoreSecret.isEmpty() ? keyStorePassword : keyStoreSecret;
        Key key = keyStore.getKey(keyStoreAlias, keyPassword.toCharArray());
        
        if (key instanceof PrivateKey) {
            privateKey = (PrivateKey) key;
        } else {
            throw new IllegalArgumentException(
                "Alias '" + keyStoreAlias + "' does not contain a private key. Found: " + 
                (key != null ? key.getClass().getName() : "null"));
        }
    }

    /**
     * Decrypt a value that starts with {cipher} prefix.
     * 
     * @param value The value potentially containing {cipher} prefix
     * @return The decrypted value or original if not a cipher
     */
    public String decryptIfCipher(String value) {
        if (value == null || !value.startsWith("{cipher}")) {
            return value;
        }
        
        try {
            String cipherText = value.substring("{cipher}".length());
            return decrypt(cipherText);
        } catch (Exception e) {
            return "[Decryption failed: " + e.getMessage() + "]";
        }
    }

    /**
     * Check if a value is encrypted (starts with {cipher}).
     */
    public static boolean isCipherValue(String value) {
        return value != null && value.startsWith("{cipher}");
    }

    /**
     * Convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert hex string to bytes.
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
