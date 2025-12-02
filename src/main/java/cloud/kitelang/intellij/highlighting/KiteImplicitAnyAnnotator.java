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
 * Annotator that hints when variable type cannot be inferred (implicit any).
 * Shows a hint when variables are assigned from identifiers or function calls.
 * <p>
 * Example:
 * <pre>
 * var result = getData()      // Hint: implicit 'any' type
 * var copy = original         // Hint: implicit 'any' type
 * var name = "hello"          // OK - inferred as string
 * var string name = getData() // OK - explicit type annotation
 * </pre>
 */
public class KiteImplicitAnyAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for VAR keyword
        if (type != KiteTokenTypes.VAR) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check if this is an implicit any pattern
        if (isImplicitAny(element)) {
            // Annotate the VAR keyword (range must be within annotated element)
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                            "Implicit 'any' type - consider adding explicit type annotation")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if this variable declaration has implicit any type.
     * Returns true if: var identifier = identifier (or function call)
     * Returns false if: var type identifier = ... (explicit type)
     *                   var identifier = literal (inferable)
     */
    private boolean isImplicitAny(PsiElement varKeyword) {
        PsiElement current = varKeyword.getNextSibling();
        PsiElement firstIdentifier = null;
        PsiElement assignOp = null;
        PsiElement valueStart = null;
        boolean hasExplicitType = false;

        // Parse: var [type] name = value
        while (current != null) {
            if (current.getNode() == null) {
                current = current.getNextSibling();
                continue;
            }

            IElementType currentType = current.getNode().getElementType();

            // Stop at newline
            if (currentType == KiteTokenTypes.NL || currentType == KiteTokenTypes.NEWLINE) {
                break;
            }

            // Found assignment - everything before is type/name, after is value
            if (currentType == KiteTokenTypes.ASSIGN) {
                assignOp = current;
                valueStart = KitePsiUtil.skipWhitespace(current.getNextSibling());
                break;
            }

            // Track identifiers before assignment
            if (currentType == KiteTokenTypes.IDENTIFIER) {
                if (firstIdentifier == null) {
                    firstIdentifier = current;
                } else {
                    // Second identifier means first was a type
                    hasExplicitType = true;
                }
            }

            current = current.getNextSibling();
        }

        // Must have assignment
        if (assignOp == null || valueStart == null) {
            return false;
        }

        // Has explicit type - no implicit any
        if (hasExplicitType) {
            return false;
        }

        // Check if value is inferable (literal) or implicit any (identifier/call)
        return isImplicitAnyValue(valueStart);
    }

    /**
     * Check if the value expression results in implicit any.
     * Returns true for identifiers (could be any type).
     * Returns false for literals (type is known).
     */
    private boolean isImplicitAnyValue(PsiElement value) {
        if (value == null || value.getNode() == null) return false;

        IElementType type = value.getNode().getElementType();

        // Literals are inferable
        if (type == KiteTokenTypes.NUMBER ||
            type == KiteTokenTypes.TRUE ||
            type == KiteTokenTypes.FALSE ||
            type == KiteTokenTypes.NULL ||
            type == KiteTokenTypes.DQUOTE ||
            type == KiteTokenTypes.SINGLE_STRING ||
            type == KiteTokenTypes.STRING ||
            type == KiteTokenTypes.LBRACK) {  // Array literal
            return false;
        }

        // Identifier (variable reference or function call) - implicit any
        if (type == KiteTokenTypes.IDENTIFIER) {
            return true;
        }

        return false;
    }

}
