package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator that warns about infinite loops (while true without break/return).
 * <p>
 * Example:
 * <pre>
 * while true {               // Warning: Infinite loop
 *     println("forever")
 * }
 *
 * while true {               // OK - has break
 *     if done {
 *         break
 *     }
 * }
 *
 * while running {            // OK - condition is not literal true
 *     process()
 * }
 * </pre>
 */
public class KiteInfiniteLoopAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for WHILE keyword
        if (type != KiteTokenTypes.WHILE) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if condition is 'true' literal
        if (!hasLiteralTrueCondition(element)) {
            return;
        }

        // Check if body contains break or return
        if (hasBreakOrReturn(element)) {
            return;
        }

        // Infinite loop - no break or return
        holder.newAnnotation(HighlightSeverity.WARNING,
                        "Infinite loop: 'while true' has no 'break' or 'return' statement")
                .range(element)
                .create();
    }

    /**
     * Check if the while loop has a literal 'true' condition.
     */
    private boolean hasLiteralTrueCondition(PsiElement whileKeyword) {
        PsiElement next = KitePsiUtil.skipWhitespace(whileKeyword.getNextSibling());

        // Condition should be 'true' literal
        if (next != null && next.getNode() != null) {
            IElementType type = next.getNode().getElementType();
            return type == KiteTokenTypes.TRUE;
        }

        return false;
    }

    /**
     * Check if the while loop body contains a break or return statement.
     */
    private boolean hasBreakOrReturn(PsiElement whileKeyword) {
        // Find the opening brace
        PsiElement current = whileKeyword.getNextSibling();
        PsiElement lbrace = null;

        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE) {
                    lbrace = current;
                    break;
                }
            }
            current = current.getNextSibling();
        }

        if (lbrace == null) {
            return false;
        }

        // Search for break or return inside the body
        return searchForBreakOrReturn(lbrace.getNextSibling());
    }

    /**
     * Search for return (or "break" identifier) in the while body.
     * Note: Kite doesn't have a BREAK keyword, but check for "break" identifier just in case.
     */
    private boolean searchForBreakOrReturn(PsiElement element) {
        int braceDepth = 1; // We start after the opening brace
        PsiElement current = element;

        while (current != null && braceDepth > 0) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // Track brace depth
                if (type == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                } else if (type == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                    if (braceDepth == 0) {
                        break; // End of while body
                    }
                }

                // Found return
                if (type == KiteTokenTypes.RETURN) {
                    return true;
                }

                // Check for "break" identifier (Kite may not have break as keyword)
                if (type == KiteTokenTypes.IDENTIFIER && "break".equals(current.getText())) {
                    return true;
                }
            }
            current = current.getNextSibling();
        }

        return false;
    }
}
