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
import org.jetbrains.annotations.Nullable;

/**
 * Annotator that detects missing return statements in functions with return types.
 * Shows an error when a function declares a return type but has no return statement.
 * <p>
 * Example:
 * <pre>
 * fun calculate(number x) number {  // Error: no return statement
 *     var y = x * 2
 * }
 * </pre>
 */
public class KiteMissingReturnAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        // Only process function declarations
        if (element.getNode().getElementType() != KiteElementTypes.FUNCTION_DECLARATION) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Get function name and return type
        String functionName = getFunctionName(element);
        String returnType = getReturnType(element);

        // If no return type or void, no need to check
        if (returnType == null || returnType.equals("void")) {
            return;
        }

        // Check if function body has a return statement
        if (!hasReturnStatement(element)) {
            PsiElement nameElement = getFunctionNameElement(element);
            if (nameElement != null) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                "Function '" + functionName + "' has return type '" + returnType + "' but no return statement")
                        .range(nameElement)
                        .create();
            }
        }
    }

    /**
     * Get the function name from the declaration.
     */
    @Nullable
    private String getFunctionName(PsiElement functionDecl) {
        // Pattern: fun name(...) returnType { }
        boolean seenFun = false;
        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.FUN) {
                seenFun = true;
            } else if (seenFun && type == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            }
        }
        return null;
    }

    /**
     * Get the PSI element for the function name.
     */
    @Nullable
    private PsiElement getFunctionNameElement(PsiElement functionDecl) {
        boolean seenFun = false;
        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.FUN) {
                seenFun = true;
            } else if (seenFun && type == KiteTokenTypes.IDENTIFIER) {
                return child;
            }
        }
        return null;
    }

    /**
     * Get the return type from the function declaration.
     * Returns null if no return type is specified.
     */
    @Nullable
    private String getReturnType(PsiElement functionDecl) {
        // Pattern: fun name(...) returnType { }
        // The return type is the identifier/type after ) and before {
        boolean seenRparen = false;
        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.RPAREN) {
                seenRparen = true;
            } else if (seenRparen) {
                if (KitePsiUtil.isWhitespace(type)) {
                    continue;
                }
                if (type == KiteTokenTypes.LBRACE) {
                    // No return type found before {
                    return null;
                }
                // This should be the return type (types are IDENTIFIER or ANY)
                if (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.ANY) {
                    return child.getText();
                }
            }
        }
        return null;
    }

    /**
     * Check if the function body contains a return statement.
     */
    private boolean hasReturnStatement(PsiElement functionDecl) {
        boolean insideBody = false;
        int braceDepth = 0;

        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.LBRACE) {
                if (!insideBody) {
                    insideBody = true;
                }
                braceDepth++;
            } else if (type == KiteTokenTypes.RBRACE) {
                braceDepth--;
                if (braceDepth == 0) {
                    break;
                }
            } else if (insideBody) {
                // Search recursively for return statements
                if (containsReturn(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Recursively check if element or its descendants contain a return statement.
     */
    private boolean containsReturn(PsiElement element) {
        if (element.getNode() != null && element.getNode().getElementType() == KiteTokenTypes.RETURN) {
            return true;
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (containsReturn(child)) {
                return true;
            }
        }
        return false;
    }
}
