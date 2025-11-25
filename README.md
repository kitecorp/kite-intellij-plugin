# Kite IntelliJ Plugin

IntelliJ IDEA plugin for the Kite Infrastructure as Code language.

## Features

### Syntax Highlighting
- Full lexical and semantic highlighting for `.kite` files
- Type-aware coloring (types shown in blue, other identifiers use default theme color)
- Support for keywords, strings, numbers, comments, decorators, and operators

### Go to Declaration (Cmd+Click)
- Navigate from identifier usages to their declarations
- **Simple identifiers**: Click `server` to jump to `resource VM.Instance server { }`
- **Property access**: Click `size` in `server.size` to jump to the `size = ...` property inside the server declaration
- **Find usages**: Cmd+Click on a declaration name shows a dropdown of all usages with:
  - Context-aware icons (Resource, Component, Function, etc.)
  - Full line of code preview
  - File name and line number

### Structure View
- Hierarchical outline with color-coded icons
- Supports resources, components, schemas, functions, types, variables, inputs, outputs, and imports

### Code Folding
- Collapse/expand declarations (components, resources, schemas, functions)
- Collapse/expand control flow statements (for, while)
- Collapse/expand object literals and comments

### Code Formatting
- Automatic code formatting with Reformat Code action (Cmd+Alt+L / Ctrl+Alt+L)
- Vertical alignment of object properties and declarations
- Proper indentation for nested structures

### Other Features
- **Comment/Uncomment**: Toggle line and block comments (Cmd+/ or Ctrl+/)
- **Brace Matching**: Automatic highlighting of matching braces, brackets, and parentheses
- **Custom Color Scheme**: Configurable syntax colors via Settings > Editor > Color Scheme > Kite

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
│   │   └── KitePsiParser.java         # PSI parser
│   ├── psi/
│   │   ├── KiteFile.java              # PSI file
│   │   ├── KiteTokenTypes.java        # Token type definitions
│   │   └── KiteElementTypes.java      # PSI element types
│   ├── highlighting/
│   │   ├── KiteSyntaxHighlighter.java
│   │   ├── KiteAnnotator.java         # Semantic highlighting
│   │   └── KiteColorSettingsPage.java
│   ├── navigation/
│   │   ├── KiteGotoDeclarationHandler.java
│   │   ├── KiteNavigatablePsiElement.java
│   │   └── KiteNavigationIconProvider.java
│   ├── reference/
│   │   ├── KiteReferenceContributor.java
│   │   └── KiteReference.java
│   ├── structure/
│   │   ├── KiteStructureViewBuilderFactory.java
│   │   └── KiteStructureViewIcons.java
│   └── formatter/
│       ├── KiteFormattingModelBuilder.java
│       └── KiteBlock.java
└── resources/
    ├── META-INF/plugin.xml            # Plugin configuration
    └── icons/kite-file.svg            # File icon
```

## Development

Built with:
- IntelliJ Platform 2024.1
- Java 21 (compiled to Java 17 bytecode)
- Gradle 8.10.2
- ANTLR4 4.13.1
