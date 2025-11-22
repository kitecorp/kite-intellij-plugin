# Kite IntelliJ Plugin

IntelliJ IDEA plugin for the Kite Infrastructure as Code language.

## Features Implemented

### ✅ Core Infrastructure
- **Language Definition**: Basic Kite language and file type registration
- **File Recognition**: `.kite` files recognized with custom icon
- **ANTLR4 Integration**: Parser and lexer generated from Kite.g4 grammar
- **PSI Integration**: Token types and element types for IntelliJ PSI system

### ✅ Syntax Highlighting
- **Lexer**: ANTLR4-based lexer wrapped for IntelliJ
- **Highlighter**: Syntax highlighting for:
  - Keywords (resource, component, schema, if, while, for, etc.)
  - Strings and numbers
  - Comments (line and block)
  - Decorators (@)
  - Operators (→, .., etc.)
  - Identifiers

### ✅ Editor Integration
- **Color Settings Page**: Customizable syntax colors in IDE settings
- **Parser Definition**: Basic PSI file structure

## Build & Run

```bash
# Build the plugin
./gradlew build

# Run in sandbox IDE
./gradlew runIde

# Run tests
./gradlew test
```

## Project Structure

```
src/main/
├── java/io/kite/intellij/
│   ├── KiteLanguage.java              # Language definition
│   ├── KiteFileType.java              # File type handler
│   ├── KiteIcons.java                 # Icon provider
│   ├── lexer/
│   │   └── KiteLexerAdapter.java      # ANTLR lexer adapter
│   ├── parser/
│   │   ├── KiteParserDefinition.java  # Parser definition
│   │   ├── KitePsiParser.java         # PSI parser
│   │   └── KitePsiElement.java        # Base PSI element
│   ├── psi/
│   │   ├── KiteFile.java              # PSI file
│   │   ├── KiteTokenTypes.java        # Token type definitions
│   │   └── KiteElementTypes.java      # PSI element types
│   └── highlighting/
│       ├── KiteSyntaxHighlighter.java # Syntax highlighter
│       ├── KiteSyntaxHighlighterFactory.java
│       └── KiteColorSettingsPage.java # Color settings UI
└── resources/
    ├── META-INF/plugin.xml            # Plugin configuration
    └── icons/kite-file.svg            # File icon
```

## Known Issues

- **Lexer whitespace handling**: ANTLR grammar skips whitespace, need to modify for IntelliJ compatibility

## Next Steps

- [ ] Fix lexer whitespace token handling
- [ ] Add code completion
- [ ] Add code folding
- [ ] Add structure view
- [ ] Add formatter
- [ ] Add error annotations

## Development

Built with:
- IntelliJ Platform 2024.1
- Java 21 (compiled to Java 17 bytecode)
- Gradle 8.10.2
- ANTLR4 4.13.1
