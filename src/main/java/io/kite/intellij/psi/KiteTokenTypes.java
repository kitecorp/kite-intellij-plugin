package io.kite.intellij.psi;

import com.intellij.psi.tree.IElementType;
import io.kite.intellij.KiteLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Token types for Kite language.
 */
public class KiteTokenTypes {

    public static class KiteTokenType extends IElementType {
        public KiteTokenType(@NotNull String debugName) {
            super(debugName, KiteLanguage.INSTANCE);
        }

        @Override
        public String toString() {
            return "KiteTokenType." + super.toString();
        }
    }

    // Keywords - IaC specific
    public static final IElementType RESOURCE = new KiteTokenType("RESOURCE");
    public static final IElementType COMPONENT = new KiteTokenType("COMPONENT");
    public static final IElementType SCHEMA = new KiteTokenType("SCHEMA");
    public static final IElementType INPUT = new KiteTokenType("INPUT");
    public static final IElementType OUTPUT = new KiteTokenType("OUTPUT");

    // Keywords - Control flow
    public static final IElementType IF = new KiteTokenType("IF");
    public static final IElementType ELSE = new KiteTokenType("ELSE");
    public static final IElementType WHILE = new KiteTokenType("WHILE");
    public static final IElementType FOR = new KiteTokenType("FOR");
    public static final IElementType IN = new KiteTokenType("IN");
    public static final IElementType RETURN = new KiteTokenType("RETURN");

    // Keywords - Declarations
    public static final IElementType IMPORT = new KiteTokenType("IMPORT");
    public static final IElementType FROM = new KiteTokenType("FROM");
    public static final IElementType FUN = new KiteTokenType("FUN");
    public static final IElementType VAR = new KiteTokenType("VAR");
    public static final IElementType TYPE = new KiteTokenType("TYPE");
    public static final IElementType INIT = new KiteTokenType("INIT");
    public static final IElementType THIS = new KiteTokenType("THIS");

    // Keywords - Types
    public static final IElementType OBJECT = new KiteTokenType("OBJECT");
    public static final IElementType ANY = new KiteTokenType("ANY");

    // Literals
    public static final IElementType TRUE = new KiteTokenType("TRUE");
    public static final IElementType FALSE = new KiteTokenType("FALSE");
    public static final IElementType NULL = new KiteTokenType("NULL");
    public static final IElementType NUMBER = new KiteTokenType("NUMBER");
    public static final IElementType STRING = new KiteTokenType("STRING");
    public static final IElementType IDENTIFIER = new KiteTokenType("IDENTIFIER");

    // Operators
    public static final IElementType AT = new KiteTokenType("AT");
    public static final IElementType DOT = new KiteTokenType("DOT");
    public static final IElementType ARROW = new KiteTokenType("ARROW");
    public static final IElementType RANGE = new KiteTokenType("RANGE");

    // Delimiters
    public static final IElementType COLON = new KiteTokenType("COLON");

    // Comments
    public static final IElementType LINE_COMMENT = new KiteTokenType("LINE_COMMENT");
    public static final IElementType BLOCK_COMMENT = new KiteTokenType("BLOCK_COMMENT");

    // Whitespace
    public static final IElementType WHITESPACE = new KiteTokenType("WHITESPACE");
    public static final IElementType NL = new KiteTokenType("NL");
    public static final IElementType NEWLINE = new KiteTokenType("NEWLINE");

    // Bad character
    public static final IElementType BAD_CHARACTER = new KiteTokenType("BAD_CHARACTER");
}
