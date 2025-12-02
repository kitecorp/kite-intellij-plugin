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
import org.jetbrains.annotations.Nullable;

/**
 * Annotator that suggests simplifying negated comparisons.
 * Shows a hint for patterns like !(x == y) which can be simplified.
 * <p>
 * Example:
 * <pre>
 * if !(x == y) { }    // Hint: Can be simplified to 'x != y'
 * if !(a > b) { }     // Hint: Can be simplified to 'a <= b'
 * if !(count < 10) { }// Hint: Can be simplified to 'count >= 10'
 * </pre>
 */
public class KiteNegatedComparisonAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for NOT operator
        if (type != KiteTokenTypes.NOT) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if followed by LPAREN
        PsiElement next = KitePsiUtil.skipWhitespace(element.getNextSibling());
        if (next == null || next.getNode() == null ||
            next.getNode().getElementType() != KiteTokenTypes.LPAREN) {
            return;
        }

        // Find comparison operator inside the parens
        ComparisonInfo info = findComparisonInParens(next);
        if (info != null) {
            String simplifiedOp = getSimplifiedOperator(info.operator);
            String suggestion = info.leftOperand + " " + simplifiedOp + " " + info.rightOperand;

            holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                            "Can be simplified to '" + suggestion + "'")
                    .range(element)
                    .create();
        }
    }

    /**
     * Find comparison operator and operands inside parentheses.
     */
    @Nullable
    private ComparisonInfo findComparisonInParens(PsiElement lparen) {
        PsiElement current = lparen.getNextSibling();
        String leftOperand = null;
        IElementType operator = null;
        String rightOperand = null;

        while (current != null) {
            if (current.getNode() == null) {
                current = current.getNextSibling();
                continue;
            }

            IElementType type = current.getNode().getElementType();

            // Stop at closing paren
            if (type == KiteTokenTypes.RPAREN) {
                break;
            }

            // Record left operand (first identifier or number)
            if (leftOperand == null &&
                (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.NUMBER)) {
                leftOperand = current.getText();
            }
            // Record comparison operator
            else if (operator == null && isComparisonOperator(type)) {
                operator = type;
            }
            // Record right operand
            else if (operator != null && rightOperand == null &&
                     (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.NUMBER)) {
                rightOperand = current.getText();
            }

            current = current.getNextSibling();
        }

        if (leftOperand != null && operator != null && rightOperand != null) {
            return new ComparisonInfo(leftOperand, operator, rightOperand);
        }
        return null;
    }

    /**
     * Check if this is a comparison operator.
     */
    private boolean isComparisonOperator(IElementType type) {
        return type == KiteTokenTypes.EQ ||
               type == KiteTokenTypes.NE ||
               type == KiteTokenTypes.LT ||
               type == KiteTokenTypes.GT ||
               type == KiteTokenTypes.LE ||
               type == KiteTokenTypes.GE;
    }

    /**
     * Get the simplified (negated) operator.
     */
    private String getSimplifiedOperator(IElementType operator) {
        if (operator == KiteTokenTypes.EQ) return "!=";
        if (operator == KiteTokenTypes.NE) return "==";
        if (operator == KiteTokenTypes.LT) return ">=";
        if (operator == KiteTokenTypes.GT) return "<=";
        if (operator == KiteTokenTypes.LE) return ">";
        if (operator == KiteTokenTypes.GE) return "<";
        return "?";
    }

    /**
     * Helper class to hold comparison information.
     */
    private static class ComparisonInfo {
        final String leftOperand;
        final IElementType operator;
        final String rightOperand;

        ComparisonInfo(String left, IElementType op, String right) {
            this.leftOperand = left;
            this.operator = op;
            this.rightOperand = right;
        }
    }
}
