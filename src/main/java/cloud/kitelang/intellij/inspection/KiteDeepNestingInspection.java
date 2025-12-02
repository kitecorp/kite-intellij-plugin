package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection that detects deeply nested code.
 * Deeply nested code is harder to read and understand.
 * Default threshold is 4 levels of nesting.
 */
public class KiteDeepNestingInspection extends KiteInspectionBase {

    private static final int DEFAULT_MAX_DEPTH = 4;

    @Override
    public @NotNull String getShortName() {
        return "KiteDeepNesting";
    }

    @Override
    protected void checkElement(@NotNull PsiElement element, @NotNull ProblemsHolder holder) {
        if (element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Only check FUNCTION_DECLARATION elements
        if (type != KiteElementTypes.FUNCTION_DECLARATION) {
            return;
        }

        // Find function body (between braces)
        boolean insideBody = false;

        var child = element.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var childType = child.getNode().getElementType();

                if (childType == KiteTokenTypes.LBRACE) {
                    insideBody = true;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBody) {
                    // Check nesting depth inside the body
                    checkNestingDepth(child, 0, holder);
                }
            }
            child = child.getNextSibling();
        }
    }

    private void checkNestingDepth(PsiElement element, int currentDepth, ProblemsHolder holder) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();
        int newDepth = currentDepth;

        // Check if this is a nesting construct
        if (isNestingConstruct(type)) {
            newDepth = currentDepth + 1;

            // Report if exceeds max depth
            if (newDepth > DEFAULT_MAX_DEPTH) {
                var keyword = findKeyword(element);
                var targetElement = keyword != null ? keyword : element;
                registerWeakWarning(holder, targetElement,
                        "Deeply nested code (depth " + newDepth + " exceeds " + DEFAULT_MAX_DEPTH + ")");
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkNestingDepth(child, newDepth, holder);
            child = child.getNextSibling();
        }
    }

    /**
     * Check if the element type represents a nesting construct.
     */
    private boolean isNestingConstruct(IElementType type) {
        // Common nesting constructs: if, for, while (represented by tokens)
        return type == KiteTokenTypes.IF ||
               type == KiteTokenTypes.FOR ||
               type == KiteTokenTypes.WHILE;
    }

    /**
     * Find the keyword element for better highlighting.
     */
    private PsiElement findKeyword(PsiElement element) {
        var child = element.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();
                if (type == KiteTokenTypes.IF ||
                    type == KiteTokenTypes.FOR ||
                    type == KiteTokenTypes.WHILE) {
                    return child;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }
}
