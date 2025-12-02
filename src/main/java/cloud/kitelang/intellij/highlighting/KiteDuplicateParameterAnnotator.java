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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotator that detects duplicate parameter names in function declarations.
 * Shows an error when the same parameter name appears multiple times.
 * <p>
 * Example:
 * <pre>
 * fun calculate(number x, string x) {  // Error: 'x' is already declared
 *     return x
 * }
 * </pre>
 */
public class KiteDuplicateParameterAnnotator implements Annotator {

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

        // Collect parameters and check for duplicates
        Map<String, List<PsiElement>> parameters = collectParameters(element);
        markDuplicates(parameters, holder);
    }

    /**
     * Collect function parameters with their elements.
     * Pattern: type1 name1, type2 name2, ...
     * The last identifier before a comma or ) is the parameter name.
     */
    private Map<String, List<PsiElement>> collectParameters(PsiElement functionDecl) {
        Map<String, List<PsiElement>> parameters = new LinkedHashMap<>();
        boolean insideParams = false;
        String lastIdentifierText = null;
        PsiElement lastIdentifierElement = null;

        for (var child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.LPAREN) {
                insideParams = true;
                continue;
            }

            if (type == KiteTokenTypes.RPAREN) {
                // Save the last parameter
                if (insideParams && lastIdentifierText != null && lastIdentifierElement != null) {
                    parameters.computeIfAbsent(lastIdentifierText, k -> new ArrayList<>())
                            .add(lastIdentifierElement);
                }
                break;
            }

            if (type == KiteTokenTypes.LBRACE) {
                // Function body started without closing paren (shouldn't happen in valid code)
                break;
            }

            if (insideParams) {
                if (KitePsiUtil.isWhitespace(type)) {
                    continue;
                }

                if (type == KiteTokenTypes.COMMA) {
                    // Previous identifier was the parameter name
                    if (lastIdentifierText != null && lastIdentifierElement != null) {
                        parameters.computeIfAbsent(lastIdentifierText, k -> new ArrayList<>())
                                .add(lastIdentifierElement);
                    }
                    lastIdentifierText = null;
                    lastIdentifierElement = null;
                } else if (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.ANY) {
                    // Track identifier - last one before comma/rparen is the param name
                    lastIdentifierText = child.getText();
                    lastIdentifierElement = child;
                }
            }
        }

        return parameters;
    }

    /**
     * Mark duplicate parameters with error annotations.
     */
    private void markDuplicates(Map<String, List<PsiElement>> parameters, AnnotationHolder holder) {
        for (var entry : parameters.entrySet()) {
            List<PsiElement> elements = entry.getValue();
            if (elements.size() > 1) {
                // Mark all but the first occurrence as duplicates
                for (int i = 1; i < elements.size(); i++) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "'" + entry.getKey() + "' is already declared")
                            .range(elements.get(i))
                            .create();
                }
            }
        }
    }
}
