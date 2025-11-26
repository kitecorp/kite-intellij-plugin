package io.kite.intellij.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Annotator that detects duplicate declarations in the same scope.
 * Shows an error with red underline when a name is already declared.
 *
 * Checks for duplicates among:
 * - input declarations
 * - output declarations
 * - component declarations/instantiations
 * - resource declarations
 * - variable declarations
 * - function declarations
 * - schema declarations
 * - type declarations
 */
public class KiteDuplicateDeclarationAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process at file level to avoid redundant checks
        if (!(element instanceof PsiFile)) {
            return;
        }

        PsiFile file = (PsiFile) element;
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Collect all declarations at file level
        Map<String, List<DeclarationInfo>> fileScope = new LinkedHashMap<>();
        collectDeclarationsInScope(file, fileScope);

        // Mark duplicates at file level
        markDuplicates(fileScope, holder);

        // Process nested scopes (inside components, schemas, resources)
        processNestedScopes(file, holder);
    }

    /**
     * Process nested scopes inside components, schemas, and resources
     */
    private void processNestedScopes(PsiElement parent, AnnotationHolder holder) {
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            // Check if this is a block that creates a new scope
            if (type == KiteElementTypes.COMPONENT_DECLARATION ||
                type == KiteElementTypes.SCHEMA_DECLARATION ||
                type == KiteElementTypes.RESOURCE_DECLARATION) {

                // Collect declarations inside this block
                Map<String, List<DeclarationInfo>> blockScope = new LinkedHashMap<>();
                collectDeclarationsInBlock(child, blockScope);
                markDuplicates(blockScope, holder);

                // Recursively process nested blocks
                processNestedScopes(child, holder);
            }
        }
    }

    /**
     * Collect declarations at the current scope level (direct children only)
     */
    private void collectDeclarationsInScope(PsiElement scope, Map<String, List<DeclarationInfo>> declarations) {
        for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();
            DeclarationInfo info = extractDeclarationInfo(child, type);
            if (info != null) {
                declarations.computeIfAbsent(info.name, k -> new ArrayList<>()).add(info);
            }
        }
    }

    /**
     * Collect declarations inside a block (component, schema, resource body)
     */
    private void collectDeclarationsInBlock(PsiElement block, Map<String, List<DeclarationInfo>> declarations) {
        // Find the content after the opening brace
        boolean insideBraces = false;
        for (PsiElement child = block.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.LBRACE) {
                insideBraces = true;
                continue;
            }
            if (type == KiteTokenTypes.RBRACE) {
                break;
            }

            if (insideBraces) {
                DeclarationInfo info = extractDeclarationInfo(child, type);
                if (info != null) {
                    declarations.computeIfAbsent(info.name, k -> new ArrayList<>()).add(info);
                }
            }
        }
    }

    /**
     * Extract declaration info from an element
     */
    @Nullable
    private DeclarationInfo extractDeclarationInfo(PsiElement element, IElementType type) {
        if (type == KiteElementTypes.INPUT_DECLARATION ||
            type == KiteElementTypes.OUTPUT_DECLARATION ||
            type == KiteElementTypes.VARIABLE_DECLARATION ||
            type == KiteElementTypes.RESOURCE_DECLARATION ||
            type == KiteElementTypes.COMPONENT_DECLARATION ||
            type == KiteElementTypes.SCHEMA_DECLARATION ||
            type == KiteElementTypes.FUNCTION_DECLARATION ||
            type == KiteElementTypes.TYPE_DECLARATION) {

            PsiElement nameElement = findDeclarationName(element, type);
            if (nameElement != null) {
                String name = nameElement.getText();
                return new DeclarationInfo(name, nameElement, type);
            }
        }
        return null;
    }

    /**
     * Find the name identifier within a declaration
     */
    @Nullable
    private PsiElement findDeclarationName(PsiElement declaration, IElementType declarationType) {
        // For component declarations, we need to distinguish between:
        // - "component WebServer { ... }" - declaration, name is "WebServer"
        // - "component WebServer serviceA { ... }" - instantiation, name is "serviceA"
        if (declarationType == KiteElementTypes.COMPONENT_DECLARATION) {
            return findComponentName(declaration);
        }

        // For other declarations: keyword [type] name [= value] or keyword [type] name { ... }
        // Find the identifier that comes before '=' or '{' or newline
        PsiElement lastIdentifier = null;
        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN ||
                       childType == KiteTokenTypes.NL) {
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
        }
        return lastIdentifier;
    }

    /**
     * Find the name of a component (handles both declarations and instantiations)
     */
    @Nullable
    private PsiElement findComponentName(PsiElement componentDecl) {
        // Count identifiers to determine if it's a declaration or instantiation
        List<PsiElement> identifiers = new ArrayList<>();

        for (PsiElement child = componentDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.IDENTIFIER) {
                identifiers.add(child);
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
        }

        if (identifiers.isEmpty()) {
            return null;
        }

        // If there's only one identifier, it's a type declaration (component WebServer { ... })
        // If there are two, the second is the instance name (component WebServer serviceA { ... })
        if (identifiers.size() == 1) {
            return identifiers.get(0); // Type name for declarations
        } else {
            return identifiers.get(identifiers.size() - 1); // Instance name for instantiations
        }
    }

    /**
     * Mark duplicate declarations with error annotations
     */
    private void markDuplicates(Map<String, List<DeclarationInfo>> declarations, AnnotationHolder holder) {
        for (Map.Entry<String, List<DeclarationInfo>> entry : declarations.entrySet()) {
            List<DeclarationInfo> infos = entry.getValue();
            if (infos.size() > 1) {
                // There are duplicates - mark all but the first one
                for (int i = 1; i < infos.size(); i++) {
                    DeclarationInfo duplicate = infos.get(i);
                    holder.newAnnotation(HighlightSeverity.ERROR,
                            "'" + duplicate.name + "' is already declared")
                        .range(duplicate.nameElement)
                        .create();
                }
            }
        }
    }

    /**
     * Information about a declaration
     */
    private static class DeclarationInfo {
        final String name;
        final PsiElement nameElement;
        final IElementType declarationType;

        DeclarationInfo(String name, PsiElement nameElement, IElementType declarationType) {
            this.name = name;
            this.nameElement = nameElement;
            this.declarationType = declarationType;
        }
    }
}
