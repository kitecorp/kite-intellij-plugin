package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection that detects unreachable code in Kite functions.
 * Reports code that appears after a return statement in a function body,
 * which can never be executed.
 * <p>
 * Note: This is a simple implementation that only detects straightforward cases
 * (statements after return at the same block level). More complex control flow
 * analysis (like detecting all branches returning) is not implemented.
 */
public class KiteUnreachableCodeInspection extends KiteInspectionBase {

    @Override
    public @NotNull String getShortName() {
        return "KiteUnreachableCode";
    }

    @Override
    protected void checkElement(@NotNull PsiElement element, @NotNull ProblemsHolder holder) {
        if (element.getNode() == null) return;

        var type = element.getNode().getElementType();

        // Only check FUNCTION_DECLARATION elements
        if (type != KiteElementTypes.FUNCTION_DECLARATION) {
            return;
        }

        // Find the function body (content between { and })
        boolean insideBody = false;
        boolean foundReturn = false;

        var child = element.getFirstChild();
        while (child != null) {
            if (child.getNode() == null) {
                child = child.getNextSibling();
                continue;
            }

            var childType = child.getNode().getElementType();

            // Enter function body
            if (childType == KiteTokenTypes.LBRACE) {
                insideBody = true;
                child = child.getNextSibling();
                continue;
            }

            // Exit function body
            if (childType == KiteTokenTypes.RBRACE) {
                break;
            }

            if (insideBody) {
                // Check for RETURN token
                if (childType == KiteTokenTypes.RETURN) {
                    foundReturn = true;
                    // Skip to end of line (the return statement continues until newline)
                    child = skipToEndOfStatement(child);
                    continue;
                }

                // If we've seen a return and this is a statement, flag it
                if (foundReturn && isStatementStart(child)) {
                    registerWarning(holder, child, "Unreachable code");
                }
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Skip to the end of a statement (after the current element).
     */
    private PsiElement skipToEndOfStatement(PsiElement element) {
        var current = element.getNextSibling();
        while (current != null) {
            if (current.getNode() != null) {
                var type = current.getNode().getElementType();
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE ||
                    type == KiteTokenTypes.RBRACE) {
                    return current;
                }
            }
            current = current.getNextSibling();
        }
        return element;
    }

    /**
     * Check if an element is the start of a statement (not whitespace or comment).
     */
    private boolean isStatementStart(PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }

        var type = element.getNode().getElementType();

        // Skip whitespace
        if (KitePsiUtil.isWhitespace(type)) {
            return false;
        }

        // Skip comments
        if (type == KiteTokenTypes.LINE_COMMENT || type == KiteTokenTypes.BLOCK_COMMENT) {
            return false;
        }

        // VAR keyword starts a variable declaration
        if (type == KiteTokenTypes.VAR) {
            return true;
        }

        // FOR keyword starts a for statement
        if (type == KiteTokenTypes.FOR) {
            return true;
        }

        // IF keyword starts an if statement
        if (type == KiteTokenTypes.IF) {
            return true;
        }

        // RETURN keyword starts a return statement (shouldn't happen after return, but just in case)
        if (type == KiteTokenTypes.RETURN) {
            return true;
        }

        // VARIABLE_DECLARATION element type
        if (type == KiteElementTypes.VARIABLE_DECLARATION) {
            return true;
        }

        // FOR_STATEMENT element type
        if (type == KiteElementTypes.FOR_STATEMENT) {
            return true;
        }

        // IDENTIFIER might be a function call or expression statement
        if (type == KiteTokenTypes.IDENTIFIER) {
            // Check if this is at statement position (after newline or at block start)
            var prev = KitePsiUtil.skipWhitespaceBackward(element.getPrevSibling());
            if (prev != null && prev.getNode() != null) {
                var prevType = prev.getNode().getElementType();
                // After newline = likely a statement
                return prevType == KiteTokenTypes.NL || prevType == KiteTokenTypes.NEWLINE;
            }
        }

        return false;
    }
}
