package io.kite.intellij.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.parser.KiteLexer;
import io.kite.intellij.psi.KiteTokenTypes;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter to use ANTLR-generated KiteLexer with IntelliJ Platform.
 * This lexer ensures all characters are covered by tokens (no gaps).
 *
 * State encoding for incremental lexing:
 * - Bits 0-7: current mode (0=DEFAULT_MODE, 1=STRING_MODE)
 * - Bits 8-15: interpolation depth (0-255)
 *
 * This allows IntelliJ to properly resume lexing from any point in the file,
 * especially important for string interpolation where the lexer needs to know
 * whether it's inside a string and how deeply nested in interpolations.
 */
public class KiteLexerAdapter extends LexerBase {
    private static final int MODE_MASK = 0xFF;
    private static final int DEPTH_SHIFT = 8;
    private static final int DEPTH_MASK = 0xFF;

    // Constants for modes (matching ANTLR generated mode numbers)
    private static final int DEFAULT_MODE = 0;
    private static final int STRING_MODE = 1;

    private CharSequence buffer;
    private int startOffset;
    private int endOffset;
    private List<TokenInfo> tokens;
    private int currentTokenIndex;

    private static class TokenInfo {
        final IElementType type;
        final int start;
        final int end;
        final int state;  // Lexer state at the END of this token

        TokenInfo(IElementType type, int start, int end, int state) {
            this.type = type;
            this.start = start;
            this.end = end;
            this.state = state;
        }
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        this.tokens = new ArrayList<>();
        this.currentTokenIndex = 0;

        // CRITICAL FIX: Always lex from the beginning of the buffer.
        //
        // ANTLR lexers with modes (STRING_MODE for double-quoted strings) cannot correctly
        // resume from arbitrary positions. When IntelliJ asks us to lex from offset N with
        // state S, the character at offset N might be ambiguous (e.g., a `"` could be either
        // opening or closing a string depending on context).
        //
        // For example, in `@allowed(["dev", "prod"])`, if IntelliJ asks us to start at the
        // closing `"` of "dev" with state 0 (DEFAULT_MODE), a new ANTLR lexer will interpret
        // that `"` as DQUOTE (opening a string) instead of STRING_DQUOTE (closing a string).
        // This cascades through the file, causing incorrect tokenization.
        //
        // The safe solution is to ALWAYS lex from offset 0. We then skip tokens that end
        // before the requested startOffset.
        this.startOffset = 0;  // Always start from beginning
        int requestedStartOffset = startOffset;  // Remember where IntelliJ wanted us to start

        // Get the FULL text to lex (from 0 to endOffset)
        String text = buffer.subSequence(0, endOffset).toString();
        KiteLexer lexer = new KiteLexer(CharStreams.fromString(text));

        // Replace default error listener with a silent one to avoid console spam
        // We handle unrecognized characters as BAD_CHARACTER tokens anyway
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                // Silent - errors are handled as BAD_CHARACTER tokens
            }
        });

        // No state restoration needed since we always start from 0

        int currentPos = 0;
        Token token;

        // Track current state after each token (starting from default)
        int currentMode = DEFAULT_MODE;
        int currentDepth = 0;

        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            int tokenStart = token.getStartIndex();
            int tokenEnd = token.getStopIndex() + 1;
            int antlrType = token.getType();

            // Fill gap with whitespace if there's a gap
            if (tokenStart > currentPos) {
                int gapState = encodeState(currentMode, currentDepth);
                tokens.add(new TokenInfo(
                    KiteTokenTypes.WHITESPACE,
                    currentPos,
                    tokenStart,
                    gapState
                ));
            }

            // Update mode/depth tracking based on token type
            // This mirrors the logic in the lexer grammar
            if (antlrType == KiteLexer.DQUOTE) {
                // Entering string mode
                currentMode = STRING_MODE;
            } else if (antlrType == KiteLexer.STRING_DQUOTE) {
                // Exiting string mode
                currentMode = DEFAULT_MODE;
            } else if (antlrType == KiteLexer.INTERP_START) {
                // Starting interpolation: push to default mode, increment depth
                currentMode = DEFAULT_MODE;
                currentDepth++;
            } else if (antlrType == KiteLexer.INTERP_END) {
                // Ending interpolation: pop back to string mode, decrement depth
                currentMode = STRING_MODE;
                if (currentDepth > 0) currentDepth--;
            }

            int tokenState = encodeState(currentMode, currentDepth);

            // Add the actual token
            tokens.add(new TokenInfo(
                convertTokenType(antlrType),
                tokenStart,
                tokenEnd,
                tokenState
            ));

            currentPos = tokenEnd;
        }

        // Fill any remaining gap at the end
        if (currentPos < text.length()) {
            int finalState = encodeState(currentMode, currentDepth);
            tokens.add(new TokenInfo(
                KiteTokenTypes.WHITESPACE,
                currentPos,
                text.length(),
                finalState
            ));
        }

        // Skip tokens that end before the requested startOffset
        // This is an optimization - IntelliJ asked to start from requestedStartOffset,
        // so it doesn't need tokens before that position
        while (currentTokenIndex < tokens.size() &&
               tokens.get(currentTokenIndex).end <= requestedStartOffset) {
            currentTokenIndex++;
        }
    }

    private int encodeState(int mode, int depth) {
        return (mode & MODE_MASK) | ((depth & DEPTH_MASK) << DEPTH_SHIFT);
    }

    @Override
    public int getState() {
        // Return the state at the END of the current token
        // This is what IntelliJ will pass to start() when resuming from this position
        if (currentTokenIndex >= tokens.size()) {
            return 0;
        }
        return tokens.get(currentTokenIndex).state;
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
            case KiteLexer.SINGLE_STRING -> KiteTokenTypes.SINGLE_STRING;
            case KiteLexer.IDENTIFIER -> KiteTokenTypes.IDENTIFIER;

            // String interpolation tokens (double-quoted strings with lexer modes)
            // From DEFAULT_MODE:
            case KiteLexer.DQUOTE -> KiteTokenTypes.DQUOTE;           // Opening " (enters STRING_MODE)
            // From STRING_MODE:
            case KiteLexer.STRING_DQUOTE -> KiteTokenTypes.STRING_DQUOTE; // Closing " (exits STRING_MODE)
            case KiteLexer.STRING_TEXT -> KiteTokenTypes.STRING_TEXT;     // Text content
            case KiteLexer.STRING_ESCAPE -> KiteTokenTypes.STRING_ESCAPE; // Escape sequences
            case KiteLexer.INTERP_START -> KiteTokenTypes.INTERP_START;   // ${ (pushes DEFAULT_MODE)
            case KiteLexer.INTERP_END -> KiteTokenTypes.INTERP_END;       // } closing interpolation
            case KiteLexer.STRING_DOLLAR -> KiteTokenTypes.STRING_DOLLAR; // Lone $ not followed by {
            case KiteLexer.INTERP_SIMPLE -> KiteTokenTypes.INTERP_SIMPLE;   // $identifier (simple interpolation)
            // Note: Inside ${...}, we get regular DEFAULT_MODE tokens (IDENTIFIER, DOT, etc.)
            // The closing } is just RBRACE (already mapped above)

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
