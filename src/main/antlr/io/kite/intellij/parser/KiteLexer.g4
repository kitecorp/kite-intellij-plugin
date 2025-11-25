lexer grammar KiteLexer;

// ============================================================================
// DEFAULT MODE TOKENS
// ============================================================================

// Keywords - IaC specific
RESOURCE    : 'resource' ;
COMPONENT   : 'component' ;
SCHEMA      : 'schema' ;
INPUT       : 'input' ;
OUTPUT      : 'output' ;

// Keywords - Control flow
IF          : 'if' ;
ELSE        : 'else' ;
WHILE       : 'while' ;
FOR         : 'for' ;
IN          : 'in' ;
RETURN      : 'return' ;

// Keywords - Declarations
IMPORT      : 'import' ;
FROM        : 'from' ;
FUN         : 'fun' ;
VAR         : 'var' ;
TYPE        : 'type' ;
INIT        : 'init' ;
THIS        : 'this' ;

// Keywords - Types
OBJECT      : 'object' ;
ANY         : 'any' ;

// Literals
TRUE        : 'true' ;
FALSE       : 'false' ;
NULL        : 'null' ;

// Operators - Arithmetic
PLUS        : '+' ;
MINUS       : '-' ;
MULTIPLY    : '*' ;
DIVIDE      : '/' ;
MODULO      : '%' ;
INCREMENT   : '++' ;
DECREMENT   : '--' ;

// Operators - Relational
LT          : '<' ;
GT          : '>' ;
LE          : '<=' ;
GE          : '>=' ;
EQ          : '==' ;
NE          : '!=' ;

// Operators - Logical
AND         : '&&' ;
OR          : '||' ;
NOT         : '!' ;

// Operators - Assignment
ASSIGN      : '=' ;
PLUS_ASSIGN : '+=' ;
MINUS_ASSIGN: '-=' ;
MUL_ASSIGN  : '*=' ;
DIV_ASSIGN  : '/=' ;

// Other operators
ARROW       : '->' ;
RANGE       : '..' ;
DOT         : '.' ;
AT          : '@' ;
UNION       : '|' ;

// Delimiters
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACE      : '{' ;
RBRACE      : '}' ;
LBRACK      : '[' ;
RBRACK      : ']' ;
COMMA       : ',' ;
COLON       : ':' ;
SEMICOLON   : ';' ;

// Literals
NUMBER
    : [0-9]+ ('.' [0-9]+)?
    ;

// Simple strings (single quotes - no interpolation)
SIMPLE_STRING
    : '\'' SingleStringCharacter* '\''
    ;

fragment
SingleStringCharacter
    : ~['\\\r\n]
    | '\\' .
    ;

// Double-quoted strings with interpolation support - use lexer modes
DQUOTE_OPEN : '"' -> pushMode(DSTRING_MODE);

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

// Whitespace and Comments
WS
    : [ \t\r]+ -> skip
    ;

NL
    : '\n'
    ;

LINE_COMMENT
    : '//' ~[\r\n]*
    ;

BLOCK_COMMENT
    : '/*' .*? '*/'
    ;

// ============================================================================
// LEXER MODE: Double-quoted string with interpolation
// ============================================================================
mode DSTRING_MODE;

// Text content (anything except ", $, \, or newline)
DSTRING_TEXT : (~["$\\\r\n])+ ;

// Escape sequences
DSTRING_ESCAPE : '\\' . ;

// Start of brace interpolation ${
INTERP_OPEN : '${' -> pushMode(INTERP_MODE) ;

// Simple interpolation $identifier (no braces)
INTERP_SIMPLE : '$' [a-zA-Z_][a-zA-Z0-9_]* ;

// Lone $ that's not followed by { or identifier - treat as text
DOLLAR_LITERAL : '$' ;

// Closing quote
DQUOTE_CLOSE : '"' -> popMode ;

// ============================================================================
// LEXER MODE: Inside ${...} interpolation
// ============================================================================
mode INTERP_MODE;

// Whitespace inside interpolation
INTERP_WS : [ \t\r\n]+ -> skip ;

// Identifier inside interpolation
INTERP_IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;

// Dot for member access inside interpolation
INTERP_DOT : '.' ;

// Opening bracket for array access
INTERP_LBRACK : '[' ;

// Closing bracket
INTERP_RBRACK : ']' ;

// Opening paren for function calls
INTERP_LPAREN : '(' ;

// Closing paren
INTERP_RPAREN : ')' ;

// Comma
INTERP_COMMA : ',' ;

// Numbers inside interpolation
INTERP_NUMBER : [0-9]+ ('.' [0-9]+)? ;

// Nested string inside interpolation
INTERP_STRING : '\'' (~['\\\r\n] | '\\' .)* '\'' ;

// End of interpolation
INTERP_CLOSE : '}' -> popMode ;
