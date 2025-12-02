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
 * Annotator that detects assignment operators in conditions (likely meant to be comparison).
 * Shows a warning when '=' is used where '==' was probably intended.
 * <p>
 * Example:
 * <pre>
 * if x = 5 { ... }     // Warning: Assignment in condition. Did you mean '=='?
 * while y = true { }   // Warning: Assignment in condition. Did you mean '=='?
 * </pre>
 */
public class KiteAssignmentInConditionAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for assignment operators
        if (!isAssignmentOperator(type)) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if this assignment is in a condition position (after IF or WHILE)
        if (isInConditionPosition(element)) {
            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Assignment in condition. Did you mean '=='?")
                    .range(element)
                    .create();
        }
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
     * Check if this assignment is in a condition position (between IF/WHILE and {).
     */
    private boolean isInConditionPosition(PsiElement assignOp) {
        // Walk backward to find IF or WHILE, checking we don't pass a LBRACE or statement boundary
        PsiElement current = assignOp.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // Found IF or WHILE - we're in a condition
                if (type == KiteTokenTypes.IF || type == KiteTokenTypes.WHILE) {
                    return true;
                }

                // Hit a block boundary - not in condition
                if (type == KiteTokenTypes.LBRACE || type == KiteTokenTypes.RBRACE) {
                    return false;
                }

                // Hit a statement boundary
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    // Check if there's an IF/WHILE on this line
                    PsiElement prev = KitePsiUtil.skipWhitespaceBackward(current.getPrevSibling());
                    if (prev != null && prev.getNode() != null) {
                        IElementType prevType = prev.getNode().getElementType();
                        if (prevType == KiteTokenTypes.IF || prevType == KiteTokenTypes.WHILE) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            current = current.getPrevSibling();
        }
        return false;
    }
}
