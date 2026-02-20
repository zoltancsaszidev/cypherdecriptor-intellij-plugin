package com.csaszi.cipherdecryptor.actions;

import com.csaszi.cipherdecryptor.crypto.DecryptionService;
import com.csaszi.cipherdecryptor.crypto.KeyStoreDecryptor;
import com.csaszi.cipherdecryptor.settings.CipherDecryptorSettings;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Action to decrypt a cipher value at the cursor or in selection.
 */
public class DecryptCipherAction extends AnAction {

    private static final Pattern CIPHER_PATTERN = Pattern.compile("\\{cipher\\}[A-Za-z0-9+/=]+");

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        if (editor == null || project == null) {
            return;
        }

        CipherDecryptorSettings settings = CipherDecryptorSettings.getInstance();
        if (!settings.isConfigured()) {
            showNotification(project, 
                    "Cipher Decryptor not configured. Please configure the KeyStore in Settings → Tools → Cipher Decryptor.",
                    NotificationType.WARNING);
            return;
        }

        String cipherValue = findCipherValue(editor);
        if (cipherValue == null) {
            showNotification(project, 
                    "No {cipher} value found at cursor or in selection.",
                    NotificationType.INFORMATION);
            return;
        }

        DecryptionService service = DecryptionService.getInstance();
        String decrypted = service.decrypt(cipherValue);

        if (decrypted != null) {
            // Copy to clipboard
            CopyPasteManager.getInstance().setContents(new StringSelection(decrypted));
            
            showNotification(project, 
                    "Decrypted value copied to clipboard: " + maskForDisplay(decrypted),
                    NotificationType.INFORMATION);
        } else {
            showNotification(project, 
                    "Failed to decrypt the cipher value.",
                    NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean enabled = editor != null && CipherDecryptorSettings.getInstance().isConfigured();
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    private String findCipherValue(Editor editor) {
        SelectionModel selection = editor.getSelectionModel();
        
        // First, check if there's a selection
        if (selection.hasSelection()) {
            String selectedText = selection.getSelectedText();
            if (selectedText != null && KeyStoreDecryptor.isCipherValue(selectedText)) {
                return selectedText;
            }
            // Try to find cipher in selection
            Matcher matcher = CIPHER_PATTERN.matcher(selectedText != null ? selectedText : "");
            if (matcher.find()) {
                return matcher.group();
            }
        }

        // Otherwise, try to find cipher value at cursor
        int offset = editor.getCaretModel().getOffset();
        String documentText = editor.getDocument().getText();
        
        // Find the line containing the cursor
        int lineStart = documentText.lastIndexOf('\n', offset - 1) + 1;
        int lineEnd = documentText.indexOf('\n', offset);
        if (lineEnd == -1) {
            lineEnd = documentText.length();
        }
        
        String line = documentText.substring(lineStart, lineEnd);
        Matcher matcher = CIPHER_PATTERN.matcher(line);
        while (matcher.find()) {
            int matchStart = lineStart + matcher.start();
            int matchEnd = lineStart + matcher.end();
            if (offset >= matchStart && offset <= matchEnd) {
                return matcher.group();
            }
        }
        
        // If cursor is not on a cipher, return the first cipher on the line
        matcher.reset();
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }

    private String maskForDisplay(String value) {
        if (value == null || value.length() <= 5) {
            return "***";
        }
        if (value.startsWith("[")) {
            return value;
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 2);
    }

    private void showNotification(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Cipher Decryptor")
                .createNotification(content, type)
                .notify(project);
    }
}
