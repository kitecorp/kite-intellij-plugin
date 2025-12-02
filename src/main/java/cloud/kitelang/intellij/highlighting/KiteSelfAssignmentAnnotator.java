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

/**
 * Annotator that detects self-assignment patterns like 'x = x'.
 * Shows a warning because self-assignment is usually a mistake.
 * <p>
 * Example:
 * <pre>
 * var x = 5
 * x = x      // Warning: Self-assignment of 'x'
 * </pre>
 */
public class KiteSelfAssignmentAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        // Only check IDENTIFIER tokens
        if (element.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return;
        }

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Check for pattern: IDENTIFIER = IDENTIFIER (same name)
        if (isSelfAssignment(element)) {
            holder.newAnnotation(HighlightSeverity.WARNING,
                            "Self-assignment of '" + element.getText() + "'")
                    .range(element)
                    .create();
        }
    }

    /**
     * Check if this identifier is the left-hand side of a self-assignment.
     */
    private boolean isSelfAssignment(PsiElement identifier) {
        // Must not be inside a resource block or component instance
        if (isInsideResourceOrComponentInstance(identifier)) {
            return false;
        }

        // Must not be part of a variable declaration (var x = x is shadowing)
        if (isVariableDeclaration(identifier)) {
            return false;
        }

        // Check pattern: identifier = identifier
        PsiElement next = KitePsiUtil.skipWhitespace(identifier.getNextSibling());
        if (next == null || next.getNode() == null) return false;

        // Must be followed by ASSIGN (=)
        if (next.getNode().getElementType() != KiteTokenTypes.ASSIGN) {
            return false;
        }

        // Get the right-hand side
        PsiElement rightSide = KitePsiUtil.skipWhitespace(next.getNextSibling());
        if (rightSide == null || rightSide.getNode() == null) return false;

        // Right side must be a single IDENTIFIER with the same name
        if (rightSide.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return false;
        }

        // Check if right side is followed by an operator (then it's not a simple self-assignment)
        PsiElement afterRight = KitePsiUtil.skipWhitespace(rightSide.getNextSibling());
        if (afterRight != null && afterRight.getNode() != null) {
            IElementType afterType = afterRight.getNode().getElementType();
            // If followed by operator, it's a computation like x = x + 1
            if (isOperator(afterType)) {
                return false;
            }
        }

        // Check if both identifiers have the same name
        return identifier.getText().equals(rightSide.getText());
    }

    /**
     * Check if the element is inside a resource block or component instance.
     */
    private boolean isInsideResourceOrComponentInstance(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (parent.getNode() != null) {
                IElementType type = parent.getNode().getElementType();
                if (type == KiteElementTypes.RESOURCE_DECLARATION) {
                    return true;
                }
                if (type == KiteElementTypes.COMPONENT_DECLARATION) {
                    // Check if it's an instance (has 2 identifiers before {)
                    if (isComponentInstance(parent)) {
                        return true;
                    }
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Check if a component declaration is an instance.
     */
    private boolean isComponentInstance(PsiElement componentDecl) {
        int identifierCount = 0;
        for (PsiElement child = componentDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.LBRACE) break;
            if (type == KiteTokenTypes.IDENTIFIER) identifierCount++;
        }
        return identifierCount >= 2;
    }

    /**
     * Check if this identifier is part of a variable declaration.
     */
    private boolean isVariableDeclaration(PsiElement identifier) {
        PsiElement prev = KitePsiUtil.skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev == null || prev.getNode() == null) return false;

        IElementType type = prev.getNode().getElementType();

        // var x = ...
        if (type == KiteTokenTypes.VAR) {
            return true;
        }

        // var Type x = ... (with explicit type)
        if (type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.ANY) {
            PsiElement prevPrev = KitePsiUtil.skipWhitespaceBackward(prev.getPrevSibling());
            if (prevPrev != null && prevPrev.getNode() != null &&
                prevPrev.getNode().getElementType() == KiteTokenTypes.VAR) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the element type is an operator.
     */
    private boolean isOperator(IElementType type) {
        return type == KiteTokenTypes.PLUS ||
               type == KiteTokenTypes.MINUS ||
               type == KiteTokenTypes.MULTIPLY ||
               type == KiteTokenTypes.DIVIDE ||
               type == KiteTokenTypes.MODULO ||
               type == KiteTokenTypes.AND ||
               type == KiteTokenTypes.OR ||
               type == KiteTokenTypes.DOT ||
               type == KiteTokenTypes.LBRACK;
    }
}
