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
```

## Testing

- Test files: `examples/` directory
- Main test file: `examples/component.kite`

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
type == KiteTokenTypes.WHITESPACE
```

### Navigation: Declaration vs Reference

An identifier is a **declaration name** (not navigable) if followed by `=`, `{`, `+=`, or `:`.
Otherwise it's a **reference** (navigable to its declaration).

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
- Only shows for function calls, not declarations

### Inlay Hints

Shows inline hints in the editor:

- Type hints for variables without explicit type (e.g., `var x = "hello"` shows `:string`)
- Parameter name hints in function calls (e.g., `greet("alice")` shows `name:`)
- Configurable via Settings > Editor > Inlay Hints > Kite

### Type Checking / Error Highlighting

Real-time validation with warnings and errors:

- Undefined reference detection: warns when identifiers don't resolve to any declaration
- Type mismatch detection: error when assigned value type doesn't match declared type
- Checks variable, input, and output declarations with explicit types
- Supports string, number, boolean, null, object, and array types
- Skips property access (handled by reference resolution)
- Skips type annotations and declaration names

### Cross-file Navigation

Go to Declaration across files:

- Cmd+Click (Mac) or Ctrl+Click (Win/Linux) on identifiers from imported modules
- Supports `import * from "path/to/file.kite"` syntax
- Automatically resolves relative paths from the current file
- Recursive import resolution for transitive dependencies
- Type checking also considers imported declarations

### Force Clean Builds

```bash
./gradlew clean compileJava --no-build-cache --rerun-tasks
killall -9 java  # Kill lingering IDE instances
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
| Cross-file Nav      | `reference/KiteImportHelper.java`              |
| Structure View      | `structure/KiteStructureViewElement.java`      |
| References          | `reference/KiteReferenceContributor.java`      |
