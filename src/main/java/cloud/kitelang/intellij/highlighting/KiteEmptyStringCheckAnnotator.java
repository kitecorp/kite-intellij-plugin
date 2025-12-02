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
 * Annotator that suggests using len(str) == 0 instead of str == "".
 * Shows a hint for empty string comparisons.
 * <p>
 * Example:
 * <pre>
 * if name == "" { }     // Hint: Consider using 'len(name) == 0'
 * if "" != value { }    // Hint: Consider using 'len(value) != 0'
 * </pre>
 */
public class KiteEmptyStringCheckAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for opening double quote (start of string literal)
        if (type != KiteTokenTypes.DQUOTE) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if this is an empty string
        if (!isEmptyString(element)) {
            return;
        }

        // Check if this empty string is part of a comparison
        String varName = getComparisonVariable(element);
        if (varName != null) {
            String operator = getComparisonOperator(element);
            String suggestion = "len(" + varName + ") " + operator + " 0";
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                            "Consider using '" + suggestion + "' instead")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if this is an empty string (DQUOTE followed by STRING_DQUOTE with no content).
     */
    private boolean isEmptyString(PsiElement openQuote) {
        PsiElement next = openQuote.getNextSibling();

        // Skip whitespace
        while (next != null && next.getNode() != null) {
            IElementType nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.STRING_DQUOTE) {
                // Found closing quote immediately - empty string
                return true;
            }
            if (nextType == KiteTokenTypes.STRING_TEXT ||
                nextType == KiteTokenTypes.STRING_ESCAPE ||
                nextType == KiteTokenTypes.INTERP_START ||
                nextType == KiteTokenTypes.INTERP_SIMPLE) {
                // Found content - not empty
                return false;
            }
            next = next.getNextSibling();
        }
        return false;
    }

    /**
     * Get the variable being compared to the empty string.
     */
    @Nullable
    private String getComparisonVariable(PsiElement emptyString) {
        // Pattern 1: identifier ==|!= ""
        PsiElement prev = KitePsiUtil.skipWhitespaceBackward(emptyString.getPrevSibling());
        if (prev != null && prev.getNode() != null) {
            IElementType prevType = prev.getNode().getElementType();
            if (prevType == KiteTokenTypes.EQ || prevType == KiteTokenTypes.NE) {
                PsiElement identifier = KitePsiUtil.skipWhitespaceBackward(prev.getPrevSibling());
                if (identifier != null && identifier.getNode() != null &&
                    identifier.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                    return identifier.getText();
                }
            }
        }

        // Pattern 2: "" ==|!= identifier
        // Find the closing quote first
        PsiElement closeQuote = findClosingQuote(emptyString);
        if (closeQuote != null) {
            PsiElement afterClose = KitePsiUtil.skipWhitespace(closeQuote.getNextSibling());
            if (afterClose != null && afterClose.getNode() != null) {
                IElementType afterType = afterClose.getNode().getElementType();
                if (afterType == KiteTokenTypes.EQ || afterType == KiteTokenTypes.NE) {
                    PsiElement identifier = KitePsiUtil.skipWhitespace(afterClose.getNextSibling());
                    if (identifier != null && identifier.getNode() != null &&
                        identifier.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                        return identifier.getText();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get the comparison operator (== or !=).
     */
    private String getComparisonOperator(PsiElement emptyString) {
        // Check before the string
        PsiElement prev = KitePsiUtil.skipWhitespaceBackward(emptyString.getPrevSibling());
        if (prev != null && prev.getNode() != null) {
            IElementType prevType = prev.getNode().getElementType();
            if (prevType == KiteTokenTypes.EQ) return "==";
            if (prevType == KiteTokenTypes.NE) return "!=";
        }

        // Check after the string
        PsiElement closeQuote = findClosingQuote(emptyString);
        if (closeQuote != null) {
            PsiElement afterClose = KitePsiUtil.skipWhitespace(closeQuote.getNextSibling());
            if (afterClose != null && afterClose.getNode() != null) {
                IElementType afterType = afterClose.getNode().getElementType();
                if (afterType == KiteTokenTypes.EQ) return "==";
                if (afterType == KiteTokenTypes.NE) return "!=";
            }
        }

        return "==";
    }

    /**
     * Find the closing quote for this string.
     */
    @Nullable
    private PsiElement findClosingQuote(PsiElement openQuote) {
        PsiElement next = openQuote.getNextSibling();
        while (next != null) {
            if (next.getNode() != null &&
                next.getNode().getElementType() == KiteTokenTypes.STRING_DQUOTE) {
                return next;
            }
            next = next.getNextSibling();
        }
        return null;
    }
}
