package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for common PSI navigation operations.
 * <p>
 * This class provides low-level utilities for navigating the PSI (Program Structure Interface)
 * tree in Kite files. These utilities are used by other helpers and the annotator.
 * <p>
 * <b>Whitespace Handling:</b><br>
 * Kite files can contain both IntelliJ platform whitespace tokens ({@code TokenType.WHITE_SPACE})
 * and Kite-specific whitespace tokens ({@code WHITESPACE}, {@code NL}, {@code NEWLINE}).
 * Always use the methods in this class to skip whitespace to handle both types.
 * <p>
 * <b>Key Methods:</b>
 * <ul>
 *   <li>{@link #skipWhitespace} - Skip forward past whitespace tokens</li>
 *   <li>{@link #skipWhitespaceBackward} - Skip backward past whitespace tokens</li>
 *   <li>{@link #isWhitespace} - Check if a token type is whitespace</li>
 *   <li>{@link #isDescendantOf} - Check parent-child relationship</li>
 *   <li>{@link #findFirstChildOfType} - Find child by element type</li>
 *   <li>{@link #getElementType} - Safely get element type (null-safe)</li>
 * </ul>
 *
 * @see KiteIdentifierContextHelper for identifier-specific navigation
 * @see KiteImportValidationHelper for import statement navigation
 */
public final class KitePsiUtil {

    private KitePsiUtil() {
        // Utility class
    }

    /**
     * Skip whitespace tokens forward.
     *
     * @param element The starting element
     * @return The first non-whitespace element, or null if none found
     */
    @Nullable
    public static PsiElement skipWhitespace(@Nullable PsiElement element) {
        while (element != null && element.getNode() != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    /**
     * Skip whitespace tokens backward.
     *
     * @param element The starting element
     * @return The first non-whitespace element going backward, or null if none found
     */
    @Nullable
    public static PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && element.getNode() != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    /**
     * Check if element type is whitespace (including newlines).
     *
     * @param type The element type to check
     * @return true if the type represents whitespace
     */
    public static boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Check if element is a descendant of ancestor.
     *
     * @param element  The potential descendant
     * @param ancestor The potential ancestor
     * @return true if element is a descendant of ancestor
     */
    public static boolean isDescendantOf(PsiElement element, PsiElement ancestor) {
        PsiElement current = element;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Find the first child element of the given type.
     *
     * @param parent The parent element
     * @param type   The element type to find
     * @return The first child with the given type, or null if not found
     */
    @Nullable
    public static PsiElement findFirstChildOfType(PsiElement parent, IElementType type) {
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == type) {
                return child;
            }
        }
        return null;
    }

    /**
     * Find the next sibling of the given type.
     *
     * @param element The starting element
     * @param type    The element type to find
     * @return The next sibling with the given type, or null if not found
     */
    @Nullable
    public static PsiElement findNextSiblingOfType(PsiElement element, IElementType type) {
        PsiElement sibling = element.getNextSibling();
        while (sibling != null) {
            if (sibling.getNode() != null && sibling.getNode().getElementType() == type) {
                return sibling;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    /**
     * Find the previous sibling of the given type.
     *
     * @param element The starting element
     * @param type    The element type to find
     * @return The previous sibling with the given type, or null if not found
     */
    @Nullable
    public static PsiElement findPrevSiblingOfType(PsiElement element, IElementType type) {
        PsiElement sibling = element.getPrevSibling();
        while (sibling != null) {
            if (sibling.getNode() != null && sibling.getNode().getElementType() == type) {
                return sibling;
            }
            sibling = sibling.getPrevSibling();
        }
        return null;
    }

    /**
     * Get the element type safely.
     *
     * @param element The element
     * @return The element type, or null if the element or its node is null
     */
    @Nullable
    public static IElementType getElementType(@Nullable PsiElement element) {
        if (element == null || element.getNode() == null) {
            return null;
        }
        return element.getNode().getElementType();
    }

    /**
     * Check if element has the specified type.
     *
     * @param element The element to check
     * @param type    The expected type
     * @return true if element has the specified type
     */
    public static boolean hasType(@Nullable PsiElement element, IElementType type) {
        return element != null && element.getNode() != null &&
               element.getNode().getElementType() == type;
    }
}
