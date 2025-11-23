package io.kite.intellij.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.parser.KiteLexer;
import io.kite.intellij.psi.KiteTokenTypes;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter to use ANTLR-generated KiteLexer with IntelliJ Platform.
 * This lexer ensures all characters are covered by tokens (no gaps).
 */
public class KiteLexerAdapter extends LexerBase {
    private CharSequence buffer;
    private int startOffset;
    private int endOffset;
    private List<TokenInfo> tokens;
    private int currentTokenIndex;

    private static class TokenInfo {
        final IElementType type;
        final int start;
        final int end;

        TokenInfo(IElementType type, int start, int end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.tokens = new ArrayList<>();
        this.currentTokenIndex = 0;

        // Lex the text and fill in gaps
        String text = buffer.subSequence(startOffset, endOffset).toString();
        KiteLexer lexer = new KiteLexer(CharStreams.fromString(text));

        int currentPos = 0;
        Token token;

        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            int tokenStart = token.getStartIndex();
            int tokenEnd = token.getStopIndex() + 1;

            // Fill gap with whitespace if there's a gap
            if (tokenStart > currentPos) {
                tokens.add(new TokenInfo(
                    KiteTokenTypes.WHITESPACE,
                    currentPos,
                    tokenStart
                ));
            }

            // Add the actual token
            tokens.add(new TokenInfo(
                convertTokenType(token.getType()),
                tokenStart,
                tokenEnd
            ));

            currentPos = tokenEnd;
        }

        // Fill any remaining gap at the end
        if (currentPos < text.length()) {
            tokens.add(new TokenInfo(
                KiteTokenTypes.WHITESPACE,
                currentPos,
                text.length()
            ));
        }
    }

    @Override
    public int getState() {
        return 0;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        if (currentTokenIndex >= tokens.size()) {
            return null;
        }
        return tokens.get(currentTokenIndex).type;
    }

    @Override
    public int getTokenStart() {
        if (currentTokenIndex >= tokens.size()) {
            return endOffset;
        }
        return startOffset + tokens.get(currentTokenIndex).start;
    }

    @Override
    public int getTokenEnd() {
        if (currentTokenIndex >= tokens.size()) {
            return endOffset;
        }
        return startOffset + tokens.get(currentTokenIndex).end;
    }

    @Override
    public void advance() {
        currentTokenIndex++;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private IElementType convertTokenType(int antlrTokenType) {
        return switch (antlrTokenType) {
            // Keywords - IaC
            case KiteLexer.RESOURCE -> KiteTokenTypes.RESOURCE;
            case KiteLexer.COMPONENT -> KiteTokenTypes.COMPONENT;
            case KiteLexer.SCHEMA -> KiteTokenTypes.SCHEMA;
            case KiteLexer.INPUT -> KiteTokenTypes.INPUT;
            case KiteLexer.OUTPUT -> KiteTokenTypes.OUTPUT;

            // Keywords - Control flow
            case KiteLexer.IF -> KiteTokenTypes.IF;
            case KiteLexer.ELSE -> KiteTokenTypes.ELSE;
            case KiteLexer.WHILE -> KiteTokenTypes.WHILE;
            case KiteLexer.FOR -> KiteTokenTypes.FOR;
            case KiteLexer.IN -> KiteTokenTypes.IN;
            case KiteLexer.RETURN -> KiteTokenTypes.RETURN;

            // Keywords - Declarations
            case KiteLexer.IMPORT -> KiteTokenTypes.IMPORT;
            case KiteLexer.FROM -> KiteTokenTypes.FROM;
            case KiteLexer.FUN -> KiteTokenTypes.FUN;
            case KiteLexer.VAR -> KiteTokenTypes.VAR;
            case KiteLexer.TYPE -> KiteTokenTypes.TYPE;
            case KiteLexer.INIT -> KiteTokenTypes.INIT;
            case KiteLexer.THIS -> KiteTokenTypes.THIS;

            // Keywords - Types
            case KiteLexer.OBJECT -> KiteTokenTypes.OBJECT;
            case KiteLexer.ANY -> KiteTokenTypes.ANY;

            // Literals
            case KiteLexer.TRUE -> KiteTokenTypes.TRUE;
            case KiteLexer.FALSE -> KiteTokenTypes.FALSE;
            case KiteLexer.NULL -> KiteTokenTypes.NULL;
            case KiteLexer.NUMBER -> KiteTokenTypes.NUMBER;
            case KiteLexer.STRING -> KiteTokenTypes.STRING;
            case KiteLexer.IDENTIFIER -> KiteTokenTypes.IDENTIFIER;

            // Operators - Arithmetic
            case KiteLexer.PLUS -> KiteTokenTypes.PLUS;
            case KiteLexer.MINUS -> KiteTokenTypes.MINUS;
            case KiteLexer.MULTIPLY -> KiteTokenTypes.MULTIPLY;
            case KiteLexer.DIVIDE -> KiteTokenTypes.DIVIDE;
            case KiteLexer.MODULO -> KiteTokenTypes.MODULO;
            case KiteLexer.INCREMENT -> KiteTokenTypes.INCREMENT;
            case KiteLexer.DECREMENT -> KiteTokenTypes.DECREMENT;

            // Operators - Relational
            case KiteLexer.LT -> KiteTokenTypes.LT;
            case KiteLexer.GT -> KiteTokenTypes.GT;
            case KiteLexer.LE -> KiteTokenTypes.LE;
            case KiteLexer.GE -> KiteTokenTypes.GE;
            case KiteLexer.EQ -> KiteTokenTypes.EQ;
            case KiteLexer.NE -> KiteTokenTypes.NE;

            // Operators - Logical
            case KiteLexer.AND -> KiteTokenTypes.AND;
            case KiteLexer.OR -> KiteTokenTypes.OR;
            case KiteLexer.NOT -> KiteTokenTypes.NOT;

            // Operators - Assignment
            case KiteLexer.ASSIGN -> KiteTokenTypes.ASSIGN;
            case KiteLexer.PLUS_ASSIGN -> KiteTokenTypes.PLUS_ASSIGN;
            case KiteLexer.MINUS_ASSIGN -> KiteTokenTypes.MINUS_ASSIGN;
            case KiteLexer.MUL_ASSIGN -> KiteTokenTypes.MUL_ASSIGN;
            case KiteLexer.DIV_ASSIGN -> KiteTokenTypes.DIV_ASSIGN;

            // Other operators
            case KiteLexer.AT -> KiteTokenTypes.AT;
            case KiteLexer.DOT -> KiteTokenTypes.DOT;
            case KiteLexer.ARROW -> KiteTokenTypes.ARROW;
            case KiteLexer.RANGE -> KiteTokenTypes.RANGE;
            case KiteLexer.UNION -> KiteTokenTypes.UNION;

            // Delimiters
            case KiteLexer.LPAREN -> KiteTokenTypes.LPAREN;
            case KiteLexer.RPAREN -> KiteTokenTypes.RPAREN;
            case KiteLexer.LBRACE -> KiteTokenTypes.LBRACE;
            case KiteLexer.RBRACE -> KiteTokenTypes.RBRACE;
            case KiteLexer.LBRACK -> KiteTokenTypes.LBRACK;
            case KiteLexer.RBRACK -> KiteTokenTypes.RBRACK;
            case KiteLexer.COMMA -> KiteTokenTypes.COMMA;
            case KiteLexer.COLON -> KiteTokenTypes.COLON;
            case KiteLexer.SEMICOLON -> KiteTokenTypes.SEMICOLON;

            // Comments
            case KiteLexer.LINE_COMMENT -> KiteTokenTypes.LINE_COMMENT;
            case KiteLexer.BLOCK_COMMENT -> KiteTokenTypes.BLOCK_COMMENT;

            // Whitespace
            case KiteLexer.WS -> KiteTokenTypes.WHITESPACE;
            case KiteLexer.NL -> KiteTokenTypes.NL;

            default -> KiteTokenTypes.BAD_CHARACTER;
        };
    }
}
