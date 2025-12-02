package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Annotator that detects duplicate decorators on a single declaration.
 * Shows an error when the same decorator is applied multiple times.
 * <p>
 * Example:
 * <pre>
 * &#64;description("First")
 * &#64;description("Second")  // Error: Duplicate decorator '@description'
 * schema Config { }
 * </pre>
 */
public class KiteDuplicateDecoratorAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process at file level to collect decorators before declarations
        if (!(element instanceof PsiFile file)) {
            return;
        }

        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Process file-level declarations
        processDeclarations(file, holder);

        // Process nested declarations (inside components)
        processNestedDeclarations(file, holder);
    }

    /**
     * Process declarations at file level.
     */
    private void processDeclarations(PsiElement parent, AnnotationHolder holder) {
        List<DecoratorInfo> pendingDecorators = new ArrayList<>();

        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            // Skip whitespace
            if (KitePsiUtil.isWhitespace(type)) {
                continue;
            }

            // Check for @ symbol (decorator start)
            if (type == KiteTokenTypes.AT) {
                PsiElement nameElement = KitePsiUtil.skipWhitespace(child.getNextSibling());
                if (nameElement != null && nameElement.getNode() != null &&
                    nameElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                    pendingDecorators.add(new DecoratorInfo(nameElement.getText(), nameElement));
                }
                continue;
            }

            // When we reach a declaration, check for duplicate decorators
            if (isDeclaration(type)) {
                checkDuplicates(pendingDecorators, holder);
                pendingDecorators.clear();
            }

            // If we hit something other than decorator-related tokens or whitespace, clear pending decorators
            if (!isDecoratorRelatedToken(type) && !isDeclaration(type) && !KitePsiUtil.isWhitespace(type)) {
                pendingDecorators.clear();
            }
        }
    }

    /**
     * Process declarations inside component bodies.
     */
    private void processNestedDeclarations(PsiElement parent, AnnotationHolder holder) {
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            if (type == KiteElementTypes.COMPONENT_DECLARATION) {
                processComponentBody(child, holder);
            }

            // Recurse into nested structures
            processNestedDeclarations(child, holder);
        }
    }

    /**
     * Process input/output declarations inside a component body.
     */
    private void processComponentBody(PsiElement component, AnnotationHolder holder) {
        List<DecoratorInfo> pendingDecorators = new ArrayList<>();
        boolean insideBody = false;

        for (PsiElement child = component.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.LBRACE) {
                insideBody = true;
                continue;
            }
            if (type == KiteTokenTypes.RBRACE) {
                break;
            }

            if (!insideBody) continue;

            // Skip whitespace
            if (KitePsiUtil.isWhitespace(type)) {
                continue;
            }

            // Check for @ symbol (decorator start)
            if (type == KiteTokenTypes.AT) {
                PsiElement nameElement = KitePsiUtil.skipWhitespace(child.getNextSibling());
                if (nameElement != null && nameElement.getNode() != null &&
                    nameElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                    pendingDecorators.add(new DecoratorInfo(nameElement.getText(), nameElement));
                }
                continue;
            }

            // When we reach input/output declaration, check for duplicates
            if (type == KiteElementTypes.INPUT_DECLARATION ||
                type == KiteElementTypes.OUTPUT_DECLARATION) {
                checkDuplicates(pendingDecorators, holder);
                pendingDecorators.clear();
            }

            // If we hit something other than decorator-related tokens or whitespace, clear pending decorators
            if (!isDecoratorRelatedToken(type) &&
                type != KiteElementTypes.INPUT_DECLARATION &&
                type != KiteElementTypes.OUTPUT_DECLARATION &&
                !KitePsiUtil.isWhitespace(type)) {
                pendingDecorators.clear();
            }
        }
    }

    /**
     * Check for duplicate decorators and mark them.
     */
    private void checkDuplicates(List<DecoratorInfo> decorators, AnnotationHolder holder) {
        Map<String, List<DecoratorInfo>> grouped = new LinkedHashMap<>();

        for (DecoratorInfo decorator : decorators) {
            grouped.computeIfAbsent(decorator.name, k -> new ArrayList<>()).add(decorator);
        }

        for (Map.Entry<String, List<DecoratorInfo>> entry : grouped.entrySet()) {
            List<DecoratorInfo> infos = entry.getValue();
            if (infos.size() > 1) {
                // Mark all but the first as duplicates
                for (int i = 1; i < infos.size(); i++) {
                    holder.newAnnotation(HighlightSeverity.ERROR,
                                    "Duplicate decorator '@" + entry.getKey() + "'")
                            .range(infos.get(i).nameElement)
                            .create();
                }
            }
        }
    }

    /**
     * Check if element type is a declaration that can have decorators.
     */
    private boolean isDeclaration(IElementType type) {
        return type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.VARIABLE_DECLARATION;
    }

    /**
     * Check if the token is part of a decorator (between @ and next declaration).
     */
    private boolean isDecoratorRelatedToken(IElementType type) {
        return type == KiteTokenTypes.AT ||
               type == KiteTokenTypes.IDENTIFIER ||
               type == KiteTokenTypes.LPAREN ||
               type == KiteTokenTypes.RPAREN ||
               type == KiteTokenTypes.STRING ||
               type == KiteTokenTypes.DQUOTE ||
               type == KiteTokenTypes.STRING_TEXT ||
               type == KiteTokenTypes.STRING_DQUOTE ||
               type == KiteTokenTypes.NUMBER ||
               type == KiteTokenTypes.COMMA ||
               type == KiteTokenTypes.COLON ||
               type == KiteTokenTypes.LBRACE ||
               type == KiteTokenTypes.RBRACE ||
               type == KiteTokenTypes.LBRACK ||
               type == KiteTokenTypes.RBRACK ||
               type == KiteElementTypes.OBJECT_LITERAL ||
               type == KiteElementTypes.ARRAY_LITERAL;
    }

    /**
     * Information about a decorator.
     */
    private record DecoratorInfo(String name, PsiElement nameElement) {
    }
}
