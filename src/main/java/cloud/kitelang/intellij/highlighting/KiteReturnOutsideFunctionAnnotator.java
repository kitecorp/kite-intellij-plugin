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
 * Annotator that detects return statements outside of function bodies.
 * Shows an error when return is used at top level or inside schemas/components/resources.
 * <p>
 * Example:
 * <pre>
 * return 42           // Error: 'return' statement outside of function
 *
 * schema Config {
 *     return 42       // Error: 'return' statement outside of function
 * }
 * </pre>
 */
public class KiteReturnOutsideFunctionAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        // Only check RETURN tokens
        if (element.getNode().getElementType() != KiteTokenTypes.RETURN) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if this return is inside a function body
        if (!isInsideFunction(element)) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "'return' statement outside of function")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if the element is inside a function body.
     */
    private boolean isInsideFunction(PsiElement element) {
        PsiElement parent = element.getParent();

        while (parent != null) {
            if (parent.getNode() != null) {
                IElementType type = parent.getNode().getElementType();

                // If we hit a FUNCTION_DECLARATION, we're inside a function
                if (type == KiteElementTypes.FUNCTION_DECLARATION) {
                    return true;
                }

                // If we hit other block-type declarations first, we're not in a function
                // (schemas, components, resources don't allow return)
                if (type == KiteElementTypes.SCHEMA_DECLARATION ||
                    type == KiteElementTypes.COMPONENT_DECLARATION ||
                    type == KiteElementTypes.RESOURCE_DECLARATION) {
                    return false;
                }
            }

            parent = parent.getParent();
        }

        // Reached the top of the file without finding a function
        return false;
    }
}
