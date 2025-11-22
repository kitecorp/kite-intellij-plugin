package io.kite.intellij.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.lexer.KiteLexerAdapter;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteFile;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Parser definition for Kite language.
 * This integrates the lexer and parser with IntelliJ's PSI system.
 */
public class KiteParserDefinition implements ParserDefinition {

    public static final IFileElementType FILE = new IFileElementType(KiteLanguage.INSTANCE);

    public static final TokenSet WHITESPACE = TokenSet.create(
            KiteTokenTypes.WHITESPACE,
            KiteTokenTypes.NEWLINE
    );

    public static final TokenSet COMMENTS = TokenSet.create(
            KiteTokenTypes.LINE_COMMENT,
            KiteTokenTypes.BLOCK_COMMENT
    );

    public static final TokenSet STRINGS = TokenSet.create(
            KiteTokenTypes.STRING
    );

    public static final TokenSet KEYWORDS = TokenSet.create(
            KiteTokenTypes.RESOURCE,
            KiteTokenTypes.COMPONENT,
            KiteTokenTypes.SCHEMA,
            KiteTokenTypes.INPUT,
            KiteTokenTypes.OUTPUT,
            KiteTokenTypes.IF,
            KiteTokenTypes.ELSE,
            KiteTokenTypes.WHILE,
            KiteTokenTypes.FOR,
            KiteTokenTypes.IN,
            KiteTokenTypes.RETURN,
            KiteTokenTypes.IMPORT,
            KiteTokenTypes.FROM,
            KiteTokenTypes.FUN,
            KiteTokenTypes.VAR,
            KiteTokenTypes.TYPE,
            KiteTokenTypes.INIT,
            KiteTokenTypes.THIS,
            KiteTokenTypes.OBJECT,
            KiteTokenTypes.ANY,
            KiteTokenTypes.TRUE,
            KiteTokenTypes.FALSE,
            KiteTokenTypes.NULL
    );

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new KiteLexerAdapter();
    }

    @NotNull
    @Override
    public PsiParser createParser(Project project) {
        return new KitePsiParser();
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return WHITESPACE;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        // For now, return a simple wrapper. We can enhance this later.
        return new KitePsiElement(node);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new KiteFile(viewProvider);
    }
}
