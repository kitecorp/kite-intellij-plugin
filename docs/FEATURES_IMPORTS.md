# Import System

[Back to Features Index](Features.md)

## Supported Import Syntax

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

## Importable Types

The following can be imported from other files:

| Type                | Example                         | Description                  |
|---------------------|---------------------------------|------------------------------|
| Variables           | `var region = "us-east-1"`      | Global variable declarations |
| Functions           | `fun formatName(...) { }`       | Function definitions         |
| Schemas             | `schema Config { }`             | Schema type definitions      |
| Components          | `component WebServer { }`       | Component definitions        |
| Resource Instances  | `resource Config myDb { }`      | Instantiated resources       |
| Component Instances | `component Server myServer { }` | Instantiated components      |

## Import Path Resolution

Imports are resolved in the following order (see `KiteImportHelper.resolveFilePath()`):

1. **Relative to containing file** - `"common.kite"` looks in same directory
2. **Project base path** - Searches from project root
3. **Project-local providers** - `.kite/providers/` directory
4. **User-global providers** - `~/.kite/providers/` directory
5. **Package-style paths** - `"aws.DatabaseConfig"` resolves to `aws/DatabaseConfig.kite`

## Import Validation

### Broken Import Detection

- **Error**: `Cannot resolve import path 'nonexistent.kite'`
- Triggered when the import path points to a file that doesn't exist
- Implemented in: `KiteTypeCheckingAnnotator`
- Test file: `KiteBrokenImportAnnotatorTest`

### Empty Import Path

- **Error**: `Empty import path`
- Triggered when import path is an empty string: `import x from ""`

### Import Ordering

- **Error**: `Import statements must appear at the beginning of the file`
- Imports must come before any other statements (var, fun, schema, resource, component)
- Multiple consecutive imports at the top are allowed

## Unused Import Detection

- **Warning (weak)**: `Unused import 'symbolName'`
- Detects imports that are never referenced in the file
- Recognizes usage in:
    - Direct identifier references
    - String interpolation (`$var` and `${var}`)
    - Type positions (resource types, function parameter types)
- Implemented in: `KiteUnusedImportAnnotator`
- Test file: `KiteUnusedImportAnnotatorTest`

## Duplicate Import Detection

- **Warning**: `'symbolName' is already imported from "path.kite"`
- Detects when the same symbol is imported multiple times:
    - Same symbol from different files
    - Same symbol from the same file (redundant import)
    - Same symbol appearing twice in a single import statement
    - Named import after a wildcard import from the same file
- Implemented in: `KiteDuplicateImportAnnotator`
- Test file: `KiteDuplicateImportAnnotatorTest`

## Remove Unused Import Quick Fix

When an unused import is detected, a quick fix is available:

- **Single symbol import**: "Remove unused import" - removes entire import line
- **Multi-symbol import**: "Remove unused import 'symbolName'" - removes only the unused symbol
- If all symbols in a multi-import are unused, removes the entire line
- Implemented in: `RemoveUnusedImportQuickFix`
- Test file: `RemoveUnusedImportQuickFixTest`

## Optimize Imports

Removes all unused imports via Cmd+Alt+O (Mac) or Ctrl+Alt+O (Windows/Linux):

- Removes entire import lines when no symbols are used
- Removes individual unused symbols from multi-symbol imports
- Preserves wildcard imports when any exported symbol is used
- Detects usage in string interpolation (`$var` and `${var}`)
- **Sorts imports alphabetically by path** (case-insensitive)
- **Sorts symbols within multi-symbol imports** (`import z, a, m` → `import a, m, z`)

## Import Path Completion

When typing inside import strings, autocomplete suggests `.kite` files:

```kite
import alpha from "ga|"  // Press Tab
// Result: import alpha from "gamma.kite"
```

**Features:**

- Suggests all `.kite` files in project
- Relative paths from current file
- **Full string replacement**: Selecting a completion replaces the entire string content, not just the prefix
- Works with both double and single quotes

## Planned Import Features

| Feature                       | Description                                               | Status |
|-------------------------------|-----------------------------------------------------------|--------|
| Import Sorting                | Auto-sort imports alphabetically when optimizing          | Done   |
| Unused Wildcard Warning       | Warn if `import *` doesn't use any symbol from the file   | Done   |
| Import Folding                | Collapse multiple imports into `[3 imports...]` in editor | Done   |
| Wildcard to Named             | Convert `import *` to explicit named imports quick fix    | Done   |
| Import Path Completion        | Autocomplete file paths in import strings                 | Done   |
| Auto-import on Paste          | Alt+Enter to add imports for undefined symbols            | Done   |
| Circular Import Detection     | Warn when files import each other in a cycle              | Done   |
| Import Grouping               | Group imports by category (local vs external)             | Planned|
| Relative Path Suggestions     | Suggest shorter relative paths for imports                | Planned|

## Implementation Files

| Feature                    | File                                               |
|----------------------------|----------------------------------------------------|
| Import Resolution          | `reference/KiteImportHelper.java`                  |
| Broken Import Detection    | `highlighting/KiteTypeCheckingAnnotator.java`      |
| Unused Import Detection    | `highlighting/KiteUnusedImportAnnotator.java`      |
| Duplicate Import Detection | `highlighting/KiteDuplicateImportAnnotator.java`   |
| Remove Import Quick Fix    | `quickfix/RemoveUnusedImportQuickFix.java`         |
| Add Import Quick Fix       | `quickfix/AddImportQuickFix.java`                  |
| Optimize Imports           | `imports/KiteImportOptimizer.java`                 |
| Import Path Completion     | `completion/KiteImportPathCompletionProvider.java` |

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
