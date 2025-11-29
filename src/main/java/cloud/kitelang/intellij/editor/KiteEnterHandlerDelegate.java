package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteLanguage;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enhanced Enter key behavior for Kite files.
 * <p>
 * Features:
 * - Auto-insert closing brace and proper indentation when pressing Enter after '{'
 * - Position cursor at the indented blank line between braces
 * - Handle Enter inside block comments with ' * ' continuation
 */
public class KiteEnterHandlerDelegate extends EnterHandlerDelegateAdapter {

    @Override
    public Result preprocessEnter(@NotNull PsiFile file,
                                  @NotNull Editor editor,
                                  @NotNull Ref<Integer> caretOffset,
                                  @NotNull Ref<Integer> caretAdvance,
                                  @NotNull DataContext dataContext,
                                  @Nullable EditorActionHandler originalHandler) {

        // Only handle Kite files
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return Result.Continue;
        }

        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        CharSequence text = document.getCharsSequence();

        // Handle Enter after opening brace: add closing brace and position cursor
        if (offset > 0 && text.charAt(offset - 1) == '{') {
            return handleEnterAfterOpenBrace(editor, document, offset, text);
        }

        // Handle Enter inside block comment: add ' * ' prefix
        if (isInsideBlockComment(text, offset)) {
            return handleEnterInBlockComment(editor, document, offset, text);
        }

        return Result.Continue;
    }

    /**
     * Handle pressing Enter right after an opening brace.
     * Creates a new line with proper indentation and adds closing brace.
     * <p>
     * Before: resource Type name {|
     * After:  resource Type name {
     * |
     * }
     */
    private Result handleEnterAfterOpenBrace(@NotNull Editor editor,
                                             @NotNull Document document,
                                             int offset,
                                             CharSequence text) {
        // Check if there's already a closing brace after the cursor
        boolean hasClosingBrace = false;
        int nextNonWhitespace = offset;
        while (nextNonWhitespace < text.length() &&
               Character.isWhitespace(text.charAt(nextNonWhitespace))) {
            nextNonWhitespace++;
        }
        if (nextNonWhitespace < text.length() && text.charAt(nextNonWhitespace) == '}') {
            hasClosingBrace = true;
        }

        // Calculate the current line's indentation
        int lineStart = findLineStart(text, offset);
        String currentIndent = getIndentation(text, lineStart);

        // Build the insertion: newline + indent + space for cursor + newline + closing brace
        String indent = currentIndent + "  ";  // Add 2 spaces for content indentation

        String insertion;
        if (hasClosingBrace) {
            // Just add newline and indent, closing brace already exists
            insertion = "\n" + indent;
        } else {
            // Add newline, indent, another newline, and closing brace
            insertion = "\n" + indent + "\n" + currentIndent + "}";
        }

        // Insert the text
        document.insertString(offset, insertion);

        // Position cursor at the indented line (after first newline + indent)
        editor.getCaretModel().moveToOffset(offset + 1 + indent.length());

        return Result.Stop;
    }

    /**
     * Handle pressing Enter inside a block comment.
     * Adds ' * ' prefix to the new line.
     * <p>
     * Before: /* comment text|
     * After:  /* comment text
     * * |
     */
    private Result handleEnterInBlockComment(@NotNull Editor editor,
                                             @NotNull Document document,
                                             int offset,
                                             CharSequence text) {
        // Calculate the current line's indentation
        int lineStart = findLineStart(text, offset);
        String currentIndent = getIndentation(text, lineStart);

        // Check if we're on a line that already starts with ' * '
        String lineContent = getLineContent(text, lineStart);
        String prefix;

        if (lineContent.trim().startsWith("*")) {
            // Continue with same indentation and ' * '
            prefix = "\n" + currentIndent + " * ";
        } else if (lineContent.trim().startsWith("/*")) {
            // First line of block comment, add ' * ' on next line
            prefix = "\n" + currentIndent + " * ";
        } else {
            // Just continue with ' * '
            prefix = "\n" + currentIndent + " * ";
        }

        document.insertString(offset, prefix);
        editor.getCaretModel().moveToOffset(offset + prefix.length());

        return Result.Stop;
    }

    /**
     * Check if the offset is inside a block comment.
     */
    private boolean isInsideBlockComment(CharSequence text, int offset) {
        // Simple heuristic: look backward for /* and forward for */
        boolean foundStart = false;
        int searchStart = Math.max(0, offset - 1000); // Limit search range

        for (int i = offset - 1; i >= searchStart; i--) {
            if (i > 0 && text.charAt(i - 1) == '/' && text.charAt(i) == '*') {
                foundStart = true;
                break;
            }
            if (i > 0 && text.charAt(i - 1) == '*' && text.charAt(i) == '/') {
                // Found end of comment before start - not inside
                return false;
            }
        }

        if (!foundStart) {
            return false;
        }

        // Check that we're before the closing */
        int searchEnd = Math.min(text.length(), offset + 1000);
        for (int i = offset; i < searchEnd - 1; i++) {
            if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                return true; // Found closing - we're inside the comment
            }
        }

        // No closing found within search range - might still be inside if comment is unclosed
        return true;
    }

    /**
     * Find the start of the line containing the given offset.
     */
    private int findLineStart(CharSequence text, int offset) {
        int pos = offset - 1;
        while (pos >= 0 && text.charAt(pos) != '\n') {
            pos--;
        }
        return pos + 1;
    }

    /**
     * Get the indentation (leading whitespace) of the line starting at lineStart.
     */
    private String getIndentation(CharSequence text, int lineStart) {
        StringBuilder indent = new StringBuilder();
        int pos = lineStart;
        while (pos < text.length() && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t')) {
            indent.append(text.charAt(pos));
            pos++;
        }
        return indent.toString();
    }

    /**
     * Get the content of the line starting at lineStart (until newline or end).
     */
    private String getLineContent(CharSequence text, int lineStart) {
        StringBuilder content = new StringBuilder();
        int pos = lineStart;
        while (pos < text.length() && text.charAt(pos) != '\n') {
            content.append(text.charAt(pos));
            pos++;
        }
        return content.toString();
    }
}
