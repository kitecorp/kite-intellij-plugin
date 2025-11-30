package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Shared utility methods for Kite completion providers.
 * Contains common PSI navigation and type checking helpers.
 */
public final class KiteCompletionHelper {

    private KiteCompletionHelper() {
        // Utility class - no instances
    }

    /**
     * Check if a PSI element is whitespace (including newlines).
     * Handles both IntelliJ platform whitespace and Kite language whitespace tokens.
     */
    public static boolean isWhitespace(@Nullable PsiElement element) {
        if (element == null || element.getNode() == null) return false;
        IElementType type = element.getNode().getElementType();
        return type == com.intellij.psi.TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Skip whitespace elements backward from the given element.
     * Returns the first non-whitespace element, or null if none found.
     */
    @Nullable
    public static PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element)) {
            element = element.getPrevSibling();
        }
        return element;
    }

    /**
     * Skip whitespace elements forward from the given element.
     * Returns the first non-whitespace element, or null if none found.
     */
    @Nullable
    public static PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element)) {
            element = element.getNextSibling();
        }
        return element;
    }
}
