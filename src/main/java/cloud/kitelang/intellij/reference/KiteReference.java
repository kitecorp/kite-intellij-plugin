package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reference implementation for Kite identifiers.
 * Resolves identifier usages to their declarations using pure PSI traversal.
 *
 * Handles three types of references:
 * 1. Simple identifiers (e.g., "server") - resolved to declarations in scope
 * 2. Property access (e.g., "size" in "server.size") - resolved within the object's declaration
 * 3. Cross-file references - resolved through import statements
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


        // Check if this is a property access (identifier after a DOT)
        List<String> propertyChain = getPropertyAccessChain();

        if (propertyChain != null) {
            // This is property access: resolve within the object's declaration scope
            resolvePropertyAccessChain(propertyChain, results);
        } else {
            // Simple identifier: search declarations in file scope
            PsiFile file = myElement.getContainingFile();
            findDeclarations(file, name, results);

            // If not found locally, search in imported files
            if (results.isEmpty()) {
                findDeclarationsInImportedFiles(file, name, results, new HashSet<>());
            }
        }

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
        // Use ElementManipulator to handle the rename
        ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myElement);
        if (manipulator != null) {
            return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
        }
        return myElement;
    }

    /**
     * Check if this identifier is part of a property access expression (after a DOT).
     * Returns the property chain as a list of identifiers (e.g., for "server.tag.Name", returns ["server", "tag"]).
     * Returns null if this is not a property access.
     */
    @Nullable
    private List<String> getPropertyAccessChain() {
        List<String> chain = new ArrayList<>();
        PsiElement current = myElement;

        // Walk backward through the chain: identifier <- DOT <- identifier <- DOT <- ...
        while (true) {
            PsiElement prev = skipWhitespaceBackward(current.getPrevSibling());

            if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.DOT) {
                // Found DOT before us, get the identifier before the DOT
                PsiElement objectElement = skipWhitespaceBackward(prev.getPrevSibling());
                if (objectElement != null && objectElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                    chain.add(0, objectElement.getText()); // Add at beginning to maintain order
                    current = objectElement;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return chain.isEmpty() ? null : chain;
    }

    /**
     * Legacy method for backward compatibility - returns the immediate object element.
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
     * Resolve property access chain: navigate through nested object literals.
     * For "server.tag.Name", chain = ["server", "tag"], name = "Name"
     * Steps:
     *   1. Find declaration of "server" (the resource)
     *   2. Find "tag" property inside server's block → get its OBJECT_LITERAL value
     *   3. Find "Name" property inside that object literal
     *
     * Special case: For component instances (e.g., "serviceA.endpoint" where serviceA is
     * "component WebServer serviceA {...}"), we need to find the property in the component
     * TYPE definition (the "component WebServer {...}" that defines inputs/outputs).
     */
    private void resolvePropertyAccessChain(List<String> chain, List<ResolveResult> results) {
        PsiFile file = myElement.getContainingFile();


        // Start with the first element in the chain (e.g., "server")
        String rootName = chain.get(0);
        PsiElement currentScope = findDeclarationElement(file, rootName);

        if (currentScope == null) {
            return;
        }


        // Special case: If the root is a component INSTANCE, we need to look up properties
        // in the component TYPE definition instead
        if (currentScope.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
            String componentTypeName = getComponentTypeName(currentScope);
            if (componentTypeName != null) {
                // Find the component type definition
                PsiElement componentTypeDef = findComponentTypeDefinition(file, componentTypeName);
                if (componentTypeDef != null) {
                    // For component instances, properties come from outputs (and inputs) of the type definition
                    findOutputOrInputInComponent(componentTypeDef, name, results);
                    if (!results.isEmpty()) {
                        return;
                    }
                } else {
                }
            }
        }

        // Navigate through the rest of the chain (e.g., ["tag"] for server.tag.Name)
        for (int i = 1; i < chain.size(); i++) {
            String propertyName = chain.get(i);

            // Find the property and get its value (should be an object literal)
            PsiElement propertyValue = findPropertyValue(currentScope, propertyName);

            if (propertyValue == null) {
                dumpPsiChildren(currentScope, 0);
                return;
            }


            // The value should be an object literal to continue traversing
            if (propertyValue.getNode().getElementType() == KiteElementTypes.OBJECT_LITERAL) {
                currentScope = propertyValue;
            } else {
                return;
            }
        }

        // Now find the target property (e.g., "Name") in the final scope
        findPropertyInScope(currentScope, name, results);
    }

    /**
     * Get the component type name from a component declaration.
     * For "component WebServer serviceA { ... }", returns "WebServer".
     * For "component WebServer { ... }" (type definition), returns null (no instance name).
     *
     * @return The component type name if this is an instance, null if it's a type definition
     */
    @Nullable
    private String getComponentTypeName(PsiElement componentDeclaration) {
        // Structure: COMPONENT <type> <name> { ... } for instances
        // Structure: COMPONENT <name> { ... } for type definitions
        // We need to find TWO identifiers before the LBRACE to identify an instance
        List<String> identifiers = new ArrayList<>();

        for (PsiElement child = componentDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.IDENTIFIER) {
                identifiers.add(child.getText());
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
        }

        // If we have 2 identifiers, first is type, second is instance name
        if (identifiers.size() >= 2) {
            return identifiers.get(0);
        }

        // Only 1 identifier = this is a type definition, not an instance
        return null;
    }

    /**
     * Find the component TYPE definition (the one with inputs/outputs, not an instance).
     * For type name "WebServer", finds "component WebServer { input..., output... }".
     */
    @Nullable
    private PsiElement findComponentTypeDefinition(PsiElement scope, String typeName) {
        IElementType type = scope.getNode().getElementType();

        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Check if this is a type definition (1 identifier) with matching name
            List<String> identifiers = new ArrayList<>();
            for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    identifiers.add(child.getText());
                } else if (childType == KiteTokenTypes.LBRACE) {
                    break;
                }
            }

            // Type definition has exactly 1 identifier before {
            if (identifiers.size() == 1 && identifiers.get(0).equals(typeName)) {
                return scope;
            }
        }

        // Recurse into children
        for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
            PsiElement result = findComponentTypeDefinition(child, typeName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Find an output or input declaration by name inside a component type definition.
     */
    private void findOutputOrInputInComponent(PsiElement componentTypeDef, String propertyName, List<ResolveResult> results) {
        for (PsiElement child = componentTypeDef.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType childType = child.getNode().getElementType();

            // Check OUTPUT_DECLARATION and INPUT_DECLARATION
            if (childType == KiteElementTypes.OUTPUT_DECLARATION || childType == KiteElementTypes.INPUT_DECLARATION) {
                PsiElement nameElement = findNameInDeclaration(child, childType);
                if (nameElement != null && propertyName.equals(nameElement.getText())) {
                    results.add(new PsiElementResolveResult(nameElement));
                    return;
                }
            }

            // Recurse into non-declaration children (but not into nested declarations)
            if (!isDeclarationType(childType) && child.getFirstChild() != null) {
                findOutputOrInputInComponent(child, propertyName, results);
                if (!results.isEmpty()) {
                    return;
                }
            }
        }
    }

    /**
     * Debug helper: dump PSI children of an element
     */
    private void dumpPsiChildren(PsiElement element, int depth) {
        String indent = "  ".repeat(depth);
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            String text = child.getText();
            if (text.length() > 40) text = text.substring(0, 40) + "...";
            text = text.replace("\n", "\\n");
            if (depth < 3 && child.getFirstChild() != null) {
                dumpPsiChildren(child, depth + 1);
            }
        }
    }

    /**
     * Find a property's value (the expression after = or :) within a declaration or object literal.
     */
    @Nullable
    private PsiElement findPropertyValue(PsiElement scope, String propertyName) {
        // If scope is already an OBJECT_LITERAL, we're conceptually "inside braces"
        boolean startInsideBraces = (scope.getNode().getElementType() == KiteElementTypes.OBJECT_LITERAL);
        return findPropertyValueRecursive(scope, propertyName, startInsideBraces);
    }

    @Nullable
    private PsiElement findPropertyValueRecursive(PsiElement element, String propertyName, boolean insideBraces) {
        PsiElement child = element.getFirstChild();
        boolean currentInsideBraces = insideBraces;


        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            // Track brace state
            if (childType == KiteTokenTypes.LBRACE) {
                currentInsideBraces = true;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentInsideBraces = false;
            }

            // Look for property assignments
            if (currentInsideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                if (propertyName.equals(identText)) {
                    // Check if followed by = or :
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                            // Get the value after = or :
                            PsiElement value = skipWhitespaceForward(next.getNextSibling());
                            if (value != null) {
                                return value;
                            }
                        }
                    }
                }
            }

            // DON'T recurse into nested OBJECT_LITERALs - we only want direct children
            // This is the KEY FIX: we should NOT descend into nested objects when searching
            // for properties at the current level
            if (childType == KiteElementTypes.OBJECT_LITERAL) {
            } else if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                PsiElement result = findPropertyValueRecursive(child, propertyName, currentInsideBraces);
                if (result != null) {
                    return result;
                }
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Find property definitions in a scope (object literal or declaration body).
     */
    private void findPropertyInScope(PsiElement scope, String propertyName, List<ResolveResult> results) {
        IElementType scopeType = scope.getNode().getElementType();

        if (scopeType == KiteElementTypes.OBJECT_LITERAL) {
            // For object literals, search directly inside
            findPropertyInObjectLiteral(scope, propertyName, results);
        } else {
            // For declarations, use the existing recursive search
            findPropertyInDeclaration(scope, propertyName, results);
        }
    }

    /**
     * Find property in an object literal (simpler case - already inside braces).
     */
    private void findPropertyInObjectLiteral(PsiElement objectLiteral, String propertyName, List<ResolveResult> results) {
        PsiElement child = objectLiteral.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                if (propertyName.equals(identText)) {
                    // Check if followed by : (object literal property)
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null && next.getNode().getElementType() == KiteTokenTypes.COLON) {
                        if (child != myElement) {
                            results.add(new PsiElementResolveResult(child));
                        }
                    }
                }
            }

            // Recurse into nested object literals
            if (childType == KiteElementTypes.OBJECT_LITERAL) {
                // Don't recurse - nested literals have their own properties
            } else if (child.getFirstChild() != null) {
                findPropertyInObjectLiteral(child, propertyName, results);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Resolve property access: find the property within the object's declaration scope.
     */
    private void resolvePropertyAccess(PsiElement objectElement, List<ResolveResult> results) {
        String objectName = objectElement.getText();
        PsiFile file = myElement.getContainingFile();


        // First, find the declaration of the object
        PsiElement objectDeclaration = findDeclarationElement(file, objectName);

        if (objectDeclaration != null) {
            // Search for the property within the object's declaration body
            findPropertyInDeclaration(objectDeclaration, name, results);
        } else {
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
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentInsideBraces = false;
            }

            // Check for property patterns only inside the braces
            if (currentInsideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                String identText = child.getText();
                if (propertyName.equals(identText)) {
                    // Check if this identifier is followed by = or : (property assignment)
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN ||
                            nextType == KiteTokenTypes.COLON ||
                            nextType == KiteTokenTypes.PLUS_ASSIGN) {
                            // Found property assignment: identifier = value or identifier: value
                            if (child != myElement) {
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

    /**
     * Find declarations in imported files (cross-file navigation).
     * Only searches for symbols that are explicitly imported (named or wildcard).
     *
     * @param file       The file containing the import statements
     * @param targetName The name to find
     * @param results    List to add found results to
     * @param visited    Set of visited file paths to prevent infinite loops
     */
    private void findDeclarationsInImportedFiles(PsiFile file, String targetName,
                                                 List<ResolveResult> results, Set<String> visited) {
        // Check if this symbol is actually imported
        // getImportSourceFile returns the file if the symbol is imported (named or wildcard)
        PsiFile importSourceFile = KiteImportHelper.getImportSourceFile(targetName, file);

        if (importSourceFile == null) {
            // Symbol is not imported, don't search imported files
            return;
        }

        if (importSourceFile.getVirtualFile() == null) {
            return;
        }

        String filePath = importSourceFile.getVirtualFile().getPath();
        if (visited.contains(filePath)) {
            return; // Already visited, skip to prevent infinite loop
        }
        visited.add(filePath);

        // Search for declarations only in the specific imported file
        findDeclarationsInFile(importSourceFile, targetName, results);

        // Also recursively check imports in the imported file (for re-exports)
        if (results.isEmpty()) {
            findDeclarationsInImportedFiles(importSourceFile, targetName, results, visited);
        }
    }

    /**
     * Find declarations in a specific file (used for cross-file search).
     * Unlike findDeclarations, this doesn't check if nameElement == myElement
     * since we're searching in a different file.
     */
    private void findDeclarationsInFile(PsiElement element, String targetName, List<ResolveResult> results) {
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                // For cross-file references, we always add the result
                results.add(new PsiElementResolveResult(nameElement));
                return; // Found it, no need to recurse further in this declaration
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findDeclarationsInFile(child, targetName, results);
            child = child.getNextSibling();
        }
    }
}
