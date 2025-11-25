package io.kite.intellij.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for "Go to Declaration" (Cmd+Click) in Kite files.
 * Uses direct PSI traversal to resolve identifiers to their declarations.
 */
public class KiteGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }

        // Only handle Kite files
        PsiFile file = sourceElement.getContainingFile();
        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        // Only handle IDENTIFIER tokens
        if (sourceElement.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        String name = sourceElement.getText();

        // For declaration names, show a dropdown of all usages
        // e.g., clicking on "server" in "resource VM.Instance server { }" shows all usages of "server"
        if (isDeclarationName(sourceElement)) {
            List<PsiElement> usages = findUsages(file, name, sourceElement);
            if (!usages.isEmpty()) {
                return usages.toArray(new PsiElement[0]);
            }
            return null;
        }

        // Check if this is a property access (identifier after a DOT)
        PsiElement objectElement = getPropertyAccessObject(sourceElement);

        if (objectElement != null) {
            // Property access: resolve within the object's declaration scope
            return resolvePropertyAccess(file, objectElement.getText(), name, sourceElement);
        } else {
            // Simple identifier: search declarations in file scope
            PsiElement declaration = findDeclaration(file, name, sourceElement);
            if (declaration != null) {
                return new PsiElement[]{declaration};
            }
        }

        return null;
    }

    /**
     * Find all usages (references) of a name in the file.
     * Used when clicking on a declaration name to show where it's used.
     * Returns wrapped elements with custom presentation for the popup.
     */
    private List<PsiElement> findUsages(PsiElement element, String targetName, PsiElement sourceElement) {
        List<PsiElement> rawUsages = new ArrayList<>();
        findUsagesRecursive(element, targetName, sourceElement, rawUsages);

        // Wrap each usage in a navigatable element with custom presentation
        List<PsiElement> wrappedUsages = new ArrayList<>();
        for (PsiElement usage : rawUsages) {
            wrappedUsages.add(new KiteNavigatablePsiElement(usage));
        }
        return wrappedUsages;
    }

    /**
     * Recursively find all usages of a name.
     */
    private void findUsagesRecursive(PsiElement element, String targetName, PsiElement sourceElement, List<PsiElement> usages) {
        IElementType type = element.getNode().getElementType();

        // Check if this is an identifier with the target name
        if (type == KiteTokenTypes.IDENTIFIER && targetName.equals(element.getText())) {
            // Exclude the source element itself
            if (element != sourceElement) {
                // Only include if this is NOT a declaration name (i.e., it's a reference/usage)
                if (!isDeclarationName(element)) {
                    usages.add(element);
                }
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findUsagesRecursive(child, targetName, sourceElement, usages);
            child = child.getNextSibling();
        }
    }

    /**
     * Check if this identifier is part of a property access expression (after a DOT).
     */
    @Nullable
    private PsiElement getPropertyAccessObject(PsiElement element) {
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            PsiElement objectElement = skipWhitespaceBackward(prev.getPrevSibling());
            if (objectElement != null && objectElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                return objectElement;
            }
        }
        return null;
    }

    /**
     * Resolve property access: find the property within the object's declaration scope.
     */
    @Nullable
    private PsiElement[] resolvePropertyAccess(PsiFile file, String objectName, String propertyName, PsiElement sourceElement) {
        // First, find the declaration of the object
        PsiElement objectDeclaration = findDeclarationElement(file, objectName);

        if (objectDeclaration != null) {
            // Search for the property within the object's declaration body
            PsiElement property = findPropertyInDeclaration(objectDeclaration, propertyName, sourceElement);
            if (property != null) {
                return new PsiElement[]{property};
            }
        }

        return null;
    }

    /**
     * Find property definitions within a declaration body.
     */
    @Nullable
    private PsiElement findPropertyInDeclaration(PsiElement declaration, String propertyName, PsiElement sourceElement) {
        return findPropertyRecursive(declaration, propertyName, sourceElement, false);
    }

    /**
     * Recursively search for property definitions within a declaration.
     */
    @Nullable
    private PsiElement findPropertyRecursive(PsiElement element, String propertyName, PsiElement sourceElement, boolean insideBraces) {
        PsiElement child = element.getFirstChild();
        boolean currentInsideBraces = insideBraces;

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            // Track when we enter/exit braces
            if (childType == KiteTokenTypes.LBRACE) {
                currentInsideBraces = true;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentInsideBraces = false;
            }

            // Check for property patterns only inside the braces
            if (currentInsideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                if (propertyName.equals(identText) && child != sourceElement) {
                    // Check if this identifier is followed by = or : (property assignment)
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN ||
                            nextType == KiteTokenTypes.COLON ||
                            nextType == KiteTokenTypes.PLUS_ASSIGN) {
                            return child;
                        }
                    }
                }
            }

            // Check for input/output/var declarations inside braces
            if (currentInsideBraces && isDeclarationType(childType)) {
                PsiElement declName = findNameInDeclaration(child, childType);
                if (declName != null && propertyName.equals(declName.getText()) && declName != sourceElement) {
                    return declName;
                }
            }

            // Recurse into composite elements, but not into nested declarations
            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                PsiElement result = findPropertyRecursive(child, propertyName, sourceElement, currentInsideBraces);
                if (result != null) {
                    return result;
                }
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find a declaration with the given name in the file (for simple identifier resolution).
     */
    @Nullable
    private PsiElement findDeclaration(PsiElement element, String targetName, PsiElement sourceElement) {
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText()) && nameElement != sourceElement) {
                return nameElement;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclaration(child, targetName, sourceElement);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find the declaration element (the whole node) for a given name.
     */
    @Nullable
    private PsiElement findDeclarationElement(PsiElement element, String targetName) {
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                return element; // Return the whole declaration, not just the name
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclarationElement(child, targetName);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    private boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT;
    }

    /**
     * Find the name identifier within a declaration.
     */
    @Nullable
    private PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            // For loop: "for identifier in ..." - name is right after 'for'
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child;
                }
                child = child.getNextSibling();
            }
        }

        // For var/input/output: keyword [type] name [= value]
        // For resource/component/schema/function: keyword [type] name { ... }
        // Find the identifier that comes before '=' or '{'
        PsiElement lastIdentifier = null;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier;
    }

    @Nullable
    private PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    @Nullable
    private PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    private boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Check if this identifier is a declaration name (the name being declared, not a reference).
     * Declaration names are:
     * - The last identifier before = or { in input/output/var/resource/component/schema/function/type declarations
     * - The identifier after "for" keyword in for loops
     * - Property names in object literals (identifier before : or =)
     */
    private boolean isDeclarationName(PsiElement element) {
        // Check if this identifier is followed by = or { or += or : (declaration/property pattern)
        PsiElement next = skipWhitespaceForward(element.getNextSibling());
        if (next != null) {
            IElementType nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.ASSIGN ||
                nextType == KiteTokenTypes.LBRACE ||
                nextType == KiteTokenTypes.PLUS_ASSIGN ||
                nextType == KiteTokenTypes.COLON) {
                // This identifier is followed by = or { or : - it's a declaration/property name
                return true;
            }
        }

        // Check if this is a for loop variable (identifier after "for" keyword)
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.FOR) {
            return true;
        }

        // Check if parent is a declaration and this is the declared name
        PsiElement parent = element.getParent();
        if (parent != null) {
            IElementType parentType = parent.getNode().getElementType();
            if (isDeclarationType(parentType)) {
                // Find the name element in this declaration
                PsiElement nameElement = findNameInDeclaration(parent, parentType);
                if (nameElement == element) {
                    return true;
                }
            }
        }

        return false;
    }
}
