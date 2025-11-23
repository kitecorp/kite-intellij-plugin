package io.kite.intellij.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
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
            } else if (tokenType == KiteTokenTypes.IMPORT) {
                parseDeclaration(builder, KiteElementTypes.IMPORT_STATEMENT);
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
        int braceDepth = 0;  // Track nested braces for object/array literals

        while (!builder.eof()) {
            // Skip whitespace and comments
            while (!builder.eof() && isSkippableToken(builder.getTokenType())) {
                builder.advanceLexer();
            }

            if (builder.eof()) break;

            IElementType tokenType = builder.getTokenType();

            // Check for closing brace - but only exit if we're at depth 0
            if (tokenType == KiteTokenTypes.RBRACE) {
                if (braceDepth == 0) {
                    // This is the closing brace of the block itself
                    builder.advanceLexer();
                    break;
                } else {
                    // This closes a nested object/array literal
                    braceDepth--;
                    builder.advanceLexer();
                }
            } else if (tokenType == KiteTokenTypes.LBRACE) {
                // Opening brace of a nested object/array literal
                braceDepth++;
                builder.advanceLexer();
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
}
