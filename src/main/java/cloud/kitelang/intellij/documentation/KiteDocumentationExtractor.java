package cloud.kitelang.intellij.documentation;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static cloud.kitelang.intellij.documentation.KiteDocumentationHtmlHelper.cleanBlockCommentContent;

/**
 * Extracts information from Kite PSI elements for documentation.
 * <p>
 * This class provides utilities for:
 * <ul>
 *   <li>Finding declarations in the PSI tree</li>
 *   <li>Extracting names, types, and signatures from declarations</li>
 *   <li>Extracting comments, decorators, and default values</li>
 *   <li>Component member extraction (inputs/outputs)</li>
 * </ul>
 * <p>
 * <b>Declaration Types Supported:</b>
 * <ul>
 *   <li>VARIABLE_DECLARATION - var statements</li>
 *   <li>INPUT_DECLARATION - input parameters</li>
 *   <li>OUTPUT_DECLARATION - output values</li>
 *   <li>RESOURCE_DECLARATION - cloud resources</li>
 *   <li>COMPONENT_DECLARATION - reusable components</li>
 *   <li>SCHEMA_DECLARATION - type schemas</li>
 *   <li>FUNCTION_DECLARATION - functions</li>
 *   <li>TYPE_DECLARATION - type aliases</li>
 *   <li>FOR_STATEMENT - loop variables</li>
 * </ul>
 *
 * @see KiteDocumentationProvider for the main documentation provider
 * @see KiteDocumentationHtmlHelper for HTML formatting
 */
public final class KiteDocumentationExtractor {

    private KiteDocumentationExtractor() {
        // Utility class
    }

    /**
     * Check if the element is a declaration.
     */
    public static boolean isDeclaration(@Nullable PsiElement element) {
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
     * Find the declaration containing or being the given element.
     */
    @Nullable
    public static PsiElement findDeclaration(PsiElement element) {
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
     * Find a declaration by name in the file.
     */
    @Nullable
    public static PsiElement findDeclarationByName(PsiFile file, String name) {
        return findDeclarationRecursive(file, name);
    }

    @Nullable
    private static PsiElement findDeclarationRecursive(PsiElement element, String name) {
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
    public static String getDeclarationName(PsiElement declaration, IElementType declarationType) {
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
     * Get the kind label for a declaration type.
     */
    @NotNull
    public static String getDeclarationKind(IElementType type) {
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
    public static String getSignature(PsiElement declaration) {
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

        return text.substring(0, endIndex).trim();
    }

    /**
     * Get any preceding comment for the declaration.
     * Supports both line comments ({@code //}) and block comments ({@code /* ... *&#47;}).
     * Can collect multiple consecutive line comments into a single documentation block.
     * Skips over decorators (like @allowed([...])) to find comments above them.
     */
    @Nullable
    public static String getPrecedingComment(PsiElement declaration) {
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
            List<String> lines = new ArrayList<>();

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
     */
    @Nullable
    private static PsiElement skipOverDecorators(PsiElement element) {
        if (element == null) return null;

        IElementType type = element.getNode() != null ? element.getNode().getElementType() : null;

        while (element != null) {
            type = element.getNode() != null ? element.getNode().getElementType() : null;

            if (type == KiteTokenTypes.RPAREN) {
                element = skipToAtSymbol(element);
                if (element == null) return null;
                element = element.getPrevSibling();
                while (element != null && isWhitespaceElement(element)) {
                    element = element.getPrevSibling();
                }
            } else if (type == KiteTokenTypes.IDENTIFIER) {
                PsiElement beforeIdent = element.getPrevSibling();
                while (beforeIdent != null && isWhitespaceElement(beforeIdent)) {
                    beforeIdent = beforeIdent.getPrevSibling();
                }
                if (beforeIdent != null && beforeIdent.getNode() != null &&
                    beforeIdent.getNode().getElementType() == KiteTokenTypes.AT) {
                    element = beforeIdent.getPrevSibling();
                    while (element != null && isWhitespaceElement(element)) {
                        element = element.getPrevSibling();
                    }
                } else {
                    break;
                }
            } else if (type == KiteTokenTypes.AT) {
                element = element.getPrevSibling();
                while (element != null && isWhitespaceElement(element)) {
                    element = element.getPrevSibling();
                }
            } else {
                break;
            }
        }

        return element;
    }

    /**
     * Skip backwards from a closing paren to find the @ symbol of a decorator.
     */
    @Nullable
    private static PsiElement skipToAtSymbol(PsiElement closeParen) {
        int parenDepth = 1;
        PsiElement current = closeParen.getPrevSibling();

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

        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }

        if (current == null) return null;
        IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
        if (type != KiteTokenTypes.IDENTIFIER) return null;

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
    public static List<String> extractDecorators(PsiElement declaration) {
        List<String> decorators = new ArrayList<>();
        PsiElement prev = declaration.getPrevSibling();

        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }

        while (prev != null) {
            IElementType type = prev.getNode() != null ? prev.getNode().getElementType() : null;

            if (type == KiteTokenTypes.RPAREN) {
                String decorator = collectDecoratorWithArgs(prev);
                if (decorator != null) {
                    decorators.add(0, decorator);
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
                PsiElement beforeIdent = prev.getPrevSibling();
                while (beforeIdent != null && isWhitespaceElement(beforeIdent)) {
                    beforeIdent = beforeIdent.getPrevSibling();
                }
                if (beforeIdent != null && beforeIdent.getNode() != null &&
                    beforeIdent.getNode().getElementType() == KiteTokenTypes.AT) {
                    decorators.add(0, "@" + prev.getText());
                    prev = beforeIdent.getPrevSibling();
                    while (prev != null && isWhitespaceElement(prev)) {
                        prev = prev.getPrevSibling();
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return decorators;
    }

    /**
     * Collect a decorator with arguments, starting from the closing paren.
     */
    @Nullable
    private static String collectDecoratorWithArgs(PsiElement closeParen) {
        List<String> tokens = new ArrayList<>();
        int parenDepth = 1;

        tokens.add(0, ")");
        PsiElement current = closeParen.getPrevSibling();

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

        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }
        if (current == null) return null;
        IElementType type = current.getNode() != null ? current.getNode().getElementType() : null;
        if (type != KiteTokenTypes.IDENTIFIER) return null;
        tokens.add(0, current.getText());

        current = current.getPrevSibling();
        while (current != null && isWhitespaceElement(current)) {
            current = current.getPrevSibling();
        }
        if (current == null || current.getNode() == null ||
            current.getNode().getElementType() != KiteTokenTypes.AT) {
            return null;
        }
        tokens.add(0, "@");

        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            sb.append(token);
        }

        return sb.toString();
    }

    /**
     * Extract resource type from a resource declaration.
     * e.g., "resource VM.Instance server { }" -> "VM.Instance"
     */
    @Nullable
    public static String extractResourceType(PsiElement declaration) {
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
                        break;
                    }
                    typeBuilder.append(child.getText());
                    lastWasDot = false;
                } else if (childType == KiteTokenTypes.DOT) {
                    typeBuilder.append(".");
                    lastWasDot = true;
                } else if (childType == KiteTokenTypes.WHITESPACE ||
                           childType == com.intellij.psi.TokenType.WHITE_SPACE) {
                    if (typeBuilder.length() > 0 && !lastWasDot) {
                        PsiElement next = child.getNextSibling();
                        while (next != null && (next.getNode().getElementType() == KiteTokenTypes.WHITESPACE ||
                                                next.getNode().getElementType() == com.intellij.psi.TokenType.WHITE_SPACE)) {
                            next = next.getNextSibling();
                        }
                        if (next != null && next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                            break;
                        }
                    }
                } else if (childType == KiteTokenTypes.LBRACE) {
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
    public static String extractComponentType(PsiElement declaration) {
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
    public static String extractVariableType(PsiElement declaration) {
        boolean foundKeyword = false;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.VAR ||
                childType == KiteTokenTypes.INPUT ||
                childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
            } else if (foundKeyword && childType == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            }

            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Extract default value from a declaration.
     * Object and array literals are replaced with {...} and [...] placeholders.
     */
    @Nullable
    public static String extractDefaultValue(PsiElement declaration) {
        boolean foundAssign = false;
        StringBuilder value = new StringBuilder();
        int braceDepth = 0;
        int bracketDepth = 0;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.ASSIGN) {
                foundAssign = true;
            } else if (foundAssign) {
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    if (braceDepth == 1) {
                        value.append("{...}");
                    }
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                } else if (childType == KiteTokenTypes.LBRACK) {
                    bracketDepth++;
                    if (bracketDepth == 1) {
                        value.append("[...]");
                    }
                } else if (childType == KiteTokenTypes.RBRACK) {
                    bracketDepth--;
                } else if (braceDepth == 0 && bracketDepth == 0 &&
                           childType != KiteTokenTypes.WHITESPACE &&
                           childType != com.intellij.psi.TokenType.WHITE_SPACE) {
                    String text = child.getText().trim();
                    if (!text.isEmpty()) {
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
        if (result.length() > 50) {
            result = result.substring(0, 47) + "...";
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Extract function parameters from a function declaration.
     */
    @Nullable
    public static String extractFunctionParams(PsiElement declaration) {
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
    public static List<String[]> extractComponentMembersWithParts(PsiElement declaration, IElementType memberType) {
        List<String[]> members = new ArrayList<>();
        extractMembersWithPartsRecursive(declaration, memberType, members);
        return members;
    }

    private static void extractMembersWithPartsRecursive(PsiElement element, IElementType memberType, List<String[]> members) {
        if (element.getNode() == null) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        if (type == memberType) {
            String[] parts = formatMemberDeclarationParts(element, memberType);
            if (parts != null) {
                members.add(parts);
            }
            return;
        }

        PsiElement child = element.getFirstChild();
        while (child != null) {
            extractMembersWithPartsRecursive(child, memberType, members);
            child = child.getNextSibling();
        }
    }

    /**
     * Format a member declaration into parts for alignment.
     * e.g., "input number port = 8080" -> ["number", "port", "8080"]
     */
    @Nullable
    public static String[] formatMemberDeclarationParts(PsiElement member, IElementType memberType) {
        boolean foundKeyword = false;
        boolean foundType = false;
        String varType = null;
        String varName = null;
        StringBuilder defaultValueBuilder = new StringBuilder();
        boolean foundAssign = false;
        int braceDepth = 0;

        PsiElement child = member.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.INPUT || childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
            } else if (foundKeyword && !foundType && childType == KiteTokenTypes.ANY) {
                varType = "any";
                foundType = true;
            } else if (foundKeyword && !foundType && childType == KiteTokenTypes.IDENTIFIER) {
                varType = child.getText();
                foundType = true;
            } else if (foundType && varName == null && childType == KiteTokenTypes.IDENTIFIER) {
                varName = child.getText();
            } else if (childType == KiteTokenTypes.ASSIGN) {
                foundAssign = true;
            } else if (foundAssign) {
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    if (braceDepth == 1) {
                        defaultValueBuilder.append("{...}");
                    }
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                } else if (braceDepth == 0 &&
                           childType != KiteTokenTypes.WHITESPACE &&
                           childType != com.intellij.psi.TokenType.WHITE_SPACE) {
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
                defaultValue = defaultValue.substring(0, 27) + "...";
            }
            return new String[] { varType, varName, defaultValue };
        }

        return null;
    }

    /**
     * Check if an element is whitespace.
     */
    public static boolean isWhitespaceElement(PsiElement element) {
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
     * Check if the element is a decorator name (identifier immediately after @).
     */
    public static boolean isDecoratorName(PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }
        if (element.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return false;
        }
        PsiElement prev = element.getPrevSibling();
        while (prev != null && isWhitespaceElement(prev)) {
            prev = prev.getPrevSibling();
        }
        return prev != null && prev.getNode() != null &&
               prev.getNode().getElementType() == KiteTokenTypes.AT;
    }
}
