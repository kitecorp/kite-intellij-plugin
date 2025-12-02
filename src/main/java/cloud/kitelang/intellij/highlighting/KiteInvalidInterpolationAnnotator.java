package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Annotator that detects unclosed string interpolations.
 * <p>
 * Example:
 * <pre>
 * var x = "Hello ${name"      // Error: Unclosed string interpolation '${'
 * var y = "Value: ${a.b"      // Error: Unclosed string interpolation '${'
 *
 * var z = "Hello ${name}"     // OK - properly closed
 * var w = "Price: $100"       // OK - simple $ not interpolation
 * </pre>
 */
public class KiteInvalidInterpolationAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        // Trigger on INTERP_START tokens (${)
        if (type == KiteTokenTypes.INTERP_START || type == KiteTokenTypes.INTERP_OPEN) {
            checkInterpolationClosure(element, holder);
        }
    }

    /**
     * Check if the interpolation started by this INTERP_START is properly closed.
     * Walks forward to find a matching INTERP_END before encountering end of string.
     */
    private void checkInterpolationClosure(PsiElement interpStart, AnnotationHolder holder) {
        int depth = 1;
        PsiElement current = interpStart.getNextSibling();

        while (current != null && depth > 0) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // Found closing }
                if (type == KiteTokenTypes.INTERP_END || type == KiteTokenTypes.INTERP_CLOSE ||
                    type == KiteTokenTypes.RBRACE) {
                    depth--;
                    if (depth == 0) {
                        return; // Properly closed
                    }
                }

                // Found nested ${
                if (type == KiteTokenTypes.INTERP_START || type == KiteTokenTypes.INTERP_OPEN) {
                    depth++;
                }

                // Found end of string without closing
                if (type == KiteTokenTypes.DQUOTE) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "Unclosed string interpolation '${'")
                            .range(interpStart)
                            .create();
                    return;
                }

                // Found newline (string might be broken)
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "Unclosed string interpolation '${'")
                            .range(interpStart)
                            .create();
                    return;
                }
            }
            current = current.getNextSibling();
        }

        // Reached end without finding closure
        if (depth > 0) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "Unclosed string interpolation '${'")
                    .range(interpStart)
                    .create();
        }
    }
}
