package io.kite.intellij.documentation;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import io.kite.intellij.KiteLanguage;
import java.awt.Color;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Documentation provider for Kite language.
 * Shows quick documentation popup when pressing Ctrl+Q (or F1 on Mac) on declarations.
 */
public class KiteDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element == null) {
            return null;
        }

        // Only handle Kite files
        PsiFile file = element.getContainingFile();
        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        // Find the declaration containing this element
        PsiElement declaration = findDeclaration(element);
        if (declaration == null) {
            return null;
        }

        return generateDocumentation(declaration);
    }

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement, int targetOffset) {
        if (contextElement == null || file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        IElementType elementType = contextElement.getNode().getElementType();

        // Handle identifiers - find their declaration
        if (elementType == KiteTokenTypes.IDENTIFIER) {
            String name = contextElement.getText();
            PsiElement declaration = findDeclarationByName(file, name);
            if (declaration != null) {
                return declaration;
            }
            // If this identifier is itself a declaration name, return the parent declaration
            PsiElement parent = contextElement.getParent();
            if (isDeclaration(parent)) {
                return parent;
            }
        }

        // Handle interpolation tokens
        if (elementType == KiteTokenTypes.INTERP_IDENTIFIER || elementType == KiteTokenTypes.INTERP_SIMPLE) {
            String varName = contextElement.getText();
            if (elementType == KiteTokenTypes.INTERP_SIMPLE && varName.startsWith("$")) {
                varName = varName.substring(1);
            }
            return findDeclarationByName(file, varName);
        }

        return null;
    }

    /**
     * Find the declaration containing or being the given element.
     */
    @Nullable
    private PsiElement findDeclaration(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (isDeclaration(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if the element is a declaration.
     */
    private boolean isDeclaration(PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }
        IElementType type = element.getNode().getElementType();
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
     * Find a declaration by name in the file.
     */
    @Nullable
    private PsiElement findDeclarationByName(PsiFile file, String name) {
        return findDeclarationRecursive(file, name);
    }

    @Nullable
    private PsiElement findDeclarationRecursive(PsiElement element, String name) {
        if (element.getNode() == null) {
            return null;
        }

        IElementType type = element.getNode().getElementType();

        if (isDeclaration(element)) {
            String declName = getDeclarationName(element, type);
            if (name.equals(declName)) {
                return element;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclarationRecursive(child, name);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Get the name from a declaration element.
     */
    @Nullable
    private String getDeclarationName(PsiElement declaration, IElementType declarationType) {
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            // For statements: find identifier after 'for' keyword
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // For most declarations: find the last identifier before '=' or '{'
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
                    return lastIdentifier.getText();
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier != null ? lastIdentifier.getText() : null;
    }

    /**
     * Generate HTML documentation for a declaration.
     */
    @NotNull
    private String generateDocumentation(PsiElement declaration) {
        StringBuilder sb = new StringBuilder();
        IElementType type = declaration.getNode().getElementType();

        // Get the declaration kind
        String kind = getDeclarationKind(type);
        String name = getDeclarationName(declaration, type);
        String signature = getSignature(declaration, type);

        // Get preceding comment (if any)
        String comment = getPrecedingComment(declaration);

        // Add a wrapper div with no text wrapping - content stays on single lines with horizontal scroll
        // Use overflow-x: auto to enable horizontal scrolling, white-space: nowrap to prevent wrapping
        sb.append("<div style=\"white-space: nowrap; overflow-x: auto; max-width: 800px;\">");

        // Build documentation HTML (without background highlight)
        sb.append("<div style=\"margin-bottom: 8px;\">");
        sb.append("<b>").append(kind).append("</b>");
        if (name != null) {
            sb.append(" ").append(name);
        }
        sb.append("</div>");

        // Add signature section (plain HTML without background)
        if (signature != null && !signature.isEmpty()) {
            sb.append("<div style=\"margin-bottom: 4px;\">");
            sb.append("<span>Declaration:</span> ");
            sb.append("<code>").append(colorizeCode(signature)).append("</code>");
            sb.append("</div>");
        }

        // Add type-specific information (plain HTML without background)
        String typeInfo = getTypeSpecificInfo(declaration, type);
        if (typeInfo != null && !typeInfo.isEmpty()) {
            sb.append(typeInfo);
        }

        // Add decorators section (if any) - AFTER default value, BEFORE comment
        java.util.List<String> decorators = extractDecorators(declaration);
        if (!decorators.isEmpty()) {
            sb.append("<div style=\"margin-bottom: 8px; background-color: ").append(getSectionBackgroundColor()).append("; padding: 8px; border-radius: 4px;\">");
            sb.append("<span>Decorators:</span>");
            sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
            for (int i = 0; i < decorators.size(); i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                sb.append(colorizeDecoratorNoBreaks(decorators.get(i)));
            }
            sb.append("</pre>");
            sb.append("</div>");
        }

        // Add comment section LAST (after type-specific info like Outputs)
        // Comments can be long, so they appear at the end
        // Use theme-aware text color (inherits from dialog - white in dark theme, black in light theme)
        if (comment != null && !comment.isEmpty()) {
            sb.append("<div style=\"margin-top: 8px;margin-bottom: 8px;\">");
            sb.append(escapeHtml(comment));
            sb.append("</div>");
        }

        // Close the wrapper div
        sb.append("</div>");

        return sb.toString();
    }

    /**
     * Get the kind label for a declaration type.
     */
    @NotNull
    private String getDeclarationKind(IElementType type) {
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return "Variable";
        if (type == KiteElementTypes.INPUT_DECLARATION) return "Input";
        if (type == KiteElementTypes.OUTPUT_DECLARATION) return "Output";
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return "Resource";
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return "Component";
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return "Schema";
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return "Function";
        if (type == KiteElementTypes.TYPE_DECLARATION) return "Type";
        if (type == KiteElementTypes.FOR_STATEMENT) return "Loop Variable";
        return "Declaration";
    }

    /**
     * Get the signature (first line) of the declaration.
     */
    @Nullable
    private String getSignature(PsiElement declaration, IElementType type) {
        String text = declaration.getText();
        // Get first line only (up to first newline or { )
        int newlineIndex = text.indexOf('\n');
        int braceIndex = text.indexOf('{');

        int endIndex = text.length();
        if (newlineIndex > 0 && newlineIndex < endIndex) {
            endIndex = newlineIndex;
        }
        if (braceIndex > 0 && braceIndex < endIndex) {
            endIndex = braceIndex; // Stop BEFORE the brace (not including it)
        }

        String signature = text.substring(0, endIndex).trim();

        return signature;
    }

    /**
     * Get any preceding comment for the declaration.
     * Supports both line comments ({@code //}) and block comments ({@code /* ... *&#47;}).
     * Can collect multiple consecutive line comments into a single documentation block.
     * Skips over decorators (like @allowed([...])) to find comments above them.
     */
    @Nullable
    private String getPrecedingComment(PsiElement declaration) {
        PsiElement prev = declaration.getPrevSibling();

        // Skip whitespace
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }

        // Skip over decorators to find comments above them
        prev = skipOverDecorators(prev);

        if (prev == null) {
            return null;
        }

        // Check if it's a comment by token type (for Kite language elements)
        IElementType prevType = prev.getNode() != null ? prev.getNode().getElementType() : null;

        // Handle block comments
        if (prevType == KiteTokenTypes.BLOCK_COMMENT || prev instanceof PsiComment) {
            String commentText = prev.getText();
            if (commentText.startsWith("/*") && commentText.endsWith("*/")) {
                // Remove /* and */ and clean up the content
                String content = commentText.substring(2, commentText.length() - 2);
                return cleanBlockCommentContent(content);
            } else if (commentText.startsWith("//")) {
                return commentText.substring(2).trim();
            }
            return commentText.trim();
        }

        // Handle line comments - collect consecutive line comments
        if (prevType == KiteTokenTypes.LINE_COMMENT) {
            StringBuilder commentBuilder = new StringBuilder();
            java.util.List<String> lines = new java.util.ArrayList<>();

            // Collect all consecutive line comments
            while (prev != null) {
                prevType = prev.getNode() != null ? prev.getNode().getElementType() : null;

                if (prevType == KiteTokenTypes.LINE_COMMENT) {
                    String line = prev.getText();
                    if (line.startsWith("//")) {
                        line = line.substring(2).trim();
                    }
                    lines.add(0, line); // Add at beginning since we're going backwards
                } else if (isWhitespaceElement(prev)) {
                    // Continue through whitespace
                } else {
                    // Stop at non-comment, non-whitespace
                    break;
                }
                prev = prev.getPrevSibling();
            }

            if (!lines.isEmpty()) {
                return String.join("\n", lines);
            }
        }

        return null;
    }

    /**
     * Skip over decorators to find what's before them.
     * Decorators are token sequences starting with @ followed by identifier and optional parentheses.
     */
    @Nullable
    private PsiElement skipOverDecorators(PsiElement element) {
        if (element == null) return null;

        IElementType type = element.getNode() != null ? element.getNode().getElementType() : null;

        // If we're at a closing paren, we might be at the end of a decorator
        while (element != null) {
            type = element.getNode() != null ? element.getNode().getElementType() : null;

            // Check if this looks like the end of a decorator (closing paren or identifier after @)
            if (type == KiteTokenTypes.RPAREN) {
                // Skip back through the decorator arguments to find @
                element = skipToAtSymbol(element);
                if (element == null) return null;
                // Now element is at @, skip whitespace before it
                element = element.getPrevSibling();
                while (element != null && isWhitespaceElement(element)) {
                    element = element.getPrevSibling();
                }
            } else if (type == KiteTokenTypes.IDENTIFIER) {
                // Check if there's an @ immediately before this identifier (no-arg decorator)
                PsiElement beforeIdent = element.getPrevSibling();
                while (beforeIdent != null && isWhitespaceElement(beforeIdent)) {
                    beforeIdent = beforeIdent.getPrevSibling();
                }
                if (beforeIdent != null && beforeIdent.getNode() != null &&
                    beforeIdent.getNode().getElementType() == KiteTokenTypes.AT) {
                    // This is a no-arg decorator like @deprecated, skip it
                    element = beforeIdent.getPrevSibling();
                    while (element != null && isWhitespaceElement(element)) {
                        element = element.getPrevSibling();
                    }
                } else {
                    // Not a decorator, we're done
                    break;
                }
            } else if (type == KiteTokenTypes.AT) {
                // At an @ symbol, skip to before it
                element = element.getPrevSibling();
                while (element != null && isWhitespaceElement(element)) {
                    element = element.getPrevSibling();
                }
            } else {
                // Not part of a decorator
                break;
            }
        }

        return element;
    }

    /**
     * Skip backwards from a closing paren to find the @ symbol of a decorator.
     */
    @Nullable
    private PsiElement skipToAtSymbol(PsiElement closeParen) {
        int parenDepth = 1;
        PsiElement current = closeParen.getPrevSibling();

        // Skip through to find the opening paren
        while (current != null && parenDepth > 0) {
            IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
            if (type == KiteTokenTypes.RPAREN) {
                parenDepth++;
            } else if (type == KiteTokenTypes.LPAREN) {
                parenDepth--;
            }
            if (parenDepth > 0) {
                current = current.getPrevSibling();
            }
        }

        if (current == null) return null;

        // current is at LPAREN, skip to identifier
        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }

        if (current == null) return null;
        IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
        if (type != KiteTokenTypes.IDENTIFIER) return null;

        // Skip to @
        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }

        if (current != null && current.getNode() != null &&
            current.getNode().getElementType() == KiteTokenTypes.AT) {
            return current;
        }

        return null;
    }

    /**
     * Extract decorators from before a declaration.
     * Returns a list of decorator strings like "@allowed([\"dev\", \"prod\"])".
     */
    @NotNull
    private java.util.List<String> extractDecorators(PsiElement declaration) {
        java.util.List<String> decorators = new java.util.ArrayList<>();
        PsiElement prev = declaration.getPrevSibling();

        // Skip whitespace
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }

        // Collect decorators (walking backwards)
        while (prev != null) {
            IElementType type = prev.getNode() != null ? prev.getNode().getElementType() : null;

            // Check if this is the end of a decorator (closing paren or identifier after @)
            if (type == KiteTokenTypes.RPAREN) {
                // Decorator with arguments: @name(...)
                String decorator = collectDecoratorWithArgs(prev);
                if (decorator != null) {
                    decorators.add(0, decorator); // Add at beginning since we're going backwards
                    prev = skipToAtSymbol(prev);
                    if (prev != null) {
                        prev = prev.getPrevSibling();
                        while (prev != null && isWhitespaceElement(prev)) {
                            prev = prev.getPrevSibling();
                        }
                    }
                } else {
                    break;
                }
            } else if (type == KiteTokenTypes.IDENTIFIER) {
                // Check if there's an @ immediately before this identifier (no-arg decorator)
                PsiElement beforeIdent = prev.getPrevSibling();
                while (beforeIdent != null && isWhitespaceElement(beforeIdent)) {
                    beforeIdent = beforeIdent.getPrevSibling();
                }
                if (beforeIdent != null && beforeIdent.getNode() != null &&
                    beforeIdent.getNode().getElementType() == KiteTokenTypes.AT) {
                    // This is a no-arg decorator like @deprecated
                    decorators.add(0, "@" + prev.getText());
                    prev = beforeIdent.getPrevSibling();
                    while (prev != null && isWhitespaceElement(prev)) {
                        prev = prev.getPrevSibling();
                    }
                } else {
                    // Not a decorator
                    break;
                }
            } else {
                // Not part of a decorator
                break;
            }
        }

        return decorators;
    }

    /**
     * Collect a decorator with arguments, starting from the closing paren.
     * Returns the full decorator string like "@allowed([\"dev\", \"prod\"])".
     */
    @Nullable
    private String collectDecoratorWithArgs(PsiElement closeParen) {
        StringBuilder sb = new StringBuilder();
        java.util.List<String> tokens = new java.util.ArrayList<>();
        int parenDepth = 1;

        tokens.add(0, ")");
        PsiElement current = closeParen.getPrevSibling();

        // Collect tokens back to opening paren
        while (current != null && parenDepth > 0) {
            IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
            if (type == KiteTokenTypes.RPAREN) {
                parenDepth++;
                tokens.add(0, ")");
            } else if (type == KiteTokenTypes.LPAREN) {
                parenDepth--;
                tokens.add(0, "(");
            } else if (!isWhitespaceElement(current)) {
                tokens.add(0, current.getText());
            }
            current = current.getPrevSibling();
        }

        if (parenDepth != 0) return null;

        // Get identifier
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }
        if (current == null) return null;
        IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
        if (type != KiteTokenTypes.IDENTIFIER) return null;
        tokens.add(0, current.getText());

        // Get @
        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }
        if (current == null || current.getNode() == null ||
            current.getNode().getElementType() != KiteTokenTypes.AT) {
            return null;
        }
        tokens.add(0, "@");

        // Build the decorator string
        for (String token : tokens) {
            sb.append(token);
        }

        return sb.toString();
    }

    /**
     * Check if an element is whitespace.
     */
    private boolean isWhitespaceElement(PsiElement element) {
        if (element instanceof PsiWhiteSpace) {
            return true;
        }
        if (element.getNode() != null) {
            IElementType type = element.getNode().getElementType();
            return type == KiteTokenTypes.WHITESPACE ||
                   type == KiteTokenTypes.NL ||
                   type == KiteTokenTypes.NEWLINE ||
                   type == com.intellij.psi.TokenType.WHITE_SPACE;
        }
        return false;
    }

    /**
     * Clean up block comment content by removing leading asterisks and normalizing indentation.
     */
    @NotNull
    private String cleanBlockCommentContent(String content) {
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // Remove leading asterisks (common in multi-line comments)
            if (line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            if (!line.isEmpty()) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(line);
            }
        }

        return result.toString();
    }

    /**
     * Get type-specific additional information.
     */
    @Nullable
    private String getTypeSpecificInfo(PsiElement declaration, IElementType type) {
        StringBuilder sb = new StringBuilder();

        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            // Extract resource type
            String resourceType = extractResourceType(declaration);
            if (resourceType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Resource Type:</span> ");
                sb.append("<code>").append(escapeHtml(resourceType)).append("</code>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Extract component type
            String componentType = extractComponentType(declaration);
            if (componentType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Component Type:</span> ");
                sb.append("<code>").append(escapeHtml(componentType)).append("</code>");
                sb.append("</div>");
            }

            // Extract inputs
            java.util.List<String[]> inputs = extractComponentMembersWithParts(declaration, KiteElementTypes.INPUT_DECLARATION);
            if (!inputs.isEmpty()) {
                sb.append("<div style=\"margin-bottom: 8px; background-color: " + getSectionBackgroundColor() + "; padding: 8px; border-radius: 4px;\">");
                sb.append("<span>Inputs:</span>");
                sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
                sb.append(formatAlignedMembersPlain(inputs));
                sb.append("</pre>");
                sb.append("</div>");
            }

            // Extract outputs
            java.util.List<String[]> outputs = extractComponentMembersWithParts(declaration, KiteElementTypes.OUTPUT_DECLARATION);
            if (!outputs.isEmpty()) {
                sb.append("<div style=\"margin-bottom: 8px; background-color: " + getSectionBackgroundColor() + "; padding: 8px; border-radius: 4px;\">");
                sb.append("<span>Outputs:</span>");
                sb.append("<pre style=\"margin: 4px 0 0 0; padding: 0; font-family: monospace; background: transparent;\">");
                sb.append(formatAlignedMembersPlain(outputs));
                sb.append("</pre>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.VARIABLE_DECLARATION ||
                   type == KiteElementTypes.INPUT_DECLARATION ||
                   type == KiteElementTypes.OUTPUT_DECLARATION) {
            // Extract variable type
            String varType = extractVariableType(declaration);
            if (varType != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Type:</span> ");
                sb.append("<code>").append(escapeHtml(varType)).append("</code>");
                sb.append("</div>");
            }

            // Extract default value
            String defaultValue = extractDefaultValue(declaration);
            if (defaultValue != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Default:</span> ");
                sb.append("<code>").append(colorizeCode(defaultValue)).append("</code>");
                sb.append("</div>");
            }
        } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            // Extract parameters
            String params = extractFunctionParams(declaration);
            if (params != null) {
                sb.append("<div style=\"margin-bottom: 4px;\">");
                sb.append("<span>Parameters:</span> ");
                sb.append("<code>").append(escapeHtml(params)).append("</code>");
                sb.append("</div>");
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Extract resource type from a resource declaration.
     * e.g., "resource VM.Instance server { }" -> "VM.Instance"
     *
     * Pattern: resource <type> <name> { ... }
     * where <type> can be dotted like VM.Instance
     */
    @Nullable
    private String extractResourceType(PsiElement declaration) {
        StringBuilder typeBuilder = new StringBuilder();
        boolean foundResource = false;
        boolean lastWasDot = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.RESOURCE) {
                foundResource = true;
            } else if (foundResource) {
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    if (typeBuilder.length() > 0 && !lastWasDot) {
                        // Previous was identifier without dot between - this is the name, stop
                        break;
                    }
                    typeBuilder.append(child.getText());
                    lastWasDot = false;
                } else if (childType == KiteTokenTypes.DOT) {
                    typeBuilder.append(".");
                    lastWasDot = true;
                } else if (childType == KiteTokenTypes.WHITESPACE ||
                           childType == com.intellij.psi.TokenType.WHITE_SPACE) {
                    // Whitespace after identifier without dot means next identifier is the name
                    // But only if we already have some type content
                    if (typeBuilder.length() > 0 && !lastWasDot) {
                        // Look ahead to see if there's a name identifier after whitespace
                        PsiElement next = child.getNextSibling();
                        while (next != null && (next.getNode().getElementType() == KiteTokenTypes.WHITESPACE ||
                                                next.getNode().getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)) {
                            next = next.getNextSibling();
                        }
                        if (next != null && next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                            // Next identifier after whitespace is the name - stop here
                            break;
                        }
                    }
                } else if (childType == KiteTokenTypes.LBRACE) {
                    // Hit the opening brace, stop
                    break;
                }
            }

            child = child.getNextSibling();
        }

        return typeBuilder.length() > 0 ? typeBuilder.toString() : null;
    }

    /**
     * Extract component type from a component declaration.
     */
    @Nullable
    private String extractComponentType(PsiElement declaration) {
        boolean foundComponent = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.COMPONENT) {
                foundComponent = true;
            } else if (foundComponent && childType == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Extract variable type from a var/input/output declaration.
     * e.g., "input number port = 8080" -> "number"
     */
    @Nullable
    private String extractVariableType(PsiElement declaration) {
        boolean foundKeyword = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.VAR ||
                childType == KiteTokenTypes.INPUT ||
                childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
            } else if (foundKeyword && childType == KiteTokenTypes.IDENTIFIER) {
                // First identifier after keyword is the type
                return child.getText();
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Extract default value from a declaration.
     * Object and array literals are replaced with {...} and [...] placeholders to avoid wrapping.
     */
    @Nullable
    private String extractDefaultValue(PsiElement declaration) {
        boolean foundAssign = false;
        StringBuilder value = new StringBuilder();
        int braceDepth = 0;   // Track nested object literals
        int bracketDepth = 0; // Track nested array literals

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.ASSIGN) {
                foundAssign = true;
            } else if (foundAssign) {
                // Handle object literals - replace with {...}
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    if (braceDepth == 1) {
                        value.append("{...}");
                    }
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                }
                // Handle array literals - replace with [...]
                else if (childType == KiteTokenTypes.LBRACK) {
                    bracketDepth++;
                    if (bracketDepth == 1) {
                        value.append("[...]");
                    }
                } else if (childType == KiteTokenTypes.RBRACK) {
                    bracketDepth--;
                }
                // Only collect tokens when not inside a literal
                else if (braceDepth == 0 && bracketDepth == 0 &&
                         childType != KiteTokenTypes.WHITESPACE &&
                         childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                    String text = child.getText().trim();
                    if (!text.isEmpty()) {
                        // Don't add space before/after certain characters:
                        // - quotes (handles split string tokens)
                        // - dots (handles property access chains like server.tag.New)
                        boolean isQuote = text.equals("\"") || text.equals("'");
                        boolean isDot = text.equals(".");
                        boolean lastWasQuote = value.length() > 0 &&
                            (value.charAt(value.length() - 1) == '"' || value.charAt(value.length() - 1) == '\'');
                        boolean lastWasDot = value.length() > 0 && value.charAt(value.length() - 1) == '.';

                        if (value.length() > 0 && !isQuote && !lastWasQuote && !isDot && !lastWasDot) {
                            value.append(" ");
                        }
                        value.append(text);
                    }
                }
            }

            child = child.getNextSibling();
        }

        String result = value.toString().trim();
        // Truncate if too long
        if (result.length() > 50) {
            result = result.substring(0, 47) + "...";
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Extract function parameters from a function declaration.
     */
    @Nullable
    private String extractFunctionParams(PsiElement declaration) {
        String text = declaration.getText();
        int parenStart = text.indexOf('(');
        int parenEnd = text.indexOf(')');

        if (parenStart > 0 && parenEnd > parenStart) {
            return text.substring(parenStart + 1, parenEnd).trim();
        }

        return null;
    }

    /**
     * Extract component members (inputs or outputs) from a component declaration.
     * Returns a list of String arrays: [0]=type, [1]=name, [2]=defaultValue (or null)
     */
    @NotNull
    private java.util.List<String[]> extractComponentMembersWithParts(PsiElement declaration, IElementType memberType) {
        java.util.List<String[]> members = new java.util.ArrayList<>();
        extractMembersWithPartsRecursive(declaration, memberType, members);
        return members;
    }

    private void extractMembersWithPartsRecursive(PsiElement element, IElementType memberType, java.util.List<String[]> members) {
        if (element.getNode() == null) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        if (type == memberType) {
            // Format the member declaration: returns [type, name, defaultValue]
            String[] parts = formatMemberDeclarationParts(element, memberType);
            if (parts != null) {
                members.add(parts);
            }
            return; // Don't recurse into found members
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            extractMembersWithPartsRecursive(child, memberType, members);
            child = child.getNextSibling();
        }
    }

    /**
     * Format a member declaration into parts for alignment.
     * e.g., "input number port = 8080" -> ["number", "port", "8080"]
     * @return String array: [0]=type, [1]=name, [2]=defaultValue (or null if no default)
     */
    @Nullable
    private String[] formatMemberDeclarationParts(PsiElement member, IElementType memberType) {
        boolean foundKeyword = false;
        boolean foundType = false;
        String varType = null;
        String varName = null;
        StringBuilder defaultValueBuilder = new StringBuilder();
        boolean foundAssign = false;
        int braceDepth = 0;  // Track nested object literals

        PsiElement child = member.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.INPUT || childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
            } else if (foundKeyword && !foundType && childType == KiteTokenTypes.IDENTIFIER) {
                // First identifier after keyword is the type
                varType = child.getText();
                foundType = true;
            } else if (foundType && varName == null && childType == KiteTokenTypes.IDENTIFIER) {
                // Second identifier is the name
                varName = child.getText();
            } else if (childType == KiteTokenTypes.ASSIGN) {
                foundAssign = true;
            } else if (foundAssign) {
                // Track brace depth for object literals
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    // For object literals, just show "{...}" placeholder
                    if (braceDepth == 1) {
                        defaultValueBuilder.append("{...}");
                    }
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                } else if (braceDepth == 0 &&
                           childType != KiteTokenTypes.WHITESPACE &&
                           childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                    // Only collect tokens when not inside an object literal
                    defaultValueBuilder.append(child.getText());
                }
            }

            child = child.getNextSibling();
        }

        if (varType != null && varName != null) {
            String defaultValue = defaultValueBuilder.toString().trim();
            if (defaultValue.isEmpty()) {
                defaultValue = null;
            } else if (defaultValue.length() > 30) {
                // Truncate long default values
                defaultValue = defaultValue.substring(0, 27) + "...";
            }
            return new String[] { varType, varName, defaultValue };
        }

        return null;
    }

    /**
     * Format members as plain text with vertical alignment at '='.
     * Uses regular spaces for alignment (for use inside <pre> tags).
     * Applies syntax highlighting colors to types and values.
     */
    @NotNull
    private String formatAlignedMembersPlain(java.util.List<String[]> members) {
        if (members.isEmpty()) {
            return "";
        }

        // Find max length of "type name" portion (before '=')
        int maxLeftLength = 0;
        for (String[] parts : members) {
            int leftLength = parts[0].length() + 1 + parts[1].length(); // "type name"
            if (leftLength > maxLeftLength) {
                maxLeftLength = leftLength;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                result.append("\n");
            }

            String[] parts = members.get(i);
            String typeName = parts[0];
            String varName = parts[1];

            // Colorize the type name if it's a known type
            if (TYPE_NAMES.contains(typeName)) {
                result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                      .append(escapeHtmlNoBreaks(typeName)).append("</span>");
            } else {
                result.append(escapeHtmlNoBreaks(typeName));
            }
            result.append(" ").append(escapeHtmlNoBreaks(varName));

            if (parts[2] != null) {
                // Add padding to align '='
                String left = typeName + " " + varName;
                int padding = maxLeftLength - left.length();
                for (int p = 0; p < padding; p++) {
                    result.append(" ");
                }
                result.append(" = ").append(colorizeCodeNoBreaks(parts[2]));
            }
        }

        return result.toString();
    }

    /**
     * Format members with vertical alignment at '='.
     * Uses HTML non-breaking spaces (&nbsp;) for alignment.
     * Applies syntax highlighting colors to types and values.
     */
    @NotNull
    private String formatAlignedMembers(java.util.List<String[]> members) {
        if (members.isEmpty()) {
            return "";
        }

        // Find max length of "type name" portion (before '=')
        int maxLeftLength = 0;
        for (String[] parts : members) {
            int leftLength = parts[0].length() + 1 + parts[1].length(); // "type name"
            if (leftLength > maxLeftLength) {
                maxLeftLength = leftLength;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                result.append("<br/>");
            }

            String[] parts = members.get(i);
            String typeName = parts[0];
            String varName = parts[1];

            // Colorize the type name if it's a known type
            if (TYPE_NAMES.contains(typeName)) {
                result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                      .append(escapeHtml(typeName)).append("</span>");
            } else {
                result.append(escapeHtml(typeName));
            }
            result.append(" ").append(escapeHtml(varName));

            if (parts[2] != null) {
                // Add padding to align '='
                String left = typeName + " " + varName;
                int padding = maxLeftLength - left.length();
                for (int p = 0; p < padding; p++) {
                    result.append("&nbsp;");
                }
                result.append(" = ").append(colorizeCode(parts[2]));
            }
        }

        return result.toString();
    }

    /**
     * Escape HTML special characters.
     */
    @NotNull
    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br/>");
    }

    /**
     * Escape HTML special characters without converting newlines.
     * For use inside <pre> tags where newlines should be preserved as-is.
     */
    @NotNull
    private String escapeHtmlNoBreaks(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    // CSS colors for syntax highlighting in documentation (matching editor colors)
    private static final String COLOR_KEYWORD = "#AB5FDB";   // Purple - keywords (matches KiteSyntaxHighlighter.KEYWORD)
    private static final String COLOR_TYPE = "#498BF6";      // Blue - type names (matches KiteAnnotator.TYPE_NAME)
    private static final String COLOR_STRING = "#6A9955";    // Green - string literals (matches KiteSyntaxHighlighter.STRING)
    private static final String COLOR_NUMBER = "#1750EB";    // Blue - number literals

    // Keywords that should be colored
    private static final java.util.Set<String> KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "resource", "component", "schema", "fun", "var", "input", "output", "type",
        "if", "else", "for", "while", "in", "return", "import", "from", "init", "this",
        "true", "false", "null"
    ));

    // Built-in type names
    private static final java.util.Set<String> TYPE_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
        "string", "number", "boolean", "object", "any", "void", "list", "map"
    ));

    /**
     * Colorize code text with syntax highlighting (no newline conversion).
     * For use inside <pre> tags where newlines should be preserved.
     */
    @NotNull
    private String colorizeCodeNoBreaks(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Handle string literals (quoted strings)
            if (c == '"') {
                int start = i;
                i++; // skip opening quote
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) {
                        i++; // skip escaped char
                    }
                    i++;
                }
                if (i < len) i++; // skip closing quote
                String str = escapeHtmlNoBreaks(text.substring(start, i));
                result.append("<span style=\"color: ").append(COLOR_STRING).append(";\">").append(str).append("</span>");
                continue;
            }

            // Handle identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String word = text.substring(start, i);

                if (KEYWORDS.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append("; font-weight: bold;\">")
                          .append(escapeHtmlNoBreaks(word)).append("</span>");
                } else if (TYPE_NAMES.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                          .append(escapeHtmlNoBreaks(word)).append("</span>");
                } else {
                    result.append(escapeHtmlNoBreaks(word));
                }
                continue;
            }

            // Handle numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                String num = text.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                      .append(escapeHtmlNoBreaks(num)).append("</span>");
                continue;
            }

            // Other characters - just escape and append
            result.append(escapeHtmlNoBreaks(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    /**
     * Colorize code text with syntax highlighting.
     * This applies coloring to keywords, types, strings, and numbers.
     */
    @NotNull
    private String colorizeCode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Handle string literals (quoted strings)
            if (c == '"') {
                int start = i;
                i++; // skip opening quote
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) {
                        i++; // skip escaped char
                    }
                    i++;
                }
                if (i < len) i++; // skip closing quote
                String str = escapeHtml(text.substring(start, i));
                result.append("<span style=\"color: ").append(COLOR_STRING).append(";\">").append(str).append("</span>");
                continue;
            }

            // Handle identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String word = text.substring(start, i);

                if (KEYWORDS.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append("; font-weight: bold;\">")
                          .append(escapeHtml(word)).append("</span>");
                } else if (TYPE_NAMES.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                          .append(escapeHtml(word)).append("</span>");
                } else {
                    result.append(escapeHtml(word));
                }
                continue;
            }

            // Handle numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                String num = text.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                      .append(escapeHtml(num)).append("</span>");
                continue;
            }

            // Other characters - just escape and append
            result.append(escapeHtml(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    /**
     * Colorize decorator text with syntax highlighting.
     * The @ symbol and decorator name get special coloring, arguments get colorized like code.
     */
    @NotNull
    private String colorizeDecoratorNoBreaks(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        // Handle @ symbol
        if (i < len && text.charAt(i) == '@') {
            result.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">@</span>");
            i++;
        }

        // Handle decorator name (identifier after @)
        if (i < len && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '_')) {
            int start = i;
            while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                i++;
            }
            String decoratorName = text.substring(start, i);
            result.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">")
                  .append(escapeHtmlNoBreaks(decoratorName)).append("</span>");
        }

        // Handle arguments (everything after decorator name) - colorize like code
        if (i < len) {
            String args = text.substring(i);
            result.append(colorizeCodeNoBreaks(args));
        }

        return result.toString();
    }

    // Decorator color - orange to match editor
    private static final String COLOR_DECORATOR = "#CC7832";

    /**
     * Gets a theme-aware section background color.
     * In light theme: slightly darker than background (like a subtle card)
     * In dark theme: slightly lighter than background (like a subtle card)
     * @return hex color string like "#e8e8e8" for light theme or "#3c3f41" for dark theme
     */
    private static String getSectionBackgroundColor() {
        // JBColor automatically picks the right color based on current theme
        // First value is for light theme, second is for dark theme
        Color bgColor = new JBColor(
            new Color(0xe8, 0xe8, 0xe8),  // Light theme: light gray
            new Color(0x3c, 0x3f, 0x41)   // Dark theme: slightly lighter than default dark bg
        );
        return String.format("#%02x%02x%02x", bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
    }
}
