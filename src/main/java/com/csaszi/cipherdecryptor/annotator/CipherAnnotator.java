package com.csaszi.cipherdecryptor.annotator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import com.csaszi.cipherdecryptor.crypto.DecryptionService;
import com.csaszi.cipherdecryptor.settings.CipherDecryptorSettings;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Annotator that highlights {cipher} values and shows decrypted value in tooltip.
 * Provides visual feedback similar to how IntelliJ shows resolved property references.
 */
public class CipherAnnotator implements Annotator {

    private static final Pattern CIPHER_PATTERN = Pattern.compile("\\{cipher\\}[A-Za-z0-9+/=]+");
    
    // Text attributes for encrypted values - uses a subtle underline like property references
    private static final TextAttributesKey CIPHER_KEY = TextAttributesKey.createTextAttributesKey(
            "CIPHER_VALUE",
            DefaultLanguageHighlighterColors.METADATA
    );
    
    private static final TextAttributesKey CIPHER_RESOLVED_KEY = TextAttributesKey.createTextAttributesKey(
            "CIPHER_RESOLVED",
            DefaultLanguageHighlighterColors.CONSTANT
    );
    
    private static final TextAttributesKey CIPHER_ERROR_KEY = TextAttributesKey.createTextAttributesKey(
            "CIPHER_ERROR",
            DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        CipherDecryptorSettings settings = CipherDecryptorSettings.getInstance();
        if (!settings.isEnabled() || !settings.isConfigured()) {
            return;
        }

        // Only process leaf elements to avoid duplicate processing
        if (element.getFirstChild() != null) {
            return;
        }

        String text = element.getText();
        if (text == null || !text.contains("{cipher}")) {
            return;
        }

        Matcher matcher = CIPHER_PATTERN.matcher(text);
        while (matcher.find()) {
            String cipherValue = matcher.group();
            int startOffset = element.getTextRange().getStartOffset() + matcher.start();
            int endOffset = element.getTextRange().getStartOffset() + matcher.end();
            TextRange range = new TextRange(startOffset, endOffset);

            DecryptionService service = DecryptionService.getInstance();
            String decrypted = service.decrypt(cipherValue);

            boolean isError = decrypted != null && 
                    (decrypted.startsWith("[Error:") || decrypted.startsWith("[Decryption failed:"));
            boolean isSuccess = decrypted != null && !isError;

            // Build tooltip with decrypted value
            String tooltip;
            String message;
            TextAttributesKey attributesKey;
            
            if (isSuccess) {
                tooltip = "<html><b>🔓 Decrypted Value:</b><br/><code>" + escapeHtml(decrypted) + "</code></html>";
                message = "Encrypted value (hover for decrypted value)";
                attributesKey = CIPHER_RESOLVED_KEY;
            } else if (isError) {
                tooltip = "<html><b>⚠️ Decryption Failed:</b><br/><code>" + escapeHtml(decrypted) + "</code></html>";
                message = "Decryption failed";
                attributesKey = CIPHER_ERROR_KEY;
            } else {
                tooltip = "<html><b>🔒 Encrypted Value</b><br/>Configure KeyStore in Settings → Tools → Cipher Decryptor</html>";
                message = "Encrypted value (configure KeyStore to decrypt)";
                attributesKey = CIPHER_KEY;
            }

            holder.newAnnotation(HighlightSeverity.INFORMATION, message)
                    .range(range)
                    .tooltip(tooltip)
                    .textAttributes(attributesKey)
                    .create();
        }
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
