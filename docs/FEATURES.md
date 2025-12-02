# Kite IntelliJ Plugin - Feature Documentation

## Feature Categories

| Category | Description | File |
|----------|-------------|------|
| [Import System](FEATURES_IMPORTS.md) | Import syntax, validation, unused detection, quick fixes | `FEATURES_IMPORTS.md` |
| [Navigation & Structure](FEATURES_NAVIGATION.md) | Go to declaration, find usages, structure view | `FEATURES_NAVIGATION.md` |
| [Editor Features](FEATURES_EDITOR.md) | Completion, hints, documentation, formatting, folding | `FEATURES_EDITOR.md` |
| [Inspections & Quick Fixes](FEATURES_INSPECTIONS.md) | Type checking, missing properties, quick fixes | `FEATURES_INSPECTIONS.md` |
| [Refactoring](FEATURES_REFACTORING.md) | Rename, extract variable | `FEATURES_REFACTORING.md` |

---

## Quick Reference

### Keyboard Shortcuts

| Action | Mac | Windows/Linux |
|--------|-----|---------------|
| Go to Class | `Cmd+O` | `Ctrl+N` |
| Go to Symbol | `Cmd+Alt+O` | `Ctrl+Alt+Shift+N` |
| Go to Declaration | `Cmd+Click` | `Ctrl+Click` |
| Quick Documentation | `F1` | `Ctrl+Q` |
| Rename | `F2` | `F2` |
| Extract Variable | `Cmd+Alt+V` | `Ctrl+Alt+V` |
| Optimize Imports | `Cmd+Alt+O` | `Ctrl+Alt+O` |
| Collapse/Expand | `Cmd+-/+` | `Ctrl+-/+` |

### Kite Language Syntax

```kite
// Imports
import defaultRegion from "common.kite"
import alpha, beta from "common.kite"
import * from "common.kite"

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
    host = "localhost"
    port = 5433
}

// Variable declarations
var string explicitType = "hello"
var inferredType = "world"

// Function declaration
fun calculateCost(number instances, string name) number {
    var baseCost = 0.10
    return instances * baseCost
}

// Decorators
@provisionOn(["aws"])
@tags({Environment: "production"})
resource VM.Instance server { }

// Component with inputs/outputs
component WebServer {
    input string port = "8080"
    output string endpoint = "http://localhost:${port}"
}
```

---

## Implementation Files Summary

| Category | Key Files |
|----------|-----------|
| Import System | `KiteImportHelper`, `KiteUnusedImportAnnotator`, `KiteImportOptimizer` |
| Navigation | `KiteGotoDeclarationHandler`, `KiteGotoClassContributor`, `KiteGotoSymbolContributor` |
| Editor | `KiteCompletionContributor`, `KiteInlayHintsProvider`, `KiteDocumentationProvider` |
| Inspections | `KiteTypeCheckingAnnotator`, `KiteMissingPropertyInspection` |
| Refactoring | `KiteIntroduceVariableHandler`, `KiteRefactoringSupportProvider` |

See individual feature files for complete implementation and test file listings.
