# Editor Features

[Back to Features Index](Features.md)

## Code Completion

### Context-Aware Completion

**In resource blocks (before `=`):**

- Shows only schema properties for the resource type
- Filters out already-defined properties

**In resource blocks (after `=`):**

- Shows variables, inputs, outputs, resources, components, functions
- **Auto-import**: Suggests symbols from unimported files with automatic import insertion

**General completion:**

- Local declarations
- Imported symbols
- Built-in types
- **Auto-import**: Suggests symbols from project files with automatic import insertion

### Auto-Import Completion

When typing a symbol name, completions include symbols from files not yet imported. Selecting such a completion automatically adds the import statement:

```kite
// Before: typing "bucket" shows completion "bucketName (import from decorators.kite)"
resource Storage backup {
    name = bucketName<caret>
}

// After selecting the completion:
import bucketName from "decorators.kite"
resource Storage backup {
    name = bucketName
}
```

**Implementation files:**
- `completion/KiteResourceCompletionProvider.java` - Auto-import in resource blocks
- `completion/KiteGeneralCompletionProvider.java` - Auto-import in general contexts

## Inlay Hints

### Variable Type Hints

Shows inferred type after variable name:

```kite
var x:string = "hello"  // :string is the hint
```

### Parameter Name Hints

Shows parameter names in function calls:

```kite
greet(name:"Alice", age:30)  // name: and age: are hints
```

### Resource Property Type Hints

Shows property types from matching schema:

```kite
resource DatabaseConfig db {
    host:string = "localhost"  // :string is the hint
}
```

## Quick Documentation

Press **Ctrl+Q** (Windows/Linux) or **F1** (Mac) to show documentation popup.

### Supported Elements

- Variables (type, default value)
- Inputs/Outputs (type, default value, parent component)
- Resources (type, properties)
- Components (inputs, outputs)
- Schemas (properties)
- Functions (parameters, return type)

### Features

- Shows preceding comments as documentation
- Displays decorators
- Works in string interpolation
- Color-coded: types (blue), strings (green), numbers (blue)

## Formatting

### Features

- Automatic indentation
- Property alignment in schemas and resources
- Configurable indent size (default: 2 spaces)
- Continuation indent for wrapped lines (default: 4 spaces)

## Smart Enter

Enhanced Enter key behavior for improved editing experience.

### After Opening Brace

When pressing Enter after `{`:

```kite
// Before (cursor after {):
schema Config {|

// After:
schema Config {
  |
}
```

- Auto-inserts closing brace if missing
- Adds proper indentation (2 spaces)
- Positions cursor on the indented blank line

### Inside Block Comments

When pressing Enter inside `/* */` comments:

```kite
// Before:
/* This is a comment|

// After:
/* This is a comment
 * |
```

- Adds ` * ` prefix to continuation lines
- Preserves indentation

## Code Folding

### Supported Foldable Regions

| Region | Placeholder | Description |
|--------|-------------|-------------|
| Multiple imports | `[N imports...]` | Folds 2+ consecutive import statements |
| Schema blocks | `{...}` | Folds schema body |
| Component blocks | `{...}` | Folds component body |
| Function blocks | `{...}` | Folds function body |
| Resource blocks | `{...}` | Folds resource body |
| Object literals | `{...}` | Folds inline objects |
| For statements | `{...}` | Folds for loop body |
| While statements | `{...}` | Folds while loop body |
| Block comments | `/*...*/` | Folds multi-line comments |

### Import Folding

- Requires 2+ imports to fold (single import is not folded)
- Shows count in placeholder: `[3 imports...]`
- Works with all import types (named, wildcard, multi-symbol)
- Imports with blank lines between them are still folded together

### Keyboard Shortcuts

| Action | Mac | Windows/Linux |
|--------|-----|---------------|
| Collapse | `Cmd+.` or `Cmd+-` | `Ctrl+.` or `Ctrl+-` |
| Expand | `Cmd+.` or `Cmd++` | `Ctrl+.` or `Ctrl++` |
| Collapse All | `Cmd+Shift+-` | `Ctrl+Shift+-` |
| Expand All | `Cmd+Shift++` | `Ctrl+Shift++` |

## Implementation Files

| Feature             | File                                          |
|---------------------|-----------------------------------------------|
| Code Completion     | `completion/KiteCompletionContributor.java`   |
| Inlay Hints         | `hints/KiteInlayHintsProvider.java`           |
| Quick Documentation | `documentation/KiteDocumentationProvider.java`|
| Formatter           | `formatter/KiteBlock.java`                    |
| Code Folding        | `KiteFoldingBuilder.java`                     |
| Smart Enter         | `editor/KiteEnterHandlerDelegate.java`        |

## Test Files

| Feature        | Test File                          |
|----------------|------------------------------------|
| Code Folding   | `KiteFoldingBuilderTest.java`      |
| Smart Enter    | `KiteEnterHandlerDelegateTest.java`|
