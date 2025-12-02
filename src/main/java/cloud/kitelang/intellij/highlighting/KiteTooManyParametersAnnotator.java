package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator that detects functions with too many parameters (more than 5).
 * Shows a warning suggesting to use a schema to group parameters.
 * <p>
 * Example:
 * <pre>
 * fun process(number a, number b, number c, number d, number e, number f) {
 *     // Warning: Function has 6 parameters. Consider using a schema to group them.
 * }
 * </pre>
 */
public class KiteTooManyParametersAnnotator implements Annotator {

    private static final int MAX_PARAMETERS = 5;

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Only check FUNCTION_DECLARATION elements
        if (type != KiteElementTypes.FUNCTION_DECLARATION) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        int paramCount = countParameters(element);
        if (paramCount > MAX_PARAMETERS) {
            // Find the function name element to annotate
            PsiElement functionName = findFunctionName(element);
            PsiElement annotationTarget = functionName != null ? functionName : element;

            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Function has " + paramCount + " parameters. Consider using a schema to group them.")
                    .range(annotationTarget)
                    .create();
        }
    }

    /**
     * Count the number of parameters in a function declaration.
     * Parameters are separated by commas. Count commas + 1 if there are any identifiers.
     */
    private int countParameters(PsiElement functionDecl) {
        int commaCount = 0;
        boolean insideParens = false;
        boolean hasIdentifier = false;

        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LPAREN) {
                insideParens = true;
                continue;
            }

            if (childType == KiteTokenTypes.RPAREN) {
                break; // End of parameter list
            }

            if (!insideParens) continue;

            // Count commas to determine number of parameters
            if (childType == KiteTokenTypes.COMMA) {
                commaCount++;
            } else if (childType == KiteTokenTypes.IDENTIFIER ||
                       childType == KiteTokenTypes.ANY ||
                       childType == KiteTokenTypes.OBJECT) {
                hasIdentifier = true;
            }
        }

        // Number of parameters = commas + 1 (if there are any identifiers)
        if (!hasIdentifier) {
            return 0; // Empty parameter list
        }
        return commaCount + 1;
    }

    /**
     * Find the function name identifier in a function declaration.
     */
    private PsiElement findFunctionName(PsiElement functionDecl) {
        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType childType = child.getNode().getElementType();

            // Function name is the first IDENTIFIER after the FUN keyword
            if (childType == KiteTokenTypes.FUN) {
                // Find the next identifier
                for (PsiElement next = child.getNextSibling(); next != null; next = next.getNextSibling()) {
                    if (next.getNode() == null) continue;
                    if (next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                        return next;
                    }
                }
            }
        }
        return null;
    }
}
