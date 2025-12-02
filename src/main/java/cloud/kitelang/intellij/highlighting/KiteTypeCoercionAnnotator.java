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
 * Annotator that detects type coercion when comparing values of different types.
 * Shows a warning when comparing mismatched literal types.
 * <p>
 * Example:
 * <pre>
 * if 5 == "5" { }      // Warning: Comparing number with string
 * if true == 1 { }     // Warning: Comparing boolean with number
 * if null == 0 { }     // Warning: Comparing null with number
 * </pre>
 */
public class KiteTypeCoercionAnnotator implements Annotator {

    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_NULL = "null";

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for == or != operators
        if (type != KiteTokenTypes.EQ && type != KiteTokenTypes.NE) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Get types of both operands
        String leftType = getLeftOperandType(element);
        String rightType = getRightOperandType(element);

        if (leftType != null && rightType != null && !leftType.equals(rightType)) {
            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Comparing " + leftType + " with " + rightType)
                    .range(element)
                    .create();
        }
    }

    /**
     * Get the type of the left operand.
     */
    @Nullable
    private String getLeftOperandType(PsiElement operator) {
        PsiElement left = KitePsiUtil.skipWhitespaceBackward(operator.getPrevSibling());
        return getElementType(left);
    }

    /**
     * Get the type of the right operand.
     */
    @Nullable
    private String getRightOperandType(PsiElement operator) {
        PsiElement right = KitePsiUtil.skipWhitespace(operator.getNextSibling());
        return getElementType(right);
    }

    /**
     * Determine the type of a literal element.
     */
    @Nullable
    private String getElementType(PsiElement element) {
        if (element == null || element.getNode() == null) return null;

        IElementType type = element.getNode().getElementType();

        if (type == KiteTokenTypes.NUMBER) {
            return TYPE_NUMBER;
        }
        if (type == KiteTokenTypes.TRUE || type == KiteTokenTypes.FALSE) {
            return TYPE_BOOLEAN;
        }
        if (type == KiteTokenTypes.NULL) {
            return TYPE_NULL;
        }
        // String literals - check for quotes or string tokens
        if (type == KiteTokenTypes.STRING ||
            type == KiteTokenTypes.SINGLE_STRING ||
            type == KiteTokenTypes.DQUOTE ||
            type == KiteTokenTypes.STRING_DQUOTE) {
            return TYPE_STRING;
        }

        // For identifiers and other expressions, we can't determine type at this level
        return null;
    }
}
