package cloud.kitelang.intellij.parser;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * PSI parser for Kite that creates typed elements for declarations.
 * Identifies resource, component, schema, function, type, and variable declarations.
 */
public class KitePsiParser implements PsiParser {

    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker rootMarker = builder.mark();

        // Parse the file content
        parseStatements(builder);

        rootMarker.done(root);
        return builder.getTreeBuilt();
    }

    private void parseStatements(PsiBuilder builder) {
        while (!builder.eof()) {
            // Skip decorators, newlines, and comments
            while (!builder.eof() && isSkippableToken(builder.getTokenType())) {
                builder.advanceLexer();
            }

            if (builder.eof()) break;

            IElementType tokenType = builder.getTokenType();

            // Check for declaration keywords
            if (tokenType == KiteTokenTypes.RESOURCE) {
                parseDeclaration(builder, KiteElementTypes.RESOURCE_DECLARATION);
            } else if (tokenType == KiteTokenTypes.COMPONENT) {
                parseDeclaration(builder, KiteElementTypes.COMPONENT_DECLARATION);
            } else if (tokenType == KiteTokenTypes.SCHEMA) {
                parseDeclaration(builder, KiteElementTypes.SCHEMA_DECLARATION);
            } else if (tokenType == KiteTokenTypes.FUN) {
                parseDeclaration(builder, KiteElementTypes.FUNCTION_DECLARATION);
            } else if (tokenType == KiteTokenTypes.TYPE) {
                parseDeclaration(builder, KiteElementTypes.TYPE_DECLARATION);
            } else if (tokenType == KiteTokenTypes.VAR) {
                parseDeclaration(builder, KiteElementTypes.VARIABLE_DECLARATION);
            } else if (tokenType == KiteTokenTypes.INPUT) {
                parseDeclaration(builder, KiteElementTypes.INPUT_DECLARATION);
            } else if (tokenType == KiteTokenTypes.OUTPUT) {
                parseDeclaration(builder, KiteElementTypes.OUTPUT_DECLARATION);
            } else if (tokenType == KiteTokenTypes.IMPORT) {
                parseDeclaration(builder, KiteElementTypes.IMPORT_STATEMENT);
            } else if (tokenType == KiteTokenTypes.FOR) {
                parseDeclaration(builder, KiteElementTypes.FOR_STATEMENT);
            } else if (tokenType == KiteTokenTypes.WHILE) {
                parseDeclaration(builder, KiteElementTypes.WHILE_STATEMENT);
            } else if (tokenType == KiteTokenTypes.LBRACE) {
                // Parse object literal (e.g., in decorator arguments)
                parseObjectLiteral(builder);
            } else if (tokenType == KiteTokenTypes.LBRACK) {
                // Parse array literal
                parseArrayLiteral(builder);
            } else {
                // Unknown token, just advance
                builder.advanceLexer();
            }
        }
    }

    private void parseDeclaration(PsiBuilder builder, IElementType elementType) {
        PsiBuilder.Marker marker = builder.mark();

        // Check if this declaration can have nested content (component, schema, resource)
        boolean canHaveNestedDeclarations = elementType == KiteElementTypes.COMPONENT_DECLARATION ||
                                            elementType == KiteElementTypes.SCHEMA_DECLARATION ||
                                            elementType == KiteElementTypes.RESOURCE_DECLARATION;

        // INPUT and OUTPUT are always single-line declarations
        boolean isSingleLineDeclaration = elementType == KiteElementTypes.INPUT_DECLARATION ||
                                          elementType == KiteElementTypes.OUTPUT_DECLARATION;

        // Consume tokens until we find a block or end of line
        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == KiteTokenTypes.LBRACE) {
                // Found opening brace
                builder.advanceLexer();

                if (canHaveNestedDeclarations) {
                    // Parse nested declarations
                    parseBlockContent(builder);
                } else {
                    // Just consume until closing brace for functions, etc.
                    consumeUntilClosingBrace(builder);
                }
                break;
            } else if (tokenType == KiteTokenTypes.NL || tokenType == KiteTokenTypes.SEMICOLON) {
                // End of single-line declaration
                // Don't consume the newline for single-line declarations - let parseBlockContent handle it
                if (!isSingleLineDeclaration) {
                    builder.advanceLexer();
                }
                break;
            } else {
                builder.advanceLexer();
            }
        }

        marker.done(elementType);
    }

    private void parseBlockContent(PsiBuilder builder) {
        while (!builder.eof()) {
            // Skip whitespace and comments
            while (!builder.eof() && isSkippableToken(builder.getTokenType())) {
                builder.advanceLexer();
            }

            if (builder.eof()) break;

            IElementType tokenType = builder.getTokenType();

            // Check for closing brace of the block itself
            if (tokenType == KiteTokenTypes.RBRACE) {
                builder.advanceLexer();
                break;
            } else if (tokenType == KiteTokenTypes.LBRACE) {
                // Parse object literal
                parseObjectLiteral(builder);
            } else if (tokenType == KiteTokenTypes.LBRACK) {
                // Parse array literal
                parseArrayLiteral(builder);
            } else if (tokenType == KiteTokenTypes.RESOURCE) {
                parseDeclaration(builder, KiteElementTypes.RESOURCE_DECLARATION);
            } else if (tokenType == KiteTokenTypes.INPUT) {
                parseDeclaration(builder, KiteElementTypes.INPUT_DECLARATION);
            } else if (tokenType == KiteTokenTypes.OUTPUT) {
                parseDeclaration(builder, KiteElementTypes.OUTPUT_DECLARATION);
            } else if (tokenType == KiteTokenTypes.VAR) {
                parseDeclaration(builder, KiteElementTypes.VARIABLE_DECLARATION);
            } else if (tokenType == KiteTokenTypes.FUN) {
                parseDeclaration(builder, KiteElementTypes.FUNCTION_DECLARATION);
            } else if (tokenType == KiteTokenTypes.FOR) {
                parseDeclaration(builder, KiteElementTypes.FOR_STATEMENT);
            } else if (tokenType == KiteTokenTypes.WHILE) {
                parseDeclaration(builder, KiteElementTypes.WHILE_STATEMENT);
            } else {
                // Unknown token, just advance
                builder.advanceLexer();
            }
        }
    }

    private void consumeUntilClosingBrace(PsiBuilder builder) {
        int braceDepth = 1;  // We already consumed the opening brace

        while (!builder.eof() && braceDepth > 0) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (tokenType == KiteTokenTypes.RBRACE) {
                braceDepth--;
            }

            builder.advanceLexer();
        }
    }

    private boolean isSkippableToken(IElementType tokenType) {
        return tokenType == KiteTokenTypes.NL ||
               tokenType == KiteTokenTypes.WHITESPACE ||
               tokenType == KiteTokenTypes.LINE_COMMENT ||
               tokenType == KiteTokenTypes.BLOCK_COMMENT ||
               tokenType == KiteTokenTypes.AT;  // Decorator prefix
    }

    /**
     * Parses an object literal { ... }
     * Recursively creates nested OBJECT_LITERAL elements for nested braces.
     */
    private void parseObjectLiteral(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        // Consume opening brace
        builder.advanceLexer();

        // Consume until closing brace, recursively handling nested structures
        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == KiteTokenTypes.LBRACE) {
                // Recursively parse nested object literal
                parseObjectLiteral(builder);
            } else if (tokenType == KiteTokenTypes.LBRACK) {
                // Recursively parse nested array literal
                parseArrayLiteral(builder);
            } else if (tokenType == KiteTokenTypes.RBRACE) {
                // End of this object literal
                builder.advanceLexer();
                break;
            } else {
                builder.advanceLexer();
            }
        }

        marker.done(KiteElementTypes.OBJECT_LITERAL);
    }

    /**
     * Parses an array literal [ ... ]
     * Recursively creates nested ARRAY_LITERAL and OBJECT_LITERAL elements.
     */
    private void parseArrayLiteral(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        // Consume opening bracket
        builder.advanceLexer();

        // Consume until closing bracket, recursively handling nested structures
        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == KiteTokenTypes.LBRACE) {
                // Recursively parse nested object literal
                parseObjectLiteral(builder);
            } else if (tokenType == KiteTokenTypes.LBRACK) {
                // Recursively parse nested array literal
                parseArrayLiteral(builder);
            } else if (tokenType == KiteTokenTypes.RBRACK) {
                // End of this array literal
                builder.advanceLexer();
                break;
            } else {
                builder.advanceLexer();
            }
        }

        marker.done(KiteElementTypes.ARRAY_LITERAL);
    }
}
