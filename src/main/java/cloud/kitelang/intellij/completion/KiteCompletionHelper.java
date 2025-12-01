package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Shared utility methods for Kite completion providers.
 * Contains common PSI navigation and type checking helpers.
 * <p>
 * Note: This class delegates to {@link KitePsiUtil} for whitespace handling.
 * Consider using KitePsiUtil directly for new code.
 */
public final class KiteCompletionHelper {

    private KiteCompletionHelper() {
        // Utility class - no instances
    }

    /**
     * Check if a PSI element is whitespace (including newlines).
     * Handles both IntelliJ platform whitespace and Kite language whitespace tokens.
     *
     * @see KitePsiUtil#isWhitespaceElement(PsiElement)
     */
    public static boolean isWhitespace(@Nullable PsiElement element) {
        return KitePsiUtil.isWhitespaceElement(element);
    }

    /**
     * Skip whitespace elements backward from the given element.
     * Returns the first non-whitespace element, or null if none found.
     *
     * @see KitePsiUtil#skipWhitespaceBackward(PsiElement)
     */
    @Nullable
    public static PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        return KitePsiUtil.skipWhitespaceBackward(element);
    }

    /**
     * Skip whitespace elements forward from the given element.
     * Returns the first non-whitespace element, or null if none found.
     *
     * @see KitePsiUtil#skipWhitespace(PsiElement)
     */
    @Nullable
    public static PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        return KitePsiUtil.skipWhitespace(element);
    }
}
