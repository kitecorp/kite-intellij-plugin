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
 * Annotator that detects redundant boolean comparisons like 'x == true' or 'x != false'.
 * Shows a warning suggesting simpler expressions.
 * <p>
 * Example:
 * <pre>
 * if isValid == true { ... }   // Can be simplified to 'isValid'
 * if isValid == false { ... }  // Can be simplified to '!isValid'
 * if isValid != true { ... }   // Can be simplified to '!isValid'
 * if isValid != false { ... }  // Can be simplified to 'isValid'
 * </pre>
 */
public class KiteRedundantBooleanAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for TRUE or FALSE literals
        if (type != KiteTokenTypes.TRUE && type != KiteTokenTypes.FALSE) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if this boolean literal is part of a comparison with an identifier
        if (isRedundantBooleanComparison(element)) {
            boolean isTrueLiteral = type == KiteTokenTypes.TRUE;
            String suggestion = getSuggestion(element, isTrueLiteral);

            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Can be simplified to '" + suggestion + "'")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if this boolean literal is part of a redundant comparison.
     * Patterns: x == true, x == false, x != true, x != false,
     *           true == x, false == x, true != x, false != x
     */
    private boolean isRedundantBooleanComparison(PsiElement boolLiteral) {
        // Check pattern: identifier ==|!= boolean
        PsiElement prev = KitePsiUtil.skipWhitespaceBackward(boolLiteral.getPrevSibling());
        if (prev != null && prev.getNode() != null) {
            IElementType prevType = prev.getNode().getElementType();
            if (prevType == KiteTokenTypes.EQ || prevType == KiteTokenTypes.NE) {
                PsiElement beforeOp = KitePsiUtil.skipWhitespaceBackward(prev.getPrevSibling());
                if (beforeOp != null && beforeOp.getNode() != null) {
                    IElementType beforeOpType = beforeOp.getNode().getElementType();
                    if (beforeOpType == KiteTokenTypes.IDENTIFIER) {
                        return true;
                    }
                }
            }
        }

        // Check pattern: boolean ==|!= identifier
        PsiElement next = KitePsiUtil.skipWhitespace(boolLiteral.getNextSibling());
        if (next != null && next.getNode() != null) {
            IElementType nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.EQ || nextType == KiteTokenTypes.NE) {
                PsiElement afterOp = KitePsiUtil.skipWhitespace(next.getNextSibling());
                if (afterOp != null && afterOp.getNode() != null) {
                    IElementType afterOpType = afterOp.getNode().getElementType();
                    if (afterOpType == KiteTokenTypes.IDENTIFIER) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Get the suggested simplified expression.
     */
    private String getSuggestion(PsiElement boolLiteral, boolean isTrueLiteral) {
        // Determine the operator and identifier
        PsiElement prev = KitePsiUtil.skipWhitespaceBackward(boolLiteral.getPrevSibling());
        if (prev != null && prev.getNode() != null) {
            IElementType prevType = prev.getNode().getElementType();
            if (prevType == KiteTokenTypes.EQ || prevType == KiteTokenTypes.NE) {
                boolean isEquals = prevType == KiteTokenTypes.EQ;
                PsiElement identifier = KitePsiUtil.skipWhitespaceBackward(prev.getPrevSibling());
                if (identifier != null) {
                    String varName = identifier.getText();
                    return getSuggestionText(varName, isEquals, isTrueLiteral);
                }
            }
        }

        PsiElement next = KitePsiUtil.skipWhitespace(boolLiteral.getNextSibling());
        if (next != null && next.getNode() != null) {
            IElementType nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.EQ || nextType == KiteTokenTypes.NE) {
                boolean isEquals = nextType == KiteTokenTypes.EQ;
                PsiElement identifier = KitePsiUtil.skipWhitespace(next.getNextSibling());
                if (identifier != null) {
                    String varName = identifier.getText();
                    return getSuggestionText(varName, isEquals, isTrueLiteral);
                }
            }
        }

        return "simplified";
    }

    /**
     * Get the suggestion text based on operator and literal.
     * x == true  -> x
     * x == false -> !x
     * x != true  -> !x
     * x != false -> x
     */
    private String getSuggestionText(String varName, boolean isEquals, boolean isTrueLiteral) {
        // Truth table:
        // == true  -> var    (isEquals && isTrueLiteral)
        // == false -> !var   (isEquals && !isTrueLiteral)
        // != true  -> !var   (!isEquals && isTrueLiteral)
        // != false -> var    (!isEquals && !isTrueLiteral)
        boolean needsNegation = (isEquals && !isTrueLiteral) || (!isEquals && isTrueLiteral);
        return needsNegation ? "!" + varName : varName;
    }
}
