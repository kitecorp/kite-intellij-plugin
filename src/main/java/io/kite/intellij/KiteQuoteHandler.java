package io.kite.intellij;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Handles auto-pairing of quotes in Kite files.
 * When the user types " or ', automatically insert the closing quote.
 */
public class KiteQuoteHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return Result.CONTINUE;
        }

        // Handle quote auto-pairing for " and '
        if (c == '"' || c == '\'') {
            // Check if we should auto-pair
            int offset = editor.getCaretModel().getOffset();
            CharSequence text = editor.getDocument().getCharsSequence();

            // Don't auto-pair if the next character is the same quote (user is closing an existing quote)
            if (offset < text.length() && text.charAt(offset) == c) {
                return Result.CONTINUE;
            }

            // Don't auto-pair if we're escaping the quote
            if (offset >= 2 && text.charAt(offset - 2) == '\\') {
                return Result.CONTINUE;
            }

            // Insert the closing quote
            EditorModificationUtil.insertStringAtCaret(editor, String.valueOf(c), false, false);
            return Result.STOP;
        }

        return Result.CONTINUE;
    }

    @Override
    public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return Result.CONTINUE;
        }

        // Handle typing a closing quote when one already exists - skip over it
        if (c == '"' || c == '\'') {
            int offset = editor.getCaretModel().getOffset();
            CharSequence text = editor.getDocument().getCharsSequence();

            // If the character at cursor is the same quote, skip over it instead of inserting
            if (offset < text.length() && text.charAt(offset) == c) {
                // Check if we're inside a quote pair (there's an opening quote before)
                // For simplicity, just skip over the closing quote
                editor.getCaretModel().moveToOffset(offset + 1);
                return Result.STOP;
            }
        }

        return Result.CONTINUE;
    }
}
