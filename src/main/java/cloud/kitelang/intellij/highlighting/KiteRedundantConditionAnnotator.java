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
 * Annotator that detects redundant conditions like 'x && x' or 'x || x'.
 * Shows a warning suggesting simpler expressions.
 * <p>
 * Example:
 * <pre>
 * if x && x { ... }         // Can be simplified to 'x'
 * if enabled || enabled { } // Can be simplified to 'enabled'
 * </pre>
 */
public class KiteRedundantConditionAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for && or || operators
        if (type != KiteTokenTypes.AND && type != KiteTokenTypes.OR) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if both operands are the same identifier
        String leftName = getLeftOperand(element);
        String rightName = getRightOperand(element);

        if (leftName != null && rightName != null && leftName.equals(rightName)) {
            String operator = type == KiteTokenTypes.AND ? "&&" : "||";
            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Redundant condition: '" + leftName + " " + operator + " " + leftName + "' can be simplified to '" + leftName + "'")
                    .range(element)
                    .create();
        }
    }

    /**
     * Get the left operand of the logical operator (must be a simple identifier).
     */
    private String getLeftOperand(PsiElement operator) {
        PsiElement left = KitePsiUtil.skipWhitespaceBackward(operator.getPrevSibling());
        if (left != null && left.getNode() != null) {
            IElementType leftType = left.getNode().getElementType();
            if (leftType == KiteTokenTypes.IDENTIFIER) {
                // Make sure there's no other operator immediately before (to handle complex expressions)
                PsiElement beforeLeft = KitePsiUtil.skipWhitespaceBackward(left.getPrevSibling());
                if (beforeLeft != null && beforeLeft.getNode() != null) {
                    IElementType beforeType = beforeLeft.getNode().getElementType();
                    // If there's another logical operator before, this is a complex expression
                    if (beforeType == KiteTokenTypes.AND || beforeType == KiteTokenTypes.OR) {
                        return null;
                    }
                }
                return left.getText();
            }
        }
        return null;
    }

    /**
     * Get the right operand of the logical operator (must be a simple identifier).
     */
    private String getRightOperand(PsiElement operator) {
        PsiElement right = KitePsiUtil.skipWhitespace(operator.getNextSibling());
        if (right != null && right.getNode() != null) {
            IElementType rightType = right.getNode().getElementType();
            if (rightType == KiteTokenTypes.IDENTIFIER) {
                // Make sure there's no other operator immediately after (to handle complex expressions)
                PsiElement afterRight = KitePsiUtil.skipWhitespace(right.getNextSibling());
                if (afterRight != null && afterRight.getNode() != null) {
                    IElementType afterType = afterRight.getNode().getElementType();
                    // If there's another logical operator after, this is a complex expression
                    if (afterType == KiteTokenTypes.AND || afterType == KiteTokenTypes.OR) {
                        return null;
                    }
                }
                return right.getText();
            }
        }
        return null;
    }
}
