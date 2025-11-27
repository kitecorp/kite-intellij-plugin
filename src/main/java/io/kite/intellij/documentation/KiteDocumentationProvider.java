package io.kite.intellij.documentation;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.KiteLanguage;
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
        sb.append("<div style=\"white-space: nowrap;\">");

        // Build documentation HTML
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append("<b>").append(kind).append("</b>");
        if (name != null) {
            sb.append(" ").append(name);
        }
        sb.append(DocumentationMarkup.DEFINITION_END);

        // Add signature section
        if (signature != null && !signature.isEmpty()) {
            sb.append(DocumentationMarkup.SECTIONS_START);
            sb.append(DocumentationMarkup.SECTION_HEADER_START);
            sb.append("Declaration:");
            sb.append(DocumentationMarkup.SECTION_SEPARATOR);
            sb.append("<code>").append(escapeHtml(signature)).append("</code>");
            sb.append(DocumentationMarkup.SECTION_END);
            sb.append(DocumentationMarkup.SECTIONS_END);
        }

        // Add comment section
        if (comment != null && !comment.isEmpty()) {
            sb.append(DocumentationMarkup.CONTENT_START);
            sb.append(escapeHtml(comment));
            sb.append(DocumentationMarkup.CONTENT_END);
        }

        // Add type-specific information
        String typeInfo = getTypeSpecificInfo(declaration, type);
        if (typeInfo != null && !typeInfo.isEmpty()) {
            sb.append(DocumentationMarkup.SECTIONS_START);
            sb.append(typeInfo);
            sb.append(DocumentationMarkup.SECTIONS_END);
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
     */
    @Nullable
    private String getPrecedingComment(PsiElement declaration) {
        PsiElement prev = declaration.getPrevSibling();

        // Skip whitespace
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }

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
                sb.append(DocumentationMarkup.SECTION_HEADER_START);
                sb.append("Resource Type:");
                sb.append(DocumentationMarkup.SECTION_SEPARATOR);
                sb.append("<code>").append(escapeHtml(resourceType)).append("</code>");
                sb.append(DocumentationMarkup.SECTION_END);
            }
        } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Extract component type
            String componentType = extractComponentType(declaration);
            if (componentType != null) {
                sb.append(DocumentationMarkup.SECTION_HEADER_START);
                sb.append("Component Type:");
                sb.append(DocumentationMarkup.SECTION_SEPARATOR);
                sb.append("<code>").append(escapeHtml(componentType)).append("</code>");
                sb.append(DocumentationMarkup.SECTION_END);
            }

            // Extract inputs
            java.util.List<String[]> inputs = extractComponentMembersWithParts(declaration, KiteElementTypes.INPUT_DECLARATION);
            if (!inputs.isEmpty()) {
                sb.append(DocumentationMarkup.SECTION_HEADER_START);
                sb.append("Inputs:");
                sb.append(DocumentationMarkup.SECTION_SEPARATOR);
                sb.append("<code>");
                sb.append(formatAlignedMembers(inputs));
                sb.append("</code>");
                sb.append(DocumentationMarkup.SECTION_END);
            }

            // Extract outputs
            java.util.List<String[]> outputs = extractComponentMembersWithParts(declaration, KiteElementTypes.OUTPUT_DECLARATION);
            if (!outputs.isEmpty()) {
                sb.append(DocumentationMarkup.SECTION_HEADER_START);
                sb.append("Outputs:");
                sb.append(DocumentationMarkup.SECTION_SEPARATOR);
                sb.append("<code>");
                sb.append(formatAlignedMembers(outputs));
                sb.append("</code>");
                sb.append(DocumentationMarkup.SECTION_END);
            }
        } else if (type == KiteElementTypes.VARIABLE_DECLARATION ||
                   type == KiteElementTypes.INPUT_DECLARATION ||
                   type == KiteElementTypes.OUTPUT_DECLARATION) {
            // Extract variable type
            String varType = extractVariableType(declaration);
            if (varType != null) {
                sb.append(DocumentationMarkup.SECTION_HEADER_START);
                sb.append("Type:");
                sb.append(DocumentationMarkup.SECTION_SEPARATOR);
                sb.append("<code>").append(escapeHtml(varType)).append("</code>");
                sb.append(DocumentationMarkup.SECTION_END);
            }

            // Extract default value
            String defaultValue = extractDefaultValue(declaration);
            if (defaultValue != null) {
                sb.append(DocumentationMarkup.SECTION_HEADER_START);
                sb.append("Default:");
                sb.append(DocumentationMarkup.SECTION_SEPARATOR);
                sb.append("<code>").append(escapeHtml(defaultValue)).append("</code>");
                sb.append(DocumentationMarkup.SECTION_END);
            }
        } else if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            // Extract parameters
            String params = extractFunctionParams(declaration);
            if (params != null) {
                sb.append(DocumentationMarkup.SECTION_HEADER_START);
                sb.append("Parameters:");
                sb.append(DocumentationMarkup.SECTION_SEPARATOR);
                sb.append("<code>").append(escapeHtml(params)).append("</code>");
                sb.append(DocumentationMarkup.SECTION_END);
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Extract resource type from a resource declaration.
     * e.g., "resource VM.Instance server { }" -> "VM.Instance"
     */
    @Nullable
    private String extractResourceType(PsiElement declaration) {
        StringBuilder typeBuilder = new StringBuilder();
        boolean foundResource = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.RESOURCE) {
                foundResource = true;
            } else if (foundResource) {
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    if (typeBuilder.length() > 0) {
                        // This is the name, not the type - stop here
                        break;
                    }
                    typeBuilder.append(child.getText());
                } else if (childType == KiteTokenTypes.DOT) {
                    typeBuilder.append(".");
                } else if (childType != KiteTokenTypes.WHITESPACE &&
                           childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                    // Check if we have a complete type before the name
                    PsiElement next = child.getNextSibling();
                    while (next != null && (next.getNode().getElementType() == KiteTokenTypes.WHITESPACE ||
                                            next.getNode().getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)) {
                        next = next.getNextSibling();
                    }
                    if (next != null && next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                        // Continue building type
                        typeBuilder.append(child.getText());
                    } else {
                        break;
                    }
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
     */
    @Nullable
    private String extractDefaultValue(PsiElement declaration) {
        boolean foundAssign = false;
        StringBuilder value = new StringBuilder();

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.ASSIGN) {
                foundAssign = true;
            } else if (foundAssign) {
                String text = child.getText().trim();
                if (!text.isEmpty() && childType != KiteTokenTypes.WHITESPACE &&
                    childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                    if (value.length() > 0) {
                        value.append(" ");
                    }
                    value.append(text);
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
            } else if (foundAssign &&
                       childType != KiteTokenTypes.WHITESPACE &&
                       childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                // Collect all non-whitespace tokens after = to form the value expression
                // This handles property access chains like server.tag.Name
                defaultValueBuilder.append(child.getText());
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
     * Format members with vertical alignment at '='.
     * Uses HTML non-breaking spaces (&nbsp;) for alignment.
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
            String left = parts[0] + " " + parts[1];

            result.append(escapeHtml(left));

            if (parts[2] != null) {
                // Add padding to align '='
                int padding = maxLeftLength - left.length();
                for (int p = 0; p < padding; p++) {
                    result.append("&nbsp;");
                }
                result.append(" = ").append(escapeHtml(parts[2]));
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
}
