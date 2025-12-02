package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator that detects useless expressions (statements with no side effects).
 * Shows a warning when expressions like 'x + 1' appear without being assigned, returned, or used.
 * <p>
 * Example:
 * <pre>
 * fun test() {
 *     x + 1       // Warning: 'x + 1' has no effect
 *     a - b       // Warning: has no effect
 * }
 * </pre>
 */
public class KiteUselessExpressionAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for arithmetic operators
        if (!isArithmeticOperator(type)) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if this expression is useless (not assigned, not returned, not part of function call)
        if (isUselessExpression(element)) {
            String expression = buildExpressionText(element);
            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Expression '" + expression + "' has no effect. Did you forget to assign?")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if this is an arithmetic operator.
     */
    private boolean isArithmeticOperator(IElementType type) {
        return type == KiteTokenTypes.PLUS ||
               type == KiteTokenTypes.MINUS ||
               type == KiteTokenTypes.MULTIPLY ||
               type == KiteTokenTypes.DIVIDE ||
               type == KiteTokenTypes.MODULO;
    }

    /**
     * Check if this expression is useless (not part of assignment, return, or function call).
     */
    private boolean isUselessExpression(PsiElement operator) {
        // Must be inside a function body
        if (!isInsideFunctionBody(operator)) {
            return false;
        }

        // Check if there's an assignment operator before this expression on the same line
        if (hasAssignmentBefore(operator)) {
            return false;
        }

        // Check if preceded by RETURN keyword
        if (hasPrecedingReturn(operator)) {
            return false;
        }

        // Check if inside parentheses (could be function argument)
        if (isInsideParentheses(operator)) {
            return false;
        }

        return true;
    }

    /**
     * Check if we're inside a function body.
     */
    private boolean isInsideFunctionBody(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent.getNode() != null &&
                parent.getNode().getElementType() == KiteElementTypes.FUNCTION_DECLARATION) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Check if there's an assignment operator before this expression on the same line.
     */
    private boolean hasAssignmentBefore(PsiElement operator) {
        PsiElement current = operator.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // Hit a newline - stop searching
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    return false;
                }

                // Found assignment - this expression is being assigned
                if (isAssignmentOperator(type)) {
                    return true;
                }

                // Found VAR - this is a variable declaration
                if (type == KiteTokenTypes.VAR) {
                    return true;
                }
            }
            current = current.getPrevSibling();
        }
        return false;
    }

    /**
     * Check if there's a RETURN keyword before this expression.
     */
    private boolean hasPrecedingReturn(PsiElement operator) {
        PsiElement current = operator.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // Hit a newline - stop searching
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    return false;
                }

                // Found RETURN keyword
                if (type == KiteTokenTypes.RETURN) {
                    return true;
                }
            }
            current = current.getPrevSibling();
        }
        return false;
    }

    /**
     * Check if we're inside parentheses (could be function call argument).
     */
    private boolean isInsideParentheses(PsiElement operator) {
        int parenDepth = 0;
        PsiElement current = operator.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // Hit a newline - stop searching
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    break;
                }

                if (type == KiteTokenTypes.RPAREN) {
                    parenDepth++;
                } else if (type == KiteTokenTypes.LPAREN) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    } else {
                        // Unmatched LPAREN - we're inside parens
                        return true;
                    }
                }
            }
            current = current.getPrevSibling();
        }
        return false;
    }

    /**
     * Check if this is an assignment operator.
     */
    private boolean isAssignmentOperator(IElementType type) {
        return type == KiteTokenTypes.ASSIGN ||
               type == KiteTokenTypes.PLUS_ASSIGN ||
               type == KiteTokenTypes.MINUS_ASSIGN ||
               type == KiteTokenTypes.MUL_ASSIGN ||
               type == KiteTokenTypes.DIV_ASSIGN;
    }

    /**
     * Build a string representation of the expression for the warning message.
     */
    private String buildExpressionText(PsiElement operator) {
        PsiElement left = KitePsiUtil.skipWhitespaceBackward(operator.getPrevSibling());
        PsiElement right = KitePsiUtil.skipWhitespace(operator.getNextSibling());

        String leftText = left != null ? left.getText() : "?";
        String rightText = right != null ? right.getText() : "?";

        return leftText + " " + operator.getText() + " " + rightText;
    }
}
