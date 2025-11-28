# Kite IntelliJ Plugin - Development Notes

## Development Principles

1. **Apply existing patterns first** - Look for similar working code before creating new solutions
2. **Ask questions when unsure** - Clarify requirements before coding
3. **Listen to user hints** - Try user suggestions first
4. **Debug actual problems** - Focus on what's actually broken
5. **Prefer simple solutions** - Complexity should match the problem
6. **When stuck, step back** - Look at similar working code

## Commands

```bash
./gradlew runIde          # Run plugin in sandbox IDE
./gradlew buildPlugin     # Build plugin ZIP
./gradlew clean build     # Clean rebuild
./gradlew compileJava     # Compile only
```

## Testing

- Test files: `examples/` directory
- Main test file: `examples/simple.kite`, `examples/common.kite`

## Key Implementation Notes

### Formatter Indent Types

```java
Indent.getSpaceIndent(n)      // ABSOLUTE - n spaces from column 0
Indent.getNormalIndent()       // RELATIVE - adds INDENT_SIZE (2 spaces)
Indent.getContinuationIndent() // RELATIVE - adds CONTINUATION_INDENT_SIZE (4 spaces)
```

### Whitespace Handling

Always check for both IntelliJ and Kite whitespace types:
```java
type == TokenType.WHITE_SPACE ||  // IntelliJ platform
type == KiteTokenTypes.NL ||
type == KiteTokenTypes.WHITESPACE ||
type == KiteTokenTypes.NEWLINE
```

### Navigation: Declaration vs Reference

An identifier is a **declaration name** (not navigable) if followed by `=`, `{`, `+=`, or `:`.
Otherwise it's a **reference** (navigable to its declaration).

### Function Parameter Navigation

When resolving references inside function bodies, check if the identifier is a function parameter:

1. Walk up PSI tree to find enclosing `FUNCTION_DECLARATION`
2. Search parameter list for matching name (pattern: `type paramName, type paramName`)
3. Return the parameter name identifier

### Import Resolution Order

Imports are resolved in this order (see `KiteImportHelper.resolveFilePath()`):

1. Relative to containing file (e.g., `"common.kite"`)
2. Project base path
3. Project-local providers: `.kite/providers/`
4. User-global providers: `~/.kite/providers/`
5. Package-style paths: `"aws.DatabaseConfig"` → `aws/DatabaseConfig.kite`

### Type Checking Exclusions

The annotator skips validation for:

- **Decorator names**: identifiers after `@` (decorators are global/built-in)
- **Schema property definitions**: `type propertyName` pattern inside schema/resource bodies
- **Type annotations**: built-in types and capitalized type names
- **Property access**: handled by reference resolution

### Inlay Hints

Two types of inlay hints:

1. **Variable type hints**: Shows inferred type after variable name (e.g., `var x:string = "hello"`)
2. **Parameter name hints**: Shows parameter names before arguments (e.g., `greet(name:"Alice")`)
3. **Resource property type hints**: Shows property types from matching schema

Schema lookup for resource hints:

- Find schema with same name as resource type
- Search current file, then imported files
- Extract `type propertyName` pairs from schema body

### Quick Documentation (Ctrl+Q / F1)

Shows documentation popup for declarations. Supports:
- Variables, inputs, outputs, resources, components, schemas, functions
- Shows type, default value, decorators, and preceding comments
- Components display all inputs and outputs
- Works in string interpolation (`${var}` and `$var`)
- Colors match editor theme (types=blue, strings=green, numbers=blue)

### Parameter Info (Ctrl+P)

Shows function parameter hints while typing inside function calls:
- Displays parameter names and types from function declaration
- Highlights current parameter based on cursor position
- Cross-file support: searches imported files for function declarations

### Force Clean Builds

```bash
./gradlew clean compileJava --no-build-cache --rerun-tasks
killall -9 java  # Kill lingering IDE instances
pkill -f "idea"  # Kill sandbox IDE
```

## File Structure

| Feature             | Main File                                      |
|---------------------|------------------------------------------------|
| Syntax Highlighting | `highlighting/KiteSyntaxHighlighter.java`      |
| Formatter           | `formatter/KiteBlock.java`                     |
| Go to Declaration   | `navigation/KiteGotoDeclarationHandler.java`   |
| Find Usages         | `navigation/KiteNavigatablePsiElement.java`    |
| Code Completion     | `completion/KiteCompletionContributor.java`    |
| Breadcrumbs         | `KiteBreadcrumbsProvider.java`                 |
| Quick Documentation | `documentation/KiteDocumentationProvider.java` |
| Parameter Info      | `parameterinfo/KiteParameterInfoHandler.java`  |
| Inlay Hints         | `hints/KiteInlayHintsProvider.java`            |
| Type Checking       | `highlighting/KiteTypeCheckingAnnotator.java`  |
| Import Resolution   | `reference/KiteImportHelper.java`              |
| Structure View      | `structure/KiteStructureViewElement.java`      |
| References          | `reference/KiteReferenceContributor.java`      |

## Kite Language Syntax

```kite
// Import syntax
import * from "path/to/file.kite"
import * from "aws.DatabaseConfig"  // Package-style

// Type definition
type Region = "us-east-1" | "us-west-2"

// Schema definition
schema DatabaseConfig {
  string host
  number port = 5432
  boolean ssl = true
}

// Resource using schema
resource DatabaseConfig photos {
  host = "localhost"  // Inlay hint shows :string
  port = 5433         // Inlay hint shows :number
}

// Variable declarations
var string explicitType = "hello"
var inferredType = "world"  // Inlay hint shows :string

// Function declaration
fun calculateCost(number instances, string name) number {
  var baseCost = 0.10
  return instances * baseCost  // Cmd+Click on 'instances' navigates to param
}

// Decorators (global, not imported)
@provisionOn(["aws"])
@tags({Environment: "production"})
resource VM.Instance server { }

// Component with inputs/outputs
component WebServer {
  input string port = "8080"
  output string endpoint = "http://localhost:${port}"
}
```
