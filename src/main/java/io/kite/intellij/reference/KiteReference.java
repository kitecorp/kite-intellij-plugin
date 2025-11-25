package io.kite.intellij.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference implementation for Kite identifiers.
 * Resolves identifier usages to their declarations using pure PSI traversal.
 *
 * Handles two types of references:
 * 1. Simple identifiers (e.g., "server") - resolved to declarations in scope
 * 2. Property access (e.g., "size" in "server.size") - resolved within the object's declaration
 */
public class KiteReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final String name;

    public KiteReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
        super(element, rangeInElement);
        this.name = element.getText();
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();

        System.err.println("[KiteRef] multiResolve called for: '" + name + "'");

        // Check if this is a property access (identifier after a DOT)
        PsiElement objectElement = getPropertyAccessObject();

        if (objectElement != null) {
            System.err.println("[KiteRef] Property access detected, object: '" + objectElement.getText() + "'");
            // This is property access: resolve within the object's declaration scope
            resolvePropertyAccess(objectElement, results);
        } else {
            System.err.println("[KiteRef] Simple identifier, searching declarations");
            // Simple identifier: search declarations in file scope
            PsiFile file = myElement.getContainingFile();
            findDeclarations(file, name, results);
        }

        System.err.println("[KiteRef] Found " + results.size() + " results");
        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        return results.length >= 1 ? results[0].getElement() : null;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        // TODO: Implement rename support
        return myElement;
    }

    /**
     * Check if this identifier is part of a property access expression (after a DOT).
     * Returns the object identifier element if this is a property access, null otherwise.
     */
    @Nullable
    private PsiElement getPropertyAccessObject() {
        PsiElement prev = skipWhitespaceBackward(myElement.getPrevSibling());

        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            // Found DOT before us, get the identifier before the DOT
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
    private void resolvePropertyAccess(PsiElement objectElement, List<ResolveResult> results) {
        String objectName = objectElement.getText();
        PsiFile file = myElement.getContainingFile();

        System.err.println("[KiteRef] resolvePropertyAccess: looking for object '" + objectName + "'");

        // First, find the declaration of the object
        PsiElement objectDeclaration = findDeclarationElement(file, objectName);

        if (objectDeclaration != null) {
            System.err.println("[KiteRef] Found object declaration: " + objectDeclaration.getNode().getElementType());
            // Search for the property within the object's declaration body
            findPropertyInDeclaration(objectDeclaration, name, results);
        } else {
            System.err.println("[KiteRef] Object declaration NOT FOUND!");
        }
    }

    /**
     * Find property assignments within a declaration body using pure PSI traversal.
     * Looks for: identifier = value, identifier: value, or input/output/var declarations
     */
    private void findPropertyInDeclaration(PsiElement declaration, String propertyName, List<ResolveResult> results) {
        // Traverse all children looking for property patterns
        findPropertyRecursive(declaration, propertyName, results, false);
    }

    /**
     * Recursively search for property definitions within a declaration.
     */
    private void findPropertyRecursive(PsiElement element, String propertyName, List<ResolveResult> results, boolean insideBraces) {
        PsiElement child = element.getFirstChild();
        boolean currentInsideBraces = insideBraces;

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            // Track when we enter/exit braces
            if (childType == KiteTokenTypes.LBRACE) {
                currentInsideBraces = true;
                System.err.println("[KiteRef] Entered braces");
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentInsideBraces = false;
                System.err.println("[KiteRef] Exited braces");
            }

            // Check for property patterns only inside the braces
            if (currentInsideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                System.err.println("[KiteRef] Found identifier inside braces: '" + identText + "'");
                if (propertyName.equals(identText)) {
                    // Check if this identifier is followed by = or : (property assignment)
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        System.err.println("[KiteRef] Next token after '" + identText + "': " + nextType);
                        if (nextType == KiteTokenTypes.ASSIGN ||
                            nextType == KiteTokenTypes.COLON ||
                            nextType == KiteTokenTypes.PLUS_ASSIGN) {
                            // Found property assignment: identifier = value or identifier: value
                            if (child != myElement) {
                                System.err.println("[KiteRef] MATCH FOUND: '" + identText + "' at " + child.getTextOffset());
                                results.add(new PsiElementResolveResult(child));
                            }
                        }
                    }
                }
            }

            // Check for input/output/var declarations inside braces
            if (currentInsideBraces && isDeclarationType(childType)) {
                PsiElement declName = findNameInDeclaration(child, childType);
                if (declName != null && propertyName.equals(declName.getText()) && declName != myElement) {
                    System.err.println("[KiteRef] MATCH in declaration: " + declName.getText());
                    results.add(new PsiElementResolveResult(declName));
                }
            }

            // Recurse into composite elements, but not into nested declarations (they have their own scope)
            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                findPropertyRecursive(child, propertyName, results, currentInsideBraces);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Find all declarations with the given name in the file (for simple identifier resolution).
     */
    private void findDeclarations(PsiElement element, String targetName, List<ResolveResult> results) {
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                if (nameElement != myElement) {
                    results.add(new PsiElementResolveResult(nameElement));
                }
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findDeclarations(child, targetName, results);
            child = child.getNextSibling();
        }
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

    /**
     * Skip whitespace tokens when traversing backward.
     */
    @Nullable
    private PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    /**
     * Skip whitespace tokens when traversing forward.
     */
    @Nullable
    private PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    private boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||  // IntelliJ's built-in whitespace (from our lexer)
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }
}
