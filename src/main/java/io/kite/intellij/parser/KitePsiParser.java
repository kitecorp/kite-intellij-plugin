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
            } else {
                // Unknown token, just advance
                builder.advanceLexer();
            }
        }
    }

    private void parseDeclaration(PsiBuilder builder, IElementType elementType) {
        PsiBuilder.Marker marker = builder.mark();

        // Consume tokens until we find the end of this declaration
        // For simplicity, we'll consume until we find a block end or statement separator
        int braceDepth = 0;
        boolean foundBlock = false;

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == KiteTokenTypes.LBRACE) {
                braceDepth++;
                foundBlock = true;
                builder.advanceLexer();
            } else if (tokenType == KiteTokenTypes.RBRACE) {
                braceDepth--;
                builder.advanceLexer();
                if (braceDepth == 0 && foundBlock) {
                    // End of declaration block
                    break;
                }
            } else if (braceDepth == 0 && (tokenType == KiteTokenTypes.NEWLINE || tokenType == KiteTokenTypes.SEMICOLON)) {
                // End of single-line declaration (like type or import)
                builder.advanceLexer();
                break;
            } else {
                builder.advanceLexer();
            }
        }

        marker.done(elementType);
    }

    private boolean isSkippableToken(IElementType tokenType) {
        return tokenType == KiteTokenTypes.NEWLINE ||
               tokenType == KiteTokenTypes.WHITESPACE ||
               tokenType == KiteTokenTypes.LINE_COMMENT ||
               tokenType == KiteTokenTypes.BLOCK_COMMENT ||
               tokenType == KiteTokenTypes.AT;  // Decorator prefix
    }
}
