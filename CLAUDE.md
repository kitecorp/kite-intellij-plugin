# Kite IntelliJ Plugin - Development Notes

> **See also:** [docs/Features.md](docs/Features.md) for comprehensive feature documentation.

## Development Principles

1. **Apply existing patterns first** - Look for similar working code before creating new solutions
2. **Ask questions when unsure** - Clarify requirements before coding
3. **Listen to user hints** - Try user suggestions first
4. **Debug actual problems** - Focus on what's actually broken
5. **Prefer simple solutions** - Complexity should match the problem
6. **When stuck, step back** - Look at similar working code
7. **Follow TDD** - Write tests first, then implement features to make tests pass
8. **Apply SOLID principles** - Single responsibility, Open/closed, Liskov substitution, Interface segregation,
   Dependency inversion
9. **Use Clean Code** - Meaningful names, small functions, clear intent, no side effects
10. **DRY** - Don't Repeat Yourself; extract common patterns into reusable methods

## Commands

```bash
./gradlew runIde          # Run plugin in sandbox IDE
./gradlew buildPlugin     # Build plugin ZIP
./gradlew clean build     # Clean rebuild
./gradlew compileJava     # Compile only
```

## Testing

### Manual Testing
- Test files: `examples/` directory
- Main test file: `examples/simple.kite`, `examples/common.kite`

### Unit Tests

```bash
./gradlew test           # Run all tests
./gradlew test --rerun-tasks  # Force re-run
```

- Test base class: `KiteTestBase` extends `BasePlatformTestCase`
- Test location: `src/test/java/cloud/kitelang/intellij/`
- Test data: `src/test/resources/testData/`

Key test patterns:

- Use `addFile("name.kite", content)` to add files to test project
- Use `configureByText(content)` to set up the main test file
- Use `myFixture.doHighlighting()` to trigger annotators
- Use `myFixture.getFile().findReferenceAt(offset)` to test references

Test assertion best practices:

- **Prefer specific assertions over multiple `.contains()` checks**
- When verifying transformed code, check for the exact expected result
- Example: Instead of separate `contains("import usedVar")` and `!contains("unusedVar")` checks,
  use a single `contains("import usedVar from \"common.kite\"")` that verifies the complete line

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
- **Array-typed property definitions**: `type[] propertyName` pattern (e.g., `string[] tags`)
- **Type annotations**: built-in types and capitalized type names
- **Property access**: handled by reference resolution

### Array Types and ARRAY_LITERAL

The PSI structure for array types like `string[] tags` is:

```
IDENTIFIER("string") -> ARRAY_LITERAL (contains LBRACK, RBRACK) -> IDENTIFIER("tags")
```

**Important**: The `[]` brackets are wrapped in an `ARRAY_LITERAL` element, NOT exposed as separate `LBRACK`/`RBRACK`
tokens at the same level as the identifiers.

**Pattern for detecting array-typed properties:**

```java
// When checking if an identifier is a schema property name
if (prevType == KiteElementTypes.ARRAY_LITERAL) {
    // Walk back past ARRAY_LITERAL to find the type identifier
    PsiElement beforeArray = skipWhitespaceBackward(prev.getPrevSibling());
    if (beforeArray != null && beforeArray.getNode() != null) {
        IElementType beforeArrayType = beforeArray.getNode().getElementType();
        if (beforeArrayType == KiteTokenTypes.IDENTIFIER || beforeArrayType == KiteTokenTypes.ANY) {
            return true; // This is a property name after type[]
        }
    }
}
```

**Files that need ARRAY_LITERAL handling:**

| File                             | Method                      | Purpose                                         |
|----------------------------------|-----------------------------|-------------------------------------------------|
| `KiteTypeCheckingAnnotator.java` | `isPropertyDefinition()`    | Recognizes `type[] name` as valid property      |
| `KiteTypeCheckingAnnotator.java` | `extractSchemaProperties()` | Extracts array-typed properties                 |
| `KiteReferenceContributor.java`  | `isAfterTypeInSchemaBody()` | Skips references for array-typed property names |
| `KiteCompletionContributor.java` | `extractSchemaProperties()` | Autocomplete for array-typed properties         |
| `KiteInlayHintsProvider.java`    | `extractSchemaProperties()` | Inlay hints for array-typed properties          |
| `formatter/KiteBlock.java`       | `getSchemaTypeLength()`     | Calculate type width including `[]`             |

### Handling `any` Type Keyword

The `any` keyword is tokenized as `KiteTokenTypes.ANY`, NOT as `IDENTIFIER`. This is important when parsing type
positions.

**Files that need special ANY handling:**

| File                             | Method                           | Purpose                                                       |
|----------------------------------|----------------------------------|---------------------------------------------------------------|
| `KiteSyntaxHighlighter.java`     | `getTokenHighlights()`           | Returns IDENTIFIER_KEYS (not KEYWORD_KEYS) so it's not purple |
| `KiteAnnotator.java`             | `annotate()`                     | Colors ANY with TYPE_NAME (blue) like other types             |
| `KiteTypeCheckingAnnotator.java` | `isPropertyDefinition()`         | Recognizes `any propertyName` as valid property               |
| `KiteTypeCheckingAnnotator.java` | `extractSchemaProperties()`      | Extracts `any` typed properties from schemas                  |
| `KiteCompletionContributor.java` | `extractSchemaProperties()`      | Autocomplete for `any` typed properties                       |
| `KiteInlayHintsProvider.java`    | `extractSchemaProperties()`      | Inlay hints for `any` typed properties                        |
| `KiteDocumentationProvider.java` | `formatMemberDeclarationParts()` | Quick docs for `input any`/`output any`                       |

**Files that DON'T need ANY handling:**

Reference/navigation files (KiteReference, KiteReferenceContributor, KiteGotoDeclarationHandler) use the "last
identifier before = or {" pattern to find declaration **names**. Since names are always IDENTIFIER tokens, they work
correctly without ANY handling.

**Pattern for adding ANY support:**

```java
// Handle 'any' keyword as a type
if (childType == KiteTokenTypes.ANY) {
    currentType = "any";
    continue;
}
// Then handle regular IDENTIFIER for other types
if (childType == KiteTokenTypes.IDENTIFIER) {
    // existing type handling...
}
```

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

### Find Usages with String Interpolations

When finding usages (Cmd+Click on declaration), the plugin searches for:

1. **IDENTIFIER tokens**: Standard identifier references
2. **INTERP_SIMPLE tokens**: `$varName` patterns in strings
3. **INTERP_IDENTIFIER tokens**: Variables inside `${...}` patterns
4. **STRING tokens**: Legacy fallback with regex pattern matching

Implementation in `KiteGotoDeclarationHandler.findUsagesRecursive()`.

### Resource Block Completion

Context-aware autocomplete in resource blocks:

1. **Before `=`**: Shows only schema properties for the resource type
    - Looks up schema by matching name to resource type
    - Filters out already-defined properties
2. **After `=`**: Shows variables, inputs, outputs, resources, components, functions
    - Functions display with correct names (not return types)

### Resource Property Navigation

Cmd+Click on property name in resource block navigates to schema property definition:

- Finds schema matching resource type name
- Searches for property definition in schema body
- Returns the property name identifier for navigation

### Structure View Icons

Different colored circular icons for each element type:

- Resource: Purple (R)
- Component: Blue (C)
- Schema: Green (S)
- Function: Orange (F)
- Type: Blue (T)
- Variable: Purple (V)
- Input: Amber yellow (I)
- Output: Lime yellow-green (O)
- Import: Brown (M)
- Property: Cornflower blue (P)

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
| Structure Icons     | `structure/KiteStructureViewIcons.java`        |
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

- always follow TDD. Functionality without a test is not valid
- we use CLEAN code principles, DRY, KISS and SOLID principlex from software architecture
- Use static imports when possible