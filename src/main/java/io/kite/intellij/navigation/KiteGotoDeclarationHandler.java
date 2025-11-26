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

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for "Go to Declaration" (Cmd+Click) in Kite files.
 * Uses direct PSI traversal to resolve identifiers to their declarations.
 *
 * For STRING tokens with interpolations:
 * - This handler provides navigation targets (finding the declaration)
 * - KiteReferenceContributor provides HighlightedReference for precise highlighting
 * - Together they enable Cmd+Click navigation with only the variable name underlined
 */
public class KiteGotoDeclarationHandler implements GotoDeclarationHandler {
    private static final Logger LOG = Logger.getInstance(KiteGotoDeclarationHandler.class);

    // Patterns for string interpolation
    private static final Pattern BRACE_INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern SIMPLE_INTERPOLATION_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

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

        IElementType elementType = sourceElement.getNode().getElementType();
        LOG.info("[KiteGotoDecl] sourceElement type: " + elementType + ", text: " + sourceElement.getText().substring(0, Math.min(50, sourceElement.getText().length())));

        // Handle INTERP_IDENTIFIER tokens (the identifier inside ${...})
        // With the new lexer, each interpolated identifier is its own token
        if (elementType == KiteTokenTypes.INTERP_IDENTIFIER) {
            String varName = sourceElement.getText();
            LOG.info("[KiteGotoDecl] INTERP_IDENTIFIER token: " + varName);
            PsiElement declaration = findDeclaration(file, varName, sourceElement);
            if (declaration != null) {
                LOG.info("[KiteGotoDecl] Found declaration for INTERP_IDENTIFIER: " + varName);
                return new PsiElement[]{declaration};
            }
            return null;
        }

        // Handle INTERP_SIMPLE tokens (the $identifier pattern)
        // The token includes the $ prefix, so extract just the variable name
        if (elementType == KiteTokenTypes.INTERP_SIMPLE) {
            String text = sourceElement.getText();
            if (text.startsWith("$") && text.length() > 1) {
                String varName = text.substring(1);
                LOG.info("[KiteGotoDecl] INTERP_SIMPLE token: " + varName);
                PsiElement declaration = findDeclaration(file, varName, sourceElement);
                if (declaration != null) {
                    LOG.info("[KiteGotoDecl] Found declaration for INTERP_SIMPLE: " + varName);
                    return new PsiElement[]{declaration};
                }
            }
            return null;
        }

        // Handle legacy STRING tokens with interpolations (for backwards compatibility)
        if (elementType == KiteTokenTypes.STRING) {
            LOG.info("[KiteGotoDecl] Legacy STRING token detected, handling string interpolation navigation");
            PsiElement[] targets = handleStringInterpolation(sourceElement, offset, file);
            if (targets != null && targets.length > 0) {
                LOG.info("[KiteGotoDecl] Found " + targets.length + " interpolation targets");
            }
            return targets;
        }

        // Only handle IDENTIFIER tokens
        if (elementType != KiteTokenTypes.IDENTIFIER) {
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
        LOG.info("[KiteGotoDecl] objectElement: " + (objectElement != null ? objectElement.getText() : "null"));

        if (objectElement != null) {
            // Property access: resolve within the object's declaration scope
            LOG.info("[KiteGotoDecl] Resolving property access: " + objectElement.getText() + "." + name);
            return resolvePropertyAccess(file, objectElement.getText(), name, sourceElement);
        } else {
            // Simple identifier: search declarations in file scope
            LOG.info("[KiteGotoDecl] Searching for declaration of: " + name);
            PsiElement declaration = findDeclaration(file, name, sourceElement);
            LOG.info("[KiteGotoDecl] findDeclaration returned: " + (declaration != null ? declaration.getText() + " at " + declaration.getTextRange() : "null"));
            if (declaration != null) {
                LOG.info("[KiteGotoDecl] Returning declaration target: " + declaration.getText());
                return new PsiElement[]{declaration};
            }
        }

        LOG.info("[KiteGotoDecl] No targets found, returning null");
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
        LOG.info("[findDeclaration] Searching in element type: " + type + " for: " + targetName);

        if (isDeclarationType(type)) {
            LOG.info("[findDeclaration] Found declaration type: " + type);
            PsiElement nameElement = findNameInDeclaration(element, type);
            LOG.info("[findDeclaration] Declaration name element: " + (nameElement != null ? nameElement.getText() : "null"));
            if (nameElement != null && targetName.equals(nameElement.getText()) && nameElement != sourceElement) {
                LOG.info("[findDeclaration] MATCH FOUND: " + nameElement.getText() + " at " + nameElement.getTextRange());
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
     * Handle string interpolation navigation.
     * Finds the interpolation variable at the given offset and resolves it to its declaration.
     */
    @Nullable
    private PsiElement[] handleStringInterpolation(PsiElement stringElement, int offset, PsiFile file) {
        String text = stringElement.getText();
        int elementStart = stringElement.getTextRange().getStartOffset();
        int relativeOffset = offset - elementStart;

        LOG.info("[KiteGotoDecl] String text: " + text + ", relativeOffset: " + relativeOffset);

        // Check ${...} interpolations
        Matcher braceMatcher = BRACE_INTERPOLATION_PATTERN.matcher(text);
        while (braceMatcher.find()) {
            int contentStart = braceMatcher.start(1);
            int contentEnd = braceMatcher.end(1);
            String content = braceMatcher.group(1);

            // Check if offset is within this interpolation
            if (relativeOffset >= braceMatcher.start() && relativeOffset <= braceMatcher.end()) {
                String varName = extractFirstIdentifier(content);
                if (varName != null) {
                    LOG.info("[KiteGotoDecl] Found brace interpolation var: " + varName);
                    PsiElement declaration = findDeclaration(file, varName, stringElement);
                    if (declaration != null) {
                        return new PsiElement[]{declaration};
                    }
                }
            }
        }

        // Check $var interpolations
        Matcher simpleMatcher = SIMPLE_INTERPOLATION_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            int matchStart = simpleMatcher.start();

            // Skip if this is part of ${...}
            if (matchStart + 1 < text.length() && text.charAt(matchStart + 1) == '{') {
                continue;
            }

            // Check if offset is within this interpolation
            if (relativeOffset >= matchStart && relativeOffset <= simpleMatcher.end()) {
                String varName = simpleMatcher.group(1);
                LOG.info("[KiteGotoDecl] Found simple interpolation var: " + varName);
                PsiElement declaration = findDeclaration(file, varName, stringElement);
                if (declaration != null) {
                    return new PsiElement[]{declaration};
                }
            }
        }

        return null;
    }

    /**
     * Extract the first identifier from an expression.
     * For "obj.prop" returns "obj", for "func()" returns "func", etc.
     */
    @Nullable
    private String extractFirstIdentifier(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (i == 0) {
                if (Character.isJavaIdentifierStart(c)) {
                    sb.append(c);
                } else {
                    return null;
                }
            } else {
                if (Character.isJavaIdentifierPart(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Check if this identifier is a declaration name (the name being declared, not a reference).
     * Declaration names are:
     * - The last identifier before = or { in input/output/var/resource/component/schema/function/type declarations
     * - The identifier after "for" keyword in for loops
     * - Property names in object literals (identifier before : or =)
     *
     * NOTE: Identifiers that come AFTER = are VALUES (references), not declaration names.
     * Example: in "instanceType = instanceTypes", instanceType is a property name (not navigable),
     * but instanceTypes is a value/reference (should be navigable).
     */
    private boolean isDeclarationName(PsiElement element) {
        LOG.info("[isDeclarationName] Checking: '" + element.getText() + "'");

        // First, check if this identifier comes AFTER an equals sign
        // If so, it's a value (reference), not a declaration name - it SHOULD be navigable
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        LOG.info("[isDeclarationName] prev element: " + (prev != null ? prev.getText() + " (" + prev.getNode().getElementType() + ")" : "null"));
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.ASSIGN) {
            // This identifier comes after =, so it's a value, not a declaration name
            LOG.info("[isDeclarationName] '" + element.getText() + "' comes after ASSIGN -> returning false (is reference)");
            return false;
        }

        // Check if this identifier is followed by = or { or += or : (declaration/property pattern)
        PsiElement next = skipWhitespaceForward(element.getNextSibling());
        LOG.info("[isDeclarationName] next element: " + (next != null ? next.getText() + " (" + next.getNode().getElementType() + ")" : "null"));
        if (next != null) {
            IElementType nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.ASSIGN ||
                nextType == KiteTokenTypes.LBRACE ||
                nextType == KiteTokenTypes.PLUS_ASSIGN ||
                nextType == KiteTokenTypes.COLON) {
                // This identifier is followed by = or { or : - it's a declaration/property name
                LOG.info("[isDeclarationName] '" + element.getText() + "' followed by " + nextType + " -> returning true");
                return true;
            }
        }

        // Check if this is a for loop variable (identifier after "for" keyword)
        // Note: We already have 'prev' from the ASSIGN check above
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.FOR) {
            LOG.info("[isDeclarationName] '" + element.getText() + "' follows FOR -> returning true");
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
