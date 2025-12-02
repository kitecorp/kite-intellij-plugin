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
 * Annotator that detects division by zero patterns like 'x / 0' or 'x % 0'.
 * Shows a warning because dividing by zero is usually an error.
 * <p>
 * Example:
 * <pre>
 * var x = 10 / 0   // Warning: Division by zero
 * var y = 10 % 0   // Warning: Modulo by zero
 * </pre>
 */
public class KiteDivisionByZeroAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for division or modulo operators
        if (type != KiteTokenTypes.DIVIDE && type != KiteTokenTypes.MODULO) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if the right side is a zero literal
        if (isDividingByZero(element)) {
            String operatorName = type == KiteTokenTypes.DIVIDE ? "Division" : "Modulo";
            holder.newAnnotation(HighlightSeverity.WARNING,
                            operatorName + " by zero")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if this division/modulo operator has a zero literal on the right side.
     */
    private boolean isDividingByZero(PsiElement operator) {
        // Get the element after the operator (skip whitespace)
        PsiElement rightSide = KitePsiUtil.skipWhitespace(operator.getNextSibling());
        if (rightSide == null || rightSide.getNode() == null) return false;

        IElementType rightType = rightSide.getNode().getElementType();

        // Check for negative zero: MINUS followed by NUMBER
        if (rightType == KiteTokenTypes.MINUS) {
            PsiElement afterMinus = KitePsiUtil.skipWhitespace(rightSide.getNextSibling());
            if (afterMinus != null && afterMinus.getNode() != null) {
                if (afterMinus.getNode().getElementType() == KiteTokenTypes.NUMBER) {
                    return isZeroLiteral(afterMinus.getText());
                }
            }
            return false;
        }

        // Check for plain NUMBER token
        if (rightType != KiteTokenTypes.NUMBER) {
            return false;
        }

        return isZeroLiteral(rightSide.getText());
    }

    /**
     * Check if the given text represents a zero literal.
     * Handles: 0, 0.0, 0.00, etc.
     */
    private boolean isZeroLiteral(String text) {
        if (text == null || text.isEmpty()) return false;

        try {
            double value = Double.parseDouble(text);
            return value == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
