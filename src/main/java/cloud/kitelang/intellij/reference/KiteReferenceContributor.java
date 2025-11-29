package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference contributor for Kite language.
 * Provides references for identifiers to enable "Go to Declaration" (Cmd+Click).
 */
public class KiteReferenceContributor extends PsiReferenceContributor {
    private static final Logger LOG = Logger.getInstance(KiteReferenceContributor.class);

    // Patterns for string interpolation
    private static final Pattern BRACE_INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern SIMPLE_INTERPOLATION_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        LOG.info("[KiteRefContrib] Registering reference providers");

        // Register reference provider for ANY PsiElement, then filter inside
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context) {

                        // Only handle Kite files
                        PsiFile file = element.getContainingFile();
                        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        if (element.getNode() == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        IElementType elementType = element.getNode().getElementType();

                        // Handle legacy STRING tokens with interpolations (for backwards compatibility)
                        if (elementType == KiteTokenTypes.STRING) {
                            return getStringInterpolationReferences(element);
                        }

                        // Handle INTERP_IDENTIFIER (the identifier inside ${...})
                        // e.g., in "${port}", the INTERP_IDENTIFIER token is "port"
                        if (elementType == KiteTokenTypes.INTERP_IDENTIFIER) {
                            String varName = element.getText();
                            LOG.info("[KiteRefContrib] Creating reference for INTERP_IDENTIFIER: " + varName);
                            TextRange range = new TextRange(0, varName.length());
                            return new PsiReference[]{new KiteStringInterpolationReference(element, range, varName)};
                        }

                        // Handle INTERP_SIMPLE (the $identifier token for simple interpolation)
                        // e.g., "$port" - the whole token includes the $
                        if (elementType == KiteTokenTypes.INTERP_SIMPLE) {
                            String text = element.getText();
                            // Extract variable name by removing the leading $
                            if (text.startsWith("$") && text.length() > 1) {
                                String varName = text.substring(1);
                                LOG.info("[KiteRefContrib] Creating reference for INTERP_SIMPLE: " + varName);
                                // Reference range is just the identifier part (after the $)
                                TextRange range = new TextRange(1, text.length());
                                return new PsiReference[]{new KiteStringInterpolationReference(element, range, varName)};
                            }
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // Only provide references for IDENTIFIER tokens
                        if (elementType != KiteTokenTypes.IDENTIFIER) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // Don't provide references for declaration names (the name being declared)
                        // e.g., in "input number port = 8080", "port" is a declaration name, not a reference
                        if (isDeclarationName(element)) {
                            LOG.info("[KiteRefContrib] Skipping declaration name: " + element.getText());
                            return PsiReference.EMPTY_ARRAY;
                        }

                        LOG.info("[KiteRefContrib] Creating reference for: " + element.getText());

                        // Create a reference for this identifier
                        TextRange range = new TextRange(0, element.getTextLength());
                        return new PsiReference[]{new KiteReference(element, range)};
                    }
                }
        );
    }

    /**
     * Get references for string interpolation expressions like ${var} or $var.
     * Each interpolation variable gets its own reference with precise text range.
     */
    private static PsiReference[] getStringInterpolationReferences(@NotNull PsiElement element) {
        String text = element.getText();
        LOG.info("[KiteRefContrib] Getting string interpolation references for: " + text);
        List<PsiReference> references = new ArrayList<>();

        // Handle ${expression} patterns
        Matcher braceMatcher = BRACE_INTERPOLATION_PATTERN.matcher(text);
        while (braceMatcher.find()) {
            int contentStart = braceMatcher.start(1);  // Start of content inside ${}
            int contentEnd = braceMatcher.end(1);      // End of content inside ${}
            String content = braceMatcher.group(1);

            // Extract the first identifier from the expression
            String varName = extractFirstIdentifier(content);
            if (varName != null) {
                // Create a reference that covers just the variable name portion
                TextRange range = new TextRange(contentStart, contentStart + varName.length());
                LOG.info("[KiteRefContrib] Created brace interpolation reference: varName=" + varName + ", range=" + range);
                references.add(new KiteStringInterpolationReference(element, range, varName));
            }
        }

        // Handle $var patterns (but skip if already part of ${...})
        Matcher simpleMatcher = SIMPLE_INTERPOLATION_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            int matchStart = simpleMatcher.start();

            // Skip if this $ is immediately followed by { (part of ${} syntax)
            if (matchStart + 1 < text.length() && text.charAt(matchStart + 1) == '{') {
                continue;
            }

            int varStart = simpleMatcher.start(1);  // Start of variable name (after $)
            int varEnd = simpleMatcher.end(1);      // End of variable name
            String varName = simpleMatcher.group(1);

            // Create a reference that covers just the variable name
            TextRange range = new TextRange(varStart, varEnd);
            references.add(new KiteStringInterpolationReference(element, range, varName));
        }

        return references.toArray(new PsiReference[0]);
    }

    /**
     * Extract the first identifier from an expression.
     * For "obj.prop" returns "obj", for "func()" returns "func", etc.
     */
    @Nullable
    private static String extractFirstIdentifier(String expression) {
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
     * - Property names inside schema bodies (identifier after a type)
     *
     * NOTE: Identifiers that come AFTER = are VALUES (references), not declaration names.
     * Example: in "instanceType = instanceTypes", instanceType is a property name (not navigable),
     * but instanceTypes is a value/reference (should be navigable).
     */
    private static boolean isDeclarationName(@NotNull PsiElement element) {
        // First, check if this identifier comes AFTER an equals sign
        // If so, it's a value (reference), not a declaration name - it SHOULD be navigable
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        LOG.info("[isDeclarationName] Checking: '" + element.getText() + "', prev=" +
                 (prev != null ? prev.getText() + " (" + prev.getNode().getElementType() + ")" : "null"));
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.ASSIGN) {
            // This identifier comes after =, so it's a value, not a declaration name
            LOG.info("[isDeclarationName] '" + element.getText() + "' comes after ASSIGN -> returning false (is reference)");
            return false;
        }

        // Check if this identifier is followed by = or { or += (declaration pattern)
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
        // Note: We already have 'prev' from earlier check, but it's valid here since
        // we only reach this point if prev wasn't ASSIGN
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.FOR) {
            return true;
        }

        // Check if this is a schema property name (identifier after a type, inside schema body)
        // Schema body pattern: schema Name { type propertyName ... }
        if (isSchemaPropertyName(element, prev)) {
            LOG.info("[isDeclarationName] '" + element.getText() + "' is schema property name -> returning true");
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
                    LOG.info("[isDeclarationName] '" + element.getText() + "' is declaration name in parent -> returning true");
                    return true;
                }
            }
        }

        LOG.info("[isDeclarationName] '" + element.getText() + "' -> returning false (is reference)");
        return false;
    }

    /**
     * Check if this identifier is a property name inside a schema body.
     * Pattern: schema Name { type propertyName [= defaultValue] }
     * The property name comes after a type identifier or type keyword (like 'any').
     */
    private static boolean isSchemaPropertyName(@NotNull PsiElement element, @Nullable PsiElement prev) {
        LOG.info("[isSchemaPropertyName] Checking: '" + element.getText() + "', prev=" +
                 (prev != null ? "'" + prev.getText() + "' (" + prev.getNode().getElementType() + ")" : "null"));

        // First, check if we're inside a schema declaration by walking up parents
        boolean insideSchema = false;
        PsiElement parent = element.getParent();
        int depth = 0;
        while (parent != null) {
            if (parent.getNode() != null) {
                IElementType parentType = parent.getNode().getElementType();
                LOG.info("[isSchemaPropertyName] Parent[" + depth + "]: " + parentType);
                if (parentType == KiteElementTypes.SCHEMA_DECLARATION) {
                    insideSchema = true;
                    LOG.info("[isSchemaPropertyName] Found SCHEMA_DECLARATION at depth " + depth);
                    break;
                }
                // Stop if we hit another declaration type (but not schema)
                if (isDeclarationType(parentType) && parentType != KiteElementTypes.SCHEMA_DECLARATION) {
                    LOG.info("[isSchemaPropertyName] Hit other declaration type: " + parentType + " -> returning false");
                    return false;
                }
            }
            parent = parent.getParent();
            depth++;
        }

        if (insideSchema) {
            // We're inside a schema - check if this identifier follows a type
            boolean result = isAfterTypeInSchemaBody(element, prev);
            LOG.info("[isSchemaPropertyName] insideSchema=true, isAfterType=" + result);
            return result;
        }

        // Alternative check: if not found via parent walk, try sibling-based detection
        // This handles cases where the PSI tree structure is flat
        LOG.info("[isSchemaPropertyName] Trying sibling-based detection");
        boolean siblingResult = isSchemaPropertyBySiblings(element);
        LOG.info("[isSchemaPropertyName] Sibling-based result: " + siblingResult);
        return siblingResult;
    }

    /**
     * Alternative check for schema property names using sibling traversal.
     * Walks backward to find a schema keyword followed by an identifier and {.
     */
    private static boolean isSchemaPropertyBySiblings(@NotNull PsiElement element) {
        LOG.info("[isSchemaPropertyBySiblings] Checking: '" + element.getText() + "'");
        // Walk backward through siblings at the file level to find schema { structure
        PsiElement current = element;
        int braceDepth = 0;
        boolean foundLBrace = false;
        boolean foundSchemaKeyword = false;
        boolean passedSchemaName = false;
        int steps = 0;

        while (current != null && steps < 100) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                LOG.info("[isSchemaPropertyBySiblings] Step " + steps + ": " + type + " = '" + current.getText().replace("\n", "\\n") + "'");

                if (type == KiteTokenTypes.RBRACE) {
                    braceDepth++;
                } else if (type == KiteTokenTypes.LBRACE) {
                    if (braceDepth > 0) {
                        braceDepth--;
                    } else {
                        foundLBrace = true;
                        LOG.info("[isSchemaPropertyBySiblings] Found LBRACE (schema opening)");
                    }
                } else if (foundLBrace && braceDepth == 0 && type == KiteTokenTypes.IDENTIFIER) {
                    // This is the schema name after 'schema' keyword
                    passedSchemaName = true;
                    LOG.info("[isSchemaPropertyBySiblings] Passed schema name: " + current.getText());
                } else if (foundLBrace && passedSchemaName && type == KiteTokenTypes.SCHEMA) {
                    foundSchemaKeyword = true;
                    LOG.info("[isSchemaPropertyBySiblings] Found SCHEMA keyword!");
                    break;
                } else if (type == KiteTokenTypes.RESOURCE || type == KiteTokenTypes.COMPONENT ||
                           type == KiteTokenTypes.FUN) {
                    // We're inside a different declaration, not a schema
                    LOG.info("[isSchemaPropertyBySiblings] Found non-schema declaration: " + type);
                    return false;
                }
            }
            current = current.getPrevSibling();
            steps++;
        }

        if (!foundSchemaKeyword) {
            LOG.info("[isSchemaPropertyBySiblings] Did not find schema keyword");
            return false;
        }

        // We're inside a schema body. Now check if this identifier follows a type.
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        boolean result = isAfterTypeInSchemaBody(element, prev);
        LOG.info("[isSchemaPropertyBySiblings] isAfterTypeInSchemaBody result: " + result);
        return result;
    }

    /**
     * Check if an identifier follows a type token inside a schema body.
     * This handles patterns like:
     * - string propertyName
     * - number[] propertyName
     * - any propertyName
     * - CustomType propertyName
     */
    private static boolean isAfterTypeInSchemaBody(@NotNull PsiElement element, @Nullable PsiElement prev) {
        LOG.info("[isAfterTypeInSchemaBody] element='" + element.getText() + "', prev=" +
                 (prev != null ? "'" + prev.getText() + "' (" + prev.getNode().getElementType() + ")" : "null"));
        if (prev == null) {
            LOG.info("[isAfterTypeInSchemaBody] prev is null -> false");
            return false;
        }

        IElementType prevType = prev.getNode().getElementType();

        // Check if previous token is a type keyword (like 'any')
        if (prevType == KiteTokenTypes.ANY) {
            LOG.info("[isAfterTypeInSchemaBody] prev is ANY -> true");
            return true;
        }

        // Check for ARRAY_LITERAL element (contains [] brackets as children)
        // PSI structure: IDENTIFIER(string) -> ARRAY_LITERAL([]) -> IDENTIFIER(tags)
        if (prevType == KiteElementTypes.ARRAY_LITERAL) {
            LOG.info("[isAfterTypeInSchemaBody] prev is ARRAY_LITERAL, checking for type before it");
            // The type name comes before the ARRAY_LITERAL
            PsiElement beforeArray = skipWhitespaceBackward(prev.getPrevSibling());
            LOG.info("[isAfterTypeInSchemaBody] beforeArray=" +
                     (beforeArray != null ? "'" + beforeArray.getText() + "' (" + beforeArray.getNode().getElementType() + ")" : "null"));
            if (beforeArray != null && beforeArray.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                // This is the pattern: type[] followed by our identifier
                LOG.info("[isAfterTypeInSchemaBody] Found array pattern: " + beforeArray.getText() + "[] -> true");
                return true;
            }
        }

        // Check for array suffix with RBRACK: type[]
        // (keeping for backwards compatibility with different PSI structures)
        if (prevType == KiteTokenTypes.RBRACK) {
            LOG.info("[isAfterTypeInSchemaBody] prev is RBRACK, checking for array pattern");
            // Walk back to find: type [ ]
            PsiElement beforeBrack = skipWhitespaceBackward(prev.getPrevSibling());
            LOG.info("[isAfterTypeInSchemaBody] beforeBrack=" +
                     (beforeBrack != null ? "'" + beforeBrack.getText() + "' (" + beforeBrack.getNode().getElementType() + ")" : "null"));
            if (beforeBrack != null && beforeBrack.getNode().getElementType() == KiteTokenTypes.LBRACK) {
                PsiElement beforeLBrack = skipWhitespaceBackward(beforeBrack.getPrevSibling());
                LOG.info("[isAfterTypeInSchemaBody] beforeLBrack=" +
                         (beforeLBrack != null ? "'" + beforeLBrack.getText() + "' (" + beforeLBrack.getNode().getElementType() + ")" : "null"));
                if (beforeLBrack != null && beforeLBrack.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                    // This is the pattern: identifier[] followed by our identifier
                    LOG.info("[isAfterTypeInSchemaBody] Found array pattern: " + beforeLBrack.getText() + "[] -> true");
                    return true;
                }
            }
        }

        // Check if previous is an identifier (could be a type name like "string", "number", "boolean", or custom type)
        if (prevType == KiteTokenTypes.IDENTIFIER) {
            String prevText = prev.getText();
            LOG.info("[isAfterTypeInSchemaBody] prev is IDENTIFIER: '" + prevText + "'");

            // If the previous identifier is a known primitive type name, it's definitely a type
            if (isPrimitiveTypeName(prevText)) {
                LOG.info("[isAfterTypeInSchemaBody] prev is primitive type -> true");
                return true;
            }

            // For other identifiers, check that this identifier isn't followed by = or { (which would make it a name, not a type)
            // In schema: "type name" vs "name ="
            // The previous identifier is a type if it comes after LBRACE, NL, or start of schema
            PsiElement beforePrev = skipWhitespaceBackward(prev.getPrevSibling());
            if (beforePrev != null) {
                IElementType beforePrevType = beforePrev.getNode().getElementType();
                LOG.info("[isAfterTypeInSchemaBody] beforePrev: '" + beforePrev.getText() + "' (" + beforePrevType + ")");
                // If previous identifier is after {, newline, or another property assignment (=, value, NL)
                // then it's likely a type
                if (beforePrevType == KiteTokenTypes.LBRACE ||
                    beforePrevType == KiteTokenTypes.NL ||
                    beforePrevType == KiteTokenTypes.NEWLINE ||
                    isValueToken(beforePrevType)) {
                    LOG.info("[isAfterTypeInSchemaBody] beforePrev is LBRACE/NL/value -> true");
                    return true;
                }
            }
        }

        LOG.info("[isAfterTypeInSchemaBody] No pattern matched -> false");
        return false;
    }

    /**
     * Check if the given text is a primitive type name.
     */
    private static boolean isPrimitiveTypeName(String text) {
        return "string".equals(text) ||
               "number".equals(text) ||
               "boolean".equals(text) ||
               "object".equals(text) ||
               "any".equals(text);
    }

    /**
     * Check if a token type represents a value (string, number, etc.)
     */
    private static boolean isValueToken(IElementType type) {
        return type == KiteTokenTypes.STRING ||
               type == KiteTokenTypes.SINGLE_STRING ||
               type == KiteTokenTypes.STRING_DQUOTE ||
               type == KiteTokenTypes.NUMBER ||
               type == KiteTokenTypes.TRUE ||
               type == KiteTokenTypes.FALSE ||
               type == KiteTokenTypes.NULL ||
               type == KiteTokenTypes.RBRACE ||
               type == KiteTokenTypes.RBRACK;
    }

    private static boolean isDeclarationType(IElementType type) {
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
    private static PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
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
    private static PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    @Nullable
    private static PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    private static boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }
}
