package com.csaszi.cipherdecryptor.hints;

import com.csaszi.cipherdecryptor.crypto.DecryptionService;
import com.csaszi.cipherdecryptor.settings.CipherDecryptorSettings;
import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;

/**
 * Provides inlay hints for cipher values, showing decrypted values inline
 * similar to how IntelliJ shows resolved property placeholders.
 */
@SuppressWarnings("UnstableApiUsage")
public class CipherInlayHintsProvider implements InlayHintsProvider<CipherInlayHintsProvider.Settings> {

    private static final Pattern CIPHER_PATTERN = Pattern.compile("\\{cipher\\}[A-Za-z0-9+/=]+");
    private static final SettingsKey<Settings> KEY = new SettingsKey<>("cipher.decryptor.hints");

    @Override
    public boolean isVisibleInSettings() {
        return true;
    }

    @NotNull
    @Override
    public SettingsKey<Settings> getKey() {
        return KEY;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
        return "Cipher decryption hints";
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return "database.password: {cipher}AQBHhXXXXXXXXXXXXXXXX";
    }

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull Settings settings) {
        return new ImmediateConfigurable() {
            @NotNull
            @Override
            public JComponent createComponent(@NotNull ChangeListener listener) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                
                JCheckBox showHints = new JCheckBox("Show decrypted values as hints", settings.showDecryptedValues);
                showHints.addChangeListener(e -> {
                    settings.showDecryptedValues = showHints.isSelected();
                    listener.settingsChanged();
                });
                
                JCheckBox showFullValue = new JCheckBox("Show full decrypted value (not masked)", settings.showFullValue);
                showFullValue.addChangeListener(e -> {
                    settings.showFullValue = showFullValue.isSelected();
                    listener.settingsChanged();
                });
                
                panel.add(showHints);
                panel.add(showFullValue);
                return panel;
            }
        };
    }

    @NotNull
    @Override
    public Settings createSettings() {
        return new Settings();
    }

    @Nullable
    @Override
    public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                                @NotNull Editor editor,
                                                @NotNull Settings settings,
                                                @NotNull InlayHintsSink sink) {
        CipherDecryptorSettings pluginSettings = CipherDecryptorSettings.getInstance();
        if (!pluginSettings.isEnabled() || !pluginSettings.isConfigured()) {
            return null;
        }

        return new FactoryInlayHintsCollector(editor) {
            @Override
            public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
                // Only process leaf elements to avoid duplicates
                if (element.getFirstChild() != null) {
                    return true;
                }
                
                String text = element.getText();
                if (text == null || !text.contains("{cipher}")) {
                    return true;
                }

                // Get project context for keystore matching
                Project project = file.getProject();

                Matcher matcher = CIPHER_PATTERN.matcher(text);
                while (matcher.find()) {
                    String cipherValue = matcher.group();
                    int startOffset = element.getTextRange().getStartOffset() + matcher.start();
                    
                    DecryptionService service = DecryptionService.getInstance();
                    String decrypted = service.decrypt(cipherValue, project);
                    
                    if (decrypted != null && settings.showDecryptedValues) {
                        PresentationFactory factory = getFactory();
                        
                        boolean isError = decrypted.startsWith("[Error:") || 
                                          decrypted.startsWith("[Decryption failed:");
                        
                        // Format the display value - show decrypted value before the cipher
                        String displayValue;
                        if (isError) {
                            displayValue = "⚠ " + decrypted + " ";
                        } else {
                            String value = settings.showFullValue ? decrypted : maskValue(decrypted);
                            displayValue = "🔓 \"" + value + "\" ← ";
                        }
                        
                        // Create text presentation with small text
                        InlayPresentation textPresentation = factory.smallText(displayValue);
                        
                        // Add a rounded background for visibility
                        InlayPresentation roundedPresentation = factory.roundWithBackground(textPresentation);
                        
                        // Add tooltip with full decrypted value and original cipher
                        String tooltip = isError 
                                ? "Decryption Error: " + decrypted 
                                : "🔓 Decrypted: \"" + decrypted + "\"\n🔒 Cipher: \"" + cipherValue + "\"\n\nClick to copy decrypted value";
                        InlayPresentation withTooltip = factory.withTooltip(tooltip, roundedPresentation);
                        
                        // Add click handler to copy decrypted value to clipboard
                        final String decryptedValue = decrypted;
                        InlayPresentation clickable = factory.onClick(withTooltip, MouseButton.Left, (event, point) -> {
                            if (!isError) {
                                StringSelection selection = new StringSelection(decryptedValue);
                                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                            }
                            return Unit.INSTANCE;
                        });
                        
                        // Add the inlay hint before the cipher value
                        sink.addInlineElement(startOffset, true, clickable, false);
                    }
                }
                
                return true;
            }
        };
    }

    /**
     * Mask the decrypted value for display in the editor.
     * Shows first 3 and last 2 characters, masks the rest.
     */
    private String maskValue(String value) {
        if (value == null) {
            return "***";
        }
        if (value.length() <= 5) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 2);
    }

    @Override
    public boolean isLanguageSupported(@NotNull Language language) {
        String langId = language.getID().toLowerCase();
        return langId.contains("properties") || 
               langId.contains("yaml") || 
               langId.contains("yml") ||
               langId.equals("text") ||
               langId.equals("plain_text");
    }

    public static class Settings {
        public boolean showDecryptedValues = true;
        public boolean showFullValue = false;
    }
}
