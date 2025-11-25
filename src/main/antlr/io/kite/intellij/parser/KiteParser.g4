parser grammar KiteParser;

options { tokenVocab=KiteLexer; }

// ============================================================================
// PARSER RULES (lowercase)
// ============================================================================

// Entry point
program
    : statementTerminator* statementList? EOF
    ;

statementList
    : nonEmptyStatement (statementTerminator+ nonEmptyStatement)* statementTerminator*
    ;

statementTerminator
    : NL
    | SEMICOLON
    ;

nonEmptyStatement
    : importStatement
    | declaration
    | ifStatement
    | initStatement
    | returnStatement
    | iterationStatement
    | blockExpression
    | expressionStatement
    ;
statement
    : nonEmptyStatement
    | emptyStatement
    ;

emptyStatement
    : NL
    ;

// Import Statement
importStatement
    : IMPORT MULTIPLY FROM stringLiteral
    ;



// Declarations
declaration
    : decoratorList? NL* functionDeclaration
    | decoratorList? NL* typeDeclaration
    | decoratorList? NL* schemaDeclaration
    | decoratorList? NL* resourceDeclaration
    | decoratorList? NL* componentDeclaration
    | decoratorList? NL* inputDeclaration
    | decoratorList? NL* outputDeclaration
    | decoratorList? NL* varDeclaration
    ;

functionDeclaration
    : FUN identifier LPAREN parameterList? RPAREN typeIdentifier? blockExpression
    ;

typeDeclaration
    : TYPE identifier ASSIGN typeParams
    ;

typeParams
    : unionTypeParam (UNION unionTypeParam)*
    ;

unionTypeParam
    : literal
    | objectExpression
    | arrayExpression
    | typeKeyword
    | identifier
    ;

typeKeyword
    : OBJECT
    | ANY
    ;

schemaDeclaration
    : SCHEMA identifier LBRACE statementTerminator* schemaPropertyList? statementTerminator* RBRACE
    ;

schemaPropertyList
    : schemaProperty (statementTerminator+ schemaProperty)* statementTerminator*
    ;

schemaProperty
    : decoratorList? typeIdentifier identifier propertyInitializer?
    ;

propertyInitializer
    : ASSIGN expression
    ;

resourceDeclaration
    : RESOURCE typeIdentifier resourceName blockExpression
    ;

resourceName
    : identifier
    | callMemberExpression
    | stringLiteral
    ;

componentDeclaration
    : COMPONENT componentType identifier? blockExpression
    ;

componentType
    : typeIdentifier
    ;

inputDeclaration
    : INPUT typeIdentifier identifier (ASSIGN expression)?
    ;

outputDeclaration
    : OUTPUT typeIdentifier identifier (ASSIGN expression)?
    ;

varDeclaration
    : VAR varDeclarationList
    ;

varDeclarationList
    : varDeclarator (COMMA varDeclarator)*
    ;

varDeclarator
    : typeIdentifier? identifier varInitializer?
    ;

varInitializer
    : (ASSIGN | PLUS_ASSIGN) expression
    ;

// Statements
ifStatement
    : IF LPAREN NL* expression NL* RPAREN NL* blockExpression elseStatement?
    | IF expression NL* blockExpression elseStatement?
    ;

elseStatement
    : ELSE NL* blockExpression
    ;

iterationStatement
    : whileStatement
    | forStatement
    ;

whileStatement
    : WHILE LPAREN NL* expression NL* RPAREN NL* blockExpression
    | WHILE expression NL* blockExpression
    ;

rangeExpression
    : NUMBER RANGE NUMBER
    ;

initStatement
    : INIT LPAREN parameterList? RPAREN blockExpression
    ;

returnStatement
    : RETURN expression?
    ;

expressionStatement
    : expression
    ;

// Decorators/Annotations
decoratorList
    : decorator (NL* decorator)*
    ;

decorator
    : AT identifier (LPAREN NL* decoratorArgs? NL* RPAREN)?
    ;

decoratorArgs
    : decoratorArg                      // Single positional: @provider("aws")
    | namedArg (NL* COMMA NL* namedArg)* (NL* COMMA)? NL*  // Named args: @provider(first="aws", second="gcp")
    ;

namedArg
    : identifier ASSIGN expression
    ;

decoratorArg
    : arrayExpression
    | objectExpression
    | callMemberExpression
    | identifier
    | literal
    | MINUS NUMBER
    ;

// Expressions (precedence from lowest to highest)
expression
    : objectExpression
    | arrayExpression
    | assignmentExpression
    ;

assignmentExpression
    : orExpression ((ASSIGN | PLUS_ASSIGN) expression)?
    ;

orExpression
    : andExpression (OR andExpression)*
    ;

andExpression
    : equalityExpression (AND equalityExpression)*
    ;

equalityExpression
    : relationalExpression ((EQ | NE) relationalExpression)*
    ;

relationalExpression
    : additiveExpression ((LT | GT | LE | GE) additiveExpression)*
    ;

additiveExpression
    : multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression ((MULTIPLY | DIVIDE | MODULO) unaryExpression)*
    ;

unaryExpression
    : (MINUS | INCREMENT | DECREMENT | NOT) unaryExpression  // Prefix: --x
    | postfixExpression
    ;

postfixExpression
    : leftHandSideExpression (INCREMENT | DECREMENT)?      // Postfix: x++
    ;

leftHandSideExpression
    : callMemberExpression
    ;

callMemberExpression
    : primaryExpression (callOrMemberAccess)*
    ;

callOrMemberAccess
    : LPAREN argumentList? RPAREN
    | DOT identifier
    | LBRACK expression RBRACK
    ;

primaryExpression
    : LPAREN expression RPAREN
    | lambdaExpression
    | literal
    | identifier
    | thisExpression
    ;

thisExpression
    : THIS
    ;

lambdaExpression
    : LPAREN parameterList? RPAREN typeIdentifier? ARROW lambdaBody
    ;

lambdaBody
    : blockExpression
    | expression
    ;

blockExpression
    : LBRACE statementTerminator* statementList? statementTerminator* RBRACE
    ;

objectExpression
    : objectDeclaration
    ;

objectDeclaration
    : OBJECT LPAREN NL* (LBRACE NL* objectPropertyList? NL* RBRACE)? NL* RPAREN  // object() or object({ key: value })
    | LBRACE NL* objectPropertyList? NL* RBRACE                             // { key: value }
    ;

objectPropertyList
    : objectProperty (NL* COMMA NL* objectProperty)* (NL* COMMA)? NL*
    ;

objectProperty
    : objectKey objectInitializer?
    ;

objectKey
    : stringLiteral
    | IDENTIFIER
    | keyword
    ;

// Allow keywords as object property names (e.g., {type: "value"})
keyword
    : RESOURCE | COMPONENT | SCHEMA | INPUT | OUTPUT
    | IF | ELSE | WHILE | FOR | IN | RETURN
    | FUN | VAR | TYPE | INIT | THIS
    | OBJECT | ANY
    | TRUE | FALSE | NULL
    ;

objectInitializer
    : COLON expression
    ;

arrayExpression
    : LBRACK NL* FOR identifier (COMMA identifier)? IN (rangeExpression | arrayExpression | identifier) COLON compactBody NL* RBRACK  // Form 1: [for ...: body]
    | LBRACK NL* FOR identifier (COMMA identifier)? IN (rangeExpression | arrayExpression | identifier) RBRACK NL* forBody         // Form 2: [for ...] body
    | LBRACK NL* arrayItems? NL* RBRACK                                                                                            // Form 3: literal array
    ;

compactBody
    : IF LPAREN expression RPAREN expression (ELSE expression)?  // Inline if
    | IF expression expression (ELSE expression)?          // Inline if without parens
    | ifStatement                                          // Block if
    | expression
    ;

forStatement
    : FOR identifier (COMMA identifier)? IN (rangeExpression | arrayExpression | identifier) NL* forBody             // Form 2: for ... body
    ;
forBody
    : blockExpression
    | resourceDeclaration
    | ifStatement
    | expressionStatement
    | emptyStatement
    ;
arrayItems
    : arrayItem (NL* COMMA NL* arrayItem)* (NL* COMMA)?  NL*  // Add NL* around commas, support trailing comma
    ;

arrayItem
    : callMemberExpression
    | identifier
    | objectExpression
    | typeKeyword       // Add this
    | literal
    ;

// Type System
typeIdentifier
    : functionType (LBRACK NUMBER? RBRACK)*
    | (complexTypeIdentifier | OBJECT | ANY) (LBRACK NUMBER? RBRACK)*
    ;
functionType
    : LPAREN functionTypeParams? RPAREN ARROW typeIdentifier
    ;
functionTypeParams
    : typeIdentifier (COMMA typeIdentifier)*
    ;
complexTypeIdentifier
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

// Parameters
parameterList
    : parameter (COMMA parameter)*
    ;

parameter
    : typeIdentifier? identifier
    ;

// Arguments
argumentList
    : expression (COMMA expression)*
    ;

// Identifiers
identifier
    : stringLiteral
    | IDENTIFIER
    ;

// String literal (simple or interpolated)
stringLiteral
    : SIMPLE_STRING
    | interpolatedString
    ;

// Interpolated string (double-quoted with ${} or $var)
interpolatedString
    : DQUOTE_OPEN interpolatedStringContent* DQUOTE_CLOSE
    ;

// Content inside an interpolated string
interpolatedStringContent
    : DSTRING_TEXT
    | DSTRING_ESCAPE
    | DOLLAR_LITERAL
    | braceInterpolation
    | simpleInterpolation
    ;

// ${expression} interpolation
braceInterpolation
    : INTERP_OPEN interpolationExpression INTERP_CLOSE
    ;

// $identifier interpolation
simpleInterpolation
    : INTERP_SIMPLE
    ;

// Expression inside ${...}
interpolationExpression
    : INTERP_IDENTIFIER (INTERP_DOT INTERP_IDENTIFIER)*
    | INTERP_IDENTIFIER INTERP_LBRACK (INTERP_NUMBER | INTERP_STRING | INTERP_IDENTIFIER) INTERP_RBRACK
    | INTERP_IDENTIFIER INTERP_LPAREN (interpolationArg (INTERP_COMMA interpolationArg)*)? INTERP_RPAREN
    ;

// Arguments in interpolation function calls
interpolationArg
    : INTERP_IDENTIFIER
    | INTERP_NUMBER
    | INTERP_STRING
    ;

// Literals
literal
    : NUMBER
    | stringLiteral
    | TRUE
    | FALSE
    | NULL
    ;
