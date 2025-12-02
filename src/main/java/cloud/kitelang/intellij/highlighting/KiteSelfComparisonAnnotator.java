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
 * Annotator that detects self-comparison patterns like 'x == x', 'x != x', etc.
 * Shows a warning because self-comparison usually indicates a mistake.
 * <p>
 * Example:
 * <pre>
 * var x = 5
 * if (x == x) { ... }   // Warning: Self-comparison of 'x'
 * </pre>
 */
public class KiteSelfComparisonAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        // Only check IDENTIFIER tokens
        if (element.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check for pattern: IDENTIFIER comparison_op IDENTIFIER (same name)
        if (isSelfComparison(element)) {
            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Self-comparison of '" + element.getText() + "'")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if this identifier is the left-hand side of a self-comparison.
     */
    private boolean isSelfComparison(PsiElement identifier) {
        // Get next element after identifier
        PsiElement next = KitePsiUtil.skipWhitespace(identifier.getNextSibling());
        if (next == null || next.getNode() == null) return false;

        IElementType opType = next.getNode().getElementType();

        // Must be a comparison operator
        if (!isComparisonOperator(opType)) {
            return false;
        }

        // Get the right-hand side
        PsiElement rightSide = KitePsiUtil.skipWhitespace(next.getNextSibling());
        if (rightSide == null || rightSide.getNode() == null) return false;

        // Right side must be a single IDENTIFIER with the same name
        if (rightSide.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return false;
        }

        // Check if right side is followed by an operator (then it's not a simple self-comparison)
        PsiElement afterRight = KitePsiUtil.skipWhitespace(rightSide.getNextSibling());
        if (afterRight != null && afterRight.getNode() != null) {
            IElementType afterType = afterRight.getNode().getElementType();
            // If followed by arithmetic operator, it's a computation like x == x + 1
            if (isArithmeticOperator(afterType)) {
                return false;
            }
        }

        // Check if both identifiers have the same name
        return identifier.getText().equals(rightSide.getText());
    }

    /**
     * Check if the element type is a comparison operator.
     */
    private boolean isComparisonOperator(IElementType type) {
        return type == KiteTokenTypes.EQ ||   // ==
               type == KiteTokenTypes.NE ||   // !=
               type == KiteTokenTypes.LT ||   // <
               type == KiteTokenTypes.GT ||   // >
               type == KiteTokenTypes.LE ||   // <=
               type == KiteTokenTypes.GE;     // >=
    }

    /**
     * Check if the element type is an arithmetic operator.
     */
    private boolean isArithmeticOperator(IElementType type) {
        return type == KiteTokenTypes.PLUS ||
               type == KiteTokenTypes.MINUS ||
               type == KiteTokenTypes.MULTIPLY ||
               type == KiteTokenTypes.DIVIDE ||
               type == KiteTokenTypes.MODULO ||
               type == KiteTokenTypes.DOT ||
               type == KiteTokenTypes.LBRACK;
    }
}
