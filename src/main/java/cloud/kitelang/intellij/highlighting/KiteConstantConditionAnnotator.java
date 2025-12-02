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
 * Annotator that detects constant conditions like 'if true', 'if false', 'if 1 == 1', etc.
 * Shows a warning because these conditions don't change at runtime.
 * <p>
 * Example:
 * <pre>
 * if true { ... }      // Warning: Constant condition 'true' is always true
 * if 1 == 1 { ... }    // Warning: Constant condition '1 == 1' is always true
 * if !false { ... }    // Warning: Constant condition '!false' is always true
 * </pre>
 */
public class KiteConstantConditionAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check for TRUE or FALSE in condition position
        if (type == KiteTokenTypes.TRUE || type == KiteTokenTypes.FALSE) {
            if (isAfterConditionalKeyword(element)) {
                boolean value = type == KiteTokenTypes.TRUE;
                String alwaysValue = value ? "always true" : "always false";
                holder.newAnnotation(HighlightSeverity.WARNING,
                                "Constant condition: '" + element.getText() + "' is " + alwaysValue)
                        .range(element)
                        .create();
            }
            return;
        }

        // Check for NOT followed by boolean literal after conditional keyword
        if (type == KiteTokenTypes.NOT) {
            if (isAfterConditionalKeyword(element)) {
                PsiElement next = KitePsiUtil.skipWhitespace(element.getNextSibling());
                if (next != null && next.getNode() != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.TRUE) {
                        holder.newAnnotation(HighlightSeverity.WARNING,
                                        "Constant condition: '!true' is always false")
                                .range(element)
                                .create();
                    } else if (nextType == KiteTokenTypes.FALSE) {
                        holder.newAnnotation(HighlightSeverity.WARNING,
                                        "Constant condition: '!false' is always true")
                                .range(element)
                                .create();
                    }
                }
            }
            return;
        }

        // Check for NUMBER literals in comparison position after conditional keyword
        if (type == KiteTokenTypes.NUMBER) {
            if (isAfterConditionalKeyword(element)) {
                ConstantResult result = checkLiteralComparison(element, type);
                if (result != null) {
                    String alwaysValue = result.isTrue ? "always true" : "always false";
                    holder.newAnnotation(HighlightSeverity.WARNING,
                                    "Constant condition: '" + result.expression + "' is " + alwaysValue)
                            .range(element)
                            .create();
                }
            }
        }
    }

    /**
     * Check if the element is immediately after an IF or WHILE keyword.
     */
    private boolean isAfterConditionalKeyword(PsiElement element) {
        PsiElement prev = KitePsiUtil.skipWhitespaceBackward(element.getPrevSibling());
        if (prev == null || prev.getNode() == null) return false;

        IElementType prevType = prev.getNode().getElementType();
        return prevType == KiteTokenTypes.IF || prevType == KiteTokenTypes.WHILE;
    }

    /**
     * Result of evaluating a constant condition.
     */
    private static class ConstantResult {
        final String expression;
        final boolean isTrue;

        ConstantResult(String expression, boolean isTrue) {
            this.expression = expression;
            this.isTrue = isTrue;
        }
    }

    /**
     * Check if this literal is followed by a comparison operator and another literal.
     */
    @Nullable
    private ConstantResult checkLiteralComparison(PsiElement leftElement, IElementType leftType) {
        String leftValue = leftElement.getText();

        PsiElement operator = KitePsiUtil.skipWhitespace(leftElement.getNextSibling());
        if (operator == null || operator.getNode() == null) return null;

        IElementType opType = operator.getNode().getElementType();
        if (!isComparisonOperator(opType)) return null;

        PsiElement rightElement = KitePsiUtil.skipWhitespace(operator.getNextSibling());
        if (rightElement == null || rightElement.getNode() == null) return null;

        IElementType rightType = rightElement.getNode().getElementType();
        if (!isLiteralToken(rightType)) return null;

        String rightValue = rightElement.getText();
        String expression = leftValue + " " + operator.getText() + " " + rightValue;

        // Evaluate the comparison
        Boolean result = evaluateComparison(leftValue, leftType, opType, rightValue, rightType);
        if (result != null) {
            return new ConstantResult(expression, result);
        }

        return null;
    }

    /**
     * Evaluate a comparison between two literals.
     */
    @Nullable
    private Boolean evaluateComparison(String leftValue, IElementType leftType,
                                       IElementType opType,
                                       String rightValue, IElementType rightType) {
        // Same type comparisons
        if (leftType == rightType) {
            if (leftType == KiteTokenTypes.NUMBER) {
                try {
                    double left = Double.parseDouble(leftValue);
                    double right = Double.parseDouble(rightValue);
                    return evaluateNumericComparison(left, opType, right);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            // String literal comparison is not supported in this initial implementation
        }

        return null;
    }

    /**
     * Evaluate a numeric comparison.
     */
    @Nullable
    private Boolean evaluateNumericComparison(double left, IElementType opType, double right) {
        if (opType == KiteTokenTypes.EQ) {
            return left == right;
        }
        if (opType == KiteTokenTypes.NE) {
            return left != right;
        }
        if (opType == KiteTokenTypes.LT) {
            return left < right;
        }
        if (opType == KiteTokenTypes.GT) {
            return left > right;
        }
        if (opType == KiteTokenTypes.LE) {
            return left <= right;
        }
        if (opType == KiteTokenTypes.GE) {
            return left >= right;
        }
        return null;
    }

    /**
     * Check if the element type is a literal token (numbers only for now).
     */
    private boolean isLiteralToken(IElementType type) {
        return type == KiteTokenTypes.NUMBER;
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
}
