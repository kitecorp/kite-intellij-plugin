# Kite IntelliJ Plugin - Implementation Summary

## Implemented Features

### Core IDE Features

- **Syntax Highlighting**: Keywords, literals, comments, decorators, operators, identifiers
- **Code Formatting**: Auto-indent, brace matching, smart enter
- **Code Completion**: Keywords, types, identifiers in scope
- **Breadcrumbs**: Navigation path showing current location in file structure
- **Structure View**: Tree view of file declarations

### Navigation Features

- **Go to Declaration** (Cmd+Click / Ctrl+Click)
    - Variables, functions, types, schemas, resources, components
    - Function parameters (click on usage navigates to parameter definition)
    - Cross-file navigation via imports
    - Import path navigation (click on "file.kite" string to open file)
    - Property access chains (e.g., `server.tag.New`)
    - String interpolation (`${var}` and `$var`)

- **Find Usages**: Shows all references to a declaration

### Documentation Features

- **Quick Documentation** (Ctrl+Q / F1)
    - Shows declaration type, signature, comments
    - Works for all declaration types
    - Supports string interpolation

- **Parameter Info** (Ctrl+P)
    - Shows function parameters while typing
    - Highlights current parameter
    - Cross-file support for imported functions

### Inlay Hints

- **Variable type hints**: `var x = "hello"` shows `:string`
- **Parameter name hints**: `greet("Alice")` shows `name:`
- **Resource property type hints**: Property types from matching schema

### Type Checking / Error Highlighting

- **Undefined reference warnings**: Identifiers not found in scope
- **Type mismatch errors**: Wrong type assigned to typed variable
- **Smart exclusions**: Skips decorators, schema properties, type annotations

### Import Resolution

Supports multiple import styles and search paths:

1. Relative paths: `"common.kite"`, `"../shared/utils.kite"`
2. Project base path
3. Project-local providers: `.kite/providers/`
4. User-global providers: `~/.kite/providers/`
5. Package-style: `"aws.DatabaseConfig"` → `aws/DatabaseConfig.kite`

## File Structure

```
src/main/java/io/kite/intellij/
├── KiteLanguage.java                    # Language definition
├── KiteFileType.java                    # File type handler
├── KiteIcons.java                       # Icon provider
├── KiteBreadcrumbsProvider.java         # Breadcrumb navigation
│
├── completion/
│   └── KiteCompletionContributor.java   # Code completion
│
├── documentation/
│   └── KiteDocumentationProvider.java   # Quick documentation
│
├── formatter/
│   ├── KiteBlock.java                   # Code formatting
│   └── KiteFormattingModelBuilder.java
│
├── highlighting/
│   ├── KiteSyntaxHighlighter.java       # Syntax highlighting
│   ├── KiteSyntaxHighlighterFactory.java
│   ├── KiteColorSettingsPage.java       # Color settings UI
│   ├── KiteTypeCheckingAnnotator.java   # Type checking/errors
│   └── KiteBraceMatcher.java            # Brace matching
│
├── hints/
│   └── KiteInlayHintsProvider.java      # Inlay hints
│
├── lexer/
│   └── KiteLexerAdapter.java            # ANTLR lexer wrapper
│
├── navigation/
│   ├── KiteGotoDeclarationHandler.java  # Go to Declaration
│   └── KiteNavigatablePsiElement.java   # Find Usages support
│
├── parameterinfo/
│   └── KiteParameterInfoHandler.java    # Ctrl+P parameter info
│
├── parser/
│   ├── KiteParserDefinition.java        # Parser definition
│   └── KitePsiParser.java               # PSI parser
│
├── psi/
│   ├── KiteFile.java                    # PSI file
│   ├── KiteTokenTypes.java              # Token types
│   └── KiteElementTypes.java            # Element types
│
├── reference/
│   ├── KiteImportHelper.java            # Import resolution
│   └── KiteReferenceContributor.java    # Reference handling
│
└── structure/
    ├── KiteStructureViewElement.java    # Structure view
    ├── KiteStructureViewIcons.java      # Element icons
    └── KiteStructureViewModel.java
```

## Build & Run

```bash
./gradlew runIde          # Run plugin in sandbox IDE
./gradlew buildPlugin     # Build plugin ZIP
./gradlew clean build     # Clean rebuild
./gradlew compileJava     # Compile only
pkill -f "idea"           # Kill sandbox IDE
```

## Test Files

- `examples/simple.kite` - Main test file with imports, schemas, resources, functions
- `examples/common.kite` - Shared declarations for cross-file testing
- `examples/component.kite` - Component declarations
- `examples/decorators.kite` - Decorator examples

## Recent Changes

### Version 0.14.0

1. **Resource Block Code Completion**
    - Context-aware autocomplete distinguishes before/after `=`
    - Before `=`: Shows only schema properties for the resource type
    - After `=`: Shows variables, inputs, outputs, resources, components, functions
    - Functions display with correct names (not return types)
    - Already-defined properties filtered from suggestions

2. **Resource Property Navigation**
    - Cmd+Click on property name navigates to schema property definition
    - Looks up schema by matching name to resource type
    - Added `findSchemaPropertyDefinition()` in `KiteGotoDeclarationHandler.java`

3. **Resource Property Inlay Hints**
    - Shows type hints after property names in resources
    - Looks up types from matching schema by name
    - Searches current file and imports for schema definitions

4. **Find Usages in String Interpolations**
    - Cmd+Click on declaration now shows usages inside strings
    - Supports `$varName` (INTERP_SIMPLE) tokens
    - Supports `${varName}` (INTERP_IDENTIFIER) tokens
    - Legacy STRING token fallback with regex pattern matching
    - Added `findUsagesInString()` helper method

5. **Structure View Icons**
    - OUTPUT icon changed to lime yellow-green (205, 220, 57)
    - INPUT icon remains amber yellow (255, 193, 7)
    - Better visual distinction between input and output

6. **Syntax Highlighting Fixes**
    - Component definition names use default text color (not blue)
    - Only component instantiation type names are blue
    - Pattern: `component TypeName instanceName { }` - TypeName blue
    - Pattern: `component DefinitionName { }` - DefinitionName default

### Previous Session

1. **Function parameter navigation**
    - Cmd+Click on parameter usage navigates to parameter declaration
    - Added `findParameterInEnclosingFunction()` and `findParameterInFunction()`

2. **Provider directory search for imports**
    - Added `.kite/providers/` (project-local) search
    - Added `~/.kite/providers/` (user-global) search
    - Added package-style path resolution

3. **Type checking improvements**
    - Skip decorator names (after `@`)
    - Skip schema property definitions
    - Import path string navigation
