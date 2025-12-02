package cloud.kitelang.intellij.psi;

import cloud.kitelang.intellij.KiteLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Token types for Kite language.
 */
public class KiteTokenTypes {

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
    public static final IElementType STRING = new KiteTokenType("STRING");  // Legacy - kept for compatibility
    public static final IElementType SINGLE_STRING = new KiteTokenType("SINGLE_STRING");  // Single-quoted strings 'text'
    public static final IElementType IDENTIFIER = new KiteTokenType("IDENTIFIER");
    // String interpolation tokens (double-quoted strings with ${} syntax)
    // These match the split grammar (KiteLexer.g4) with lexer modes
    public static final IElementType DQUOTE = new KiteTokenType("DQUOTE");             // Opening " (enters STRING_MODE)
    public static final IElementType STRING_DQUOTE = new KiteTokenType("STRING_DQUOTE"); // Closing " (exits STRING_MODE)
    public static final IElementType STRING_TEXT = new KiteTokenType("STRING_TEXT");     // Text content inside "..."
    public static final IElementType STRING_ESCAPE = new KiteTokenType("STRING_ESCAPE"); // Escape sequences like \n
    public static final IElementType INTERP_START = new KiteTokenType("INTERP_START");   // ${ (pushes DEFAULT_MODE)
    public static final IElementType INTERP_END = new KiteTokenType("INTERP_END");       // } closing interpolation (pops mode)
    public static final IElementType STRING_DOLLAR = new KiteTokenType("STRING_DOLLAR"); // Lone $ not followed by {
    // Legacy tokens for backwards compatibility (can be removed after migration)
    public static final IElementType DQUOTE_OPEN = DQUOTE;       // Alias
    public static final IElementType DQUOTE_CLOSE = STRING_DQUOTE; // Alias
    public static final IElementType DSTRING_TEXT = STRING_TEXT;   // Alias
    public static final IElementType DSTRING_ESCAPE = STRING_ESCAPE; // Alias
    public static final IElementType INTERP_OPEN = INTERP_START;   // Alias
    public static final IElementType DOLLAR_LITERAL = STRING_DOLLAR; // Alias
    // Note: With split grammar and lexer modes, inside ${...} we get regular
    // DEFAULT_MODE tokens (IDENTIFIER, DOT, LBRACK, etc.) - no special INTERP_* tokens needed.
    // The following are kept for reference but may not be used:
    public static final IElementType INTERP_IDENTIFIER = new KiteTokenType("INTERP_IDENTIFIER");
    public static final IElementType INTERP_SIMPLE = new KiteTokenType("INTERP_SIMPLE");
    public static final IElementType INTERP_DOT = new KiteTokenType("INTERP_DOT");
    public static final IElementType INTERP_LBRACK = new KiteTokenType("INTERP_LBRACK");
    public static final IElementType INTERP_RBRACK = new KiteTokenType("INTERP_RBRACK");
    public static final IElementType INTERP_LPAREN = new KiteTokenType("INTERP_LPAREN");
    public static final IElementType INTERP_RPAREN = new KiteTokenType("INTERP_RPAREN");
    public static final IElementType INTERP_COMMA = new KiteTokenType("INTERP_COMMA");
    public static final IElementType INTERP_NUMBER = new KiteTokenType("INTERP_NUMBER");
    public static final IElementType INTERP_STRING = new KiteTokenType("INTERP_STRING");
    public static final IElementType INTERP_CLOSE = new KiteTokenType("INTERP_CLOSE");
    // Operators - Arithmetic
    public static final IElementType PLUS = new KiteTokenType("PLUS");
    public static final IElementType MINUS = new KiteTokenType("MINUS");
    public static final IElementType MULTIPLY = new KiteTokenType("MULTIPLY");
    public static final IElementType DIVIDE = new KiteTokenType("DIVIDE");
    public static final IElementType MODULO = new KiteTokenType("MODULO");
    public static final IElementType INCREMENT = new KiteTokenType("INCREMENT");
    public static final IElementType DECREMENT = new KiteTokenType("DECREMENT");
    // Operators - Relational
    public static final IElementType LT = new KiteTokenType("LT");
    public static final IElementType GT = new KiteTokenType("GT");
    public static final IElementType LE = new KiteTokenType("LE");
    public static final IElementType GE = new KiteTokenType("GE");
    public static final IElementType EQ = new KiteTokenType("EQ");
    public static final IElementType NE = new KiteTokenType("NE");
    // Operators - Logical
    public static final IElementType AND = new KiteTokenType("AND");
    public static final IElementType OR = new KiteTokenType("OR");
    public static final IElementType NOT = new KiteTokenType("NOT");
    // Operators - Assignment
    public static final IElementType ASSIGN = new KiteTokenType("ASSIGN");
    public static final IElementType PLUS_ASSIGN = new KiteTokenType("PLUS_ASSIGN");
    public static final IElementType MINUS_ASSIGN = new KiteTokenType("MINUS_ASSIGN");
    public static final IElementType MUL_ASSIGN = new KiteTokenType("MUL_ASSIGN");
    public static final IElementType DIV_ASSIGN = new KiteTokenType("DIV_ASSIGN");
    // Other operators
    public static final IElementType AT = new KiteTokenType("AT");
    public static final IElementType DOT = new KiteTokenType("DOT");
    public static final IElementType ARROW = new KiteTokenType("ARROW");
    public static final IElementType RANGE = new KiteTokenType("RANGE");
    public static final IElementType UNION = new KiteTokenType("UNION");
    // Delimiters
    public static final IElementType LPAREN = new KiteTokenType("LPAREN");
    public static final IElementType RPAREN = new KiteTokenType("RPAREN");
    public static final IElementType LBRACE = new KiteTokenType("LBRACE");
    public static final IElementType RBRACE = new KiteTokenType("RBRACE");
    public static final IElementType LBRACK = new KiteTokenType("LBRACK");
    public static final IElementType RBRACK = new KiteTokenType("RBRACK");
    public static final IElementType COMMA = new KiteTokenType("COMMA");
    public static final IElementType COLON = new KiteTokenType("COLON");
    public static final IElementType SEMICOLON = new KiteTokenType("SEMICOLON");
    // Comments
    public static final IElementType LINE_COMMENT = new KiteTokenType("LINE_COMMENT");
    public static final IElementType BLOCK_COMMENT = new KiteTokenType("BLOCK_COMMENT");
    // Whitespace
    public static final IElementType WHITESPACE = new KiteTokenType("WHITESPACE");
    public static final IElementType NL = new KiteTokenType("NL");
    public static final IElementType NEWLINE = new KiteTokenType("NEWLINE");
    // Bad character
    public static final IElementType BAD_CHARACTER = new KiteTokenType("BAD_CHARACTER");

    public static class KiteTokenType extends IElementType {
        public KiteTokenType(@NotNull String debugName) {
            super(debugName, KiteLanguage.INSTANCE);
        }

        @Override
        public String toString() {
            return "KiteTokenType." + super.toString();
        }
    }
}
