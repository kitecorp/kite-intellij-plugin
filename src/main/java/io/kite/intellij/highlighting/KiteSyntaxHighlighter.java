package io.kite.intellij.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import io.kite.intellij.lexer.KiteLexerAdapter;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Syntax highlighter for Kite language.
 */
public class KiteSyntaxHighlighter extends SyntaxHighlighterBase {

    // Define text attribute keys for different token types
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("KITE_KEYWORD",
                    new TextAttributes(JBColor.namedColor("Kite.keyword", new Color(0xAB5FDB)),
                            null, null, null, Font.PLAIN));

    public static final TextAttributesKey STRING =
            createTextAttributesKey("KITE_STRING",
                    new TextAttributes(JBColor.namedColor("Kite.string", new Color(0x6A9955)),
                            null, null, null, Font.PLAIN));

    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("KITE_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

    public static final TextAttributesKey LINE_COMMENT =
            createTextAttributesKey("KITE_LINE_COMMENT",
                    new TextAttributes(JBColor.namedColor("Kite.lineComment", new Color(0x808080)),
                            null, null, null, Font.PLAIN));

    public static final TextAttributesKey BLOCK_COMMENT =
            createTextAttributesKey("KITE_BLOCK_COMMENT",
                    new TextAttributes(JBColor.namedColor("Kite.blockComment", new Color(0x808080)),
                            null, null, null, Font.PLAIN));

    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("KITE_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

    public static final TextAttributesKey DECORATOR =
            createTextAttributesKey("KITE_DECORATOR",
                    new TextAttributes(JBColor.namedColor("Kite.decorator", new Color(0xD97706)),
                            null, null, null, Font.PLAIN));

    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("KITE_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);

    // Interpolation highlighting
    public static final TextAttributesKey INTERPOLATION_DELIM =
            createTextAttributesKey("KITE_INTERPOLATION_DELIM",
                    new TextAttributes(JBColor.namedColor("Kite.interpolationDelim", new Color(0xD97706)),
                            null, null, null, Font.BOLD));

    public static final TextAttributesKey INTERPOLATION_VAR =
            createTextAttributesKey("KITE_INTERPOLATION_VAR", DefaultLanguageHighlighterColors.IDENTIFIER);

    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];
    private static final TextAttributesKey[] KEYWORD_KEYS = new TextAttributesKey[]{KEYWORD};
    private static final TextAttributesKey[] STRING_KEYS = new TextAttributesKey[]{STRING};
    private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};
    private static final TextAttributesKey[] LINE_COMMENT_KEYS = new TextAttributesKey[]{LINE_COMMENT};
    private static final TextAttributesKey[] BLOCK_COMMENT_KEYS = new TextAttributesKey[]{BLOCK_COMMENT};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};
    private static final TextAttributesKey[] DECORATOR_KEYS = new TextAttributesKey[]{DECORATOR};
    private static final TextAttributesKey[] OPERATOR_KEYS = new TextAttributesKey[]{OPERATOR};

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new KiteLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        // Keywords
        if (tokenType == KiteTokenTypes.RESOURCE || tokenType == KiteTokenTypes.COMPONENT ||
                tokenType == KiteTokenTypes.SCHEMA || tokenType == KiteTokenTypes.INPUT ||
                tokenType == KiteTokenTypes.OUTPUT || tokenType == KiteTokenTypes.IF ||
                tokenType == KiteTokenTypes.ELSE || tokenType == KiteTokenTypes.WHILE ||
                tokenType == KiteTokenTypes.FOR || tokenType == KiteTokenTypes.IN ||
                tokenType == KiteTokenTypes.RETURN || tokenType == KiteTokenTypes.IMPORT ||
                tokenType == KiteTokenTypes.FROM || tokenType == KiteTokenTypes.FUN ||
                tokenType == KiteTokenTypes.VAR || tokenType == KiteTokenTypes.TYPE ||
                tokenType == KiteTokenTypes.INIT || tokenType == KiteTokenTypes.THIS ||
                tokenType == KiteTokenTypes.OBJECT || tokenType == KiteTokenTypes.ANY ||
                tokenType == KiteTokenTypes.NULL) {
            return KEYWORD_KEYS;
        }

        // Boolean literals - same color as numbers
        if (tokenType == KiteTokenTypes.TRUE || tokenType == KiteTokenTypes.FALSE) {
            return NUMBER_KEYS;
        }

        // String
        if (tokenType == KiteTokenTypes.STRING) {
            return STRING_KEYS;
        }

        // Number
        if (tokenType == KiteTokenTypes.NUMBER) {
            return NUMBER_KEYS;
        }

        // Comments
        if (tokenType == KiteTokenTypes.LINE_COMMENT) {
            return LINE_COMMENT_KEYS;
        }
        if (tokenType == KiteTokenTypes.BLOCK_COMMENT) {
            return BLOCK_COMMENT_KEYS;
        }

        // Decorator (@)
        if (tokenType == KiteTokenTypes.AT) {
            return DECORATOR_KEYS;
        }

        // Operators
        if (tokenType == KiteTokenTypes.DOT || tokenType == KiteTokenTypes.ARROW ||
                tokenType == KiteTokenTypes.RANGE) {
            return OPERATOR_KEYS;
        }

        // Identifier
        if (tokenType == KiteTokenTypes.IDENTIFIER) {
            return IDENTIFIER_KEYS;
        }

        return EMPTY_KEYS;
    }
}
