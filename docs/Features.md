# Kite IntelliJ Plugin - Feature Documentation

## Table of Contents

- [Import System](#import-system)
- [Navigation](#navigation)
- [Code Completion](#code-completion)
- [Type Checking](#type-checking)
- [Quick Fixes](#quick-fixes)
- [Inlay Hints](#inlay-hints)
- [Quick Documentation](#quick-documentation)
- [Structure View](#structure-view)
- [Formatting](#formatting)
- [Smart Enter](#smart-enter)
- [Code Folding](#code-folding)
- [Refactoring](#refactoring)

---

## Import System

### Supported Import Syntax

```kite
// Named import - single symbol
import defaultRegion from "common.kite"

// Named import - multiple symbols
import alpha, beta, gamma from "common.kite"

// Wildcard import - all exports
import * from "common.kite"

// Package-style path
import DatabaseConfig from "aws.DatabaseConfig"
```

### Importable Types

The following can be imported from other files:

| Type                | Example                         | Description                  |
|---------------------|---------------------------------|------------------------------|
| Variables           | `var region = "us-east-1"`      | Global variable declarations |
| Functions           | `fun formatName(...) { }`       | Function definitions         |
| Schemas             | `schema Config { }`             | Schema type definitions      |
| Components          | `component WebServer { }`       | Component definitions        |
| Resource Instances  | `resource Config myDb { }`      | Instantiated resources       |
| Component Instances | `component Server myServer { }` | Instantiated components      |

### Import Path Resolution

Imports are resolved in the following order (see `KiteImportHelper.resolveFilePath()`):

1. **Relative to containing file** - `"common.kite"` looks in same directory
2. **Project base path** - Searches from project root
3. **Project-local providers** - `.kite/providers/` directory
4. **User-global providers** - `~/.kite/providers/` directory
5. **Package-style paths** - `"aws.DatabaseConfig"` resolves to `aws/DatabaseConfig.kite`

### Import Validation

#### Broken Import Detection

- **Error**: `Cannot resolve import path 'nonexistent.kite'`
- Triggered when the import path points to a file that doesn't exist
- Implemented in: `KiteTypeCheckingAnnotator`
- Test file: `KiteBrokenImportAnnotatorTest`

#### Empty Import Path

- **Error**: `Empty import path`
- Triggered when import path is an empty string: `import x from ""`

#### Import Ordering

- **Error**: `Import statements must appear at the beginning of the file`
- Imports must come before any other statements (var, fun, schema, resource, component)
- Multiple consecutive imports at the top are allowed

### Unused Import Detection

- **Warning (weak)**: `Unused import 'symbolName'`
- Detects imports that are never referenced in the file
- Recognizes usage in:
    - Direct identifier references
    - String interpolation (`$var` and `${var}`)
    - Type positions (resource types, function parameter types)
- Implemented in: `KiteUnusedImportAnnotator`
- Test file: `KiteUnusedImportAnnotatorTest`

### Duplicate Import Detection

- **Warning**: `'symbolName' is already imported from "path.kite"`
- Detects when the same symbol is imported multiple times:
    - Same symbol from different files
    - Same symbol from the same file (redundant import)
    - Same symbol appearing twice in a single import statement
    - Named import after a wildcard import from the same file
- Implemented in: `KiteDuplicateImportAnnotator`
- Test file: `KiteDuplicateImportAnnotatorTest`

### Remove Unused Import Quick Fix

When an unused import is detected, a quick fix is available:

- **Single symbol import**: "Remove unused import" - removes entire import line
- **Multi-symbol import**: "Remove unused import 'symbolName'" - removes only the unused symbol
- If all symbols in a multi-import are unused, removes the entire line
- Implemented in: `RemoveUnusedImportQuickFix`
- Test file: `RemoveUnusedImportQuickFixTest`

### Planned Import Features

| Feature                       | Description                                               | Complexity |
|-------------------------------|-----------------------------------------------------------|------------|
| [x] Import Sorting            | Auto-sort imports alphabetically when optimizing          | Easy       |
| [x] Unused Wildcard Warning   | Warn if `import *` doesn't use any symbol from the file   | Easy       |
| [x] Import Folding            | Collapse multiple imports into `[3 imports...]` in editor | Easy       |
| [x] Wildcard to Named         | Convert `import *` to explicit named imports quick fix    | Medium     |
| [x] Import Path Completion    | Autocomplete file paths in import strings                 | Medium     |
| [ ] Circular Import Detection | Warn when files import each other in a cycle              | Medium     |
| [ ] Import Grouping           | Group imports by category (local vs external)             | Medium     |
| [ ] Relative Path Suggestions | Suggest shorter relative paths for imports                | Medium     |
| [ ] Auto-import on Paste      | Add imports when pasting code with undefined symbols      | Hard       |

---

## Navigation

### Go to Class (Cmd+O / Ctrl+N)

Navigate to schemas and components by name:

- Opens a popup to search by name
- Fuzzy matching supported
- Shows file location
- Works across all project files

**Finds:**
- Schemas
- Components

### Go to Symbol (Cmd+Alt+O / Ctrl+Alt+Shift+N)

Navigate to any declaration by name:

- Opens a popup to search by name
- Fuzzy matching supported
- Shows file location and type icon
- Works across all project files

**Finds:**
- Schemas
- Components
- Functions
- Variables
- Resources
- Type aliases

### Go to Declaration (Cmd+Click / Ctrl+Click)

Navigate to the definition of:

- Variables, inputs, outputs
- Functions
- Schemas
- Components
- Resource and component instances
- Imported symbols (navigates to source file)
- Function parameters (within function body)
- Resource properties (navigates to schema property definition)

### Find Usages

When clicking on a declaration, all usages are highlighted:

- Standard identifier references
- String interpolation usage (`$var` and `${var}`)

### Breadcrumbs

Shows hierarchical path in editor header:

- File > Schema/Component/Function > Property/Parameter

---

## Code Completion

### Context-Aware Completion

**In resource blocks (before `=`):**

- Shows only schema properties for the resource type
- Filters out already-defined properties

**In resource blocks (after `=`):**

- Shows variables, inputs, outputs, resources, components, functions

**General completion:**

- Local declarations
- Imported symbols
- Built-in types

---

## Type Checking

### Undefined Symbol Detection

- **Warning/Error**: `Cannot resolve symbol 'name'`
- Warning when no import candidates exist
- Shows as error when symbol is completely unknown

### Excluded from Validation

- Decorator names (after `@`)
- Schema property definitions (`type propertyName`)
- Array-typed properties (`type[] propertyName`)
- Built-in type names
- Property access chains

---

## Quick Fixes

### Available Quick Fixes

| Quick Fix            | Trigger                | Action                                    |
|----------------------|------------------------|-------------------------------------------|
| Remove unused import | Unused import warning  | Removes import or symbol                  |
| Add import           | Undefined symbol error | Adds import when symbol exists in project |
| Wildcard to Named    | `import *` statement   | Converts `import *` to explicit named imports |
| Optimize imports     | Cmd+Alt+O / Ctrl+Alt+O | Removes all unused imports at once        |

### Add Import Quick Fix

When an undefined symbol is detected, the quick fix searches project files:

- **Availability**: Shows when symbol exists in another file that can be imported
- **Multiple candidates**: If symbol exists in multiple files, offers choice
- **Existing imports**: Adds to existing import from same file (`import a` → `import a, b`)
- **Limitation**: Schema names in type positions are excluded from type checking validation

### Optimize Imports

Removes all unused imports via Cmd+Alt+O (Mac) or Ctrl+Alt+O (Windows/Linux):

- Removes entire import lines when no symbols are used
- Removes individual unused symbols from multi-symbol imports
- Preserves wildcard imports when any exported symbol is used
- Detects usage in string interpolation (`$var` and `${var}`)
- **Sorts imports alphabetically by path** (case-insensitive)
- **Sorts symbols within multi-symbol imports** (`import z, a, m` → `import a, m, z`)

---

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

---

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

---

## Structure View

### View Hierarchy

Shows file structure in tool window with:

- Imports
- Schemas and their properties
- Components and their inputs/outputs
- Functions
- Resources
- Variables

### Icons

| Element   | Color      | Letter |
|-----------|------------|--------|
| Resource  | Purple     | R      |
| Component | Blue       | C      |
| Schema    | Green      | S      |
| Function  | Orange     | F      |
| Type      | Blue       | T      |
| Variable  | Purple     | V      |
| Input     | Amber      | I      |
| Output    | Lime       | O      |
| Import    | Brown      | M      |
| Property  | Cornflower | P      |

---

## Formatting

### Features

- Automatic indentation
- Property alignment in schemas and resources
- Configurable indent size (default: 2 spaces)
- Continuation indent for wrapped lines (default: 4 spaces)

---

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

---

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

---

## Refactoring

### Rename (F2)

Rename identifiers with automatic reference updates:

- Variables, inputs, outputs
- Functions
- Schemas, components
- Function parameters
- In-place editing with preview

### Extract Variable (Cmd+Alt+V / Ctrl+Alt+V)

Extract selected expression into a new variable:

```kite
// Before: select "a + b"
var result = a + b * 2

// After:
var sum = a + b
var result = sum * 2
```

**Features:**
- Extracts literals (string, number, boolean)
- Extracts binary expressions (+, -, *, /)
- Extracts function calls
- Extracts property access
- Option to replace all occurrences
- Preserves indentation
- Places declaration before usage

---

## Implementation Files

| Feature                    | Main Implementation                                |
|----------------------------|----------------------------------------------------|
| Import Resolution          | `reference/KiteImportHelper.java`                  |
| Broken Import Detection    | `highlighting/KiteTypeCheckingAnnotator.java`      |
| Unused Import Detection    | `highlighting/KiteUnusedImportAnnotator.java`      |
| Duplicate Import Detection | `highlighting/KiteDuplicateImportAnnotator.java`   |
| Remove Import Quick Fix    | `quickfix/RemoveUnusedImportQuickFix.java`         |
| Add Import Quick Fix       | `quickfix/AddImportQuickFix.java`                  |
| Optimize Imports           | `imports/KiteImportOptimizer.java`                 |
| Navigation                 | `navigation/KiteGotoDeclarationHandler.java`       |
| Go to Class                | `navigation/KiteGotoClassContributor.java`         |
| Go to Symbol               | `navigation/KiteGotoSymbolContributor.java`        |
| Code Completion            | `completion/KiteCompletionContributor.java`        |
| Import Path Completion     | `completion/KiteImportPathCompletionProvider.java` |
| Inlay Hints                | `hints/KiteInlayHintsProvider.java`                |
| Quick Documentation        | `documentation/KiteDocumentationProvider.java`     |
| Structure View             | `structure/KiteStructureViewElement.java`          |
| Formatter                  | `formatter/KiteBlock.java`                         |
| Code Folding               | `KiteFoldingBuilder.java`                          |
| Smart Enter                | `editor/KiteEnterHandlerDelegate.java`             |
| Extract Variable           | `refactoring/KiteIntroduceVariableHandler.java`    |
| Refactoring Support        | `refactoring/KiteRefactoringSupportProvider.java`  |

## Test Files

| Feature                | Test File                                |
|------------------------|------------------------------------------|
| Import Resolution      | `AddImportQuickFixTest.java`             |
| Broken Imports         | `KiteBrokenImportAnnotatorTest.java`     |
| Unused Imports         | `KiteUnusedImportAnnotatorTest.java`     |
| Duplicate Imports      | `KiteDuplicateImportAnnotatorTest.java`  |
| Remove Import Fix      | `RemoveUnusedImportQuickFixTest.java`    |
| Add Import Fix         | `AddImportIntentionTest.java`            |
| Optimize Imports       | `KiteImportOptimizerTest.java`           |
| Import Folding         | `KiteFoldingBuilderTest.java`            |
| Wildcard to Named      | `WildcardToNamedImportQuickFixTest.java` |
| Import Path Completion | `KiteImportPathCompletionTest.java`      |
| Code Folding           | `KiteFoldingBuilderTest.java`            |
| Smart Enter            | `KiteEnterHandlerDelegateTest.java`      |
| Extract Variable       | `KiteExtractVariableTest.java`           |
| Refactoring            | `KiteRefactoringTest.java`               |
| Go to Class/Symbol     | `KiteGotoContributorTest.java`           |
