# Kite IntelliJ Plugin - Todo List

## Test Gaps

### High Priority

- [ ] **Structure View Tests** (2-3 hrs)
  - `KiteStructureViewElement` - tree hierarchy, element presentation
  - `KiteStructureViewModel` - model behavior
  - `KiteStructureViewIcons` - icon colors for each element type
  - Test navigation from structure view to source

- [ ] **Semantic Annotator Tests** (2-3 hrs)
  - `KiteAnnotator.java` - type identifier highlighting (blue coloring)
  - `any` keyword coloring as TYPE_NAME
  - String interpolation pattern detection (`${var}` and `$var`)
  - Decorator highlighting
  - Property name vs value distinction

### Medium Priority

- [ ] **Editor Component Tests** (4-5 hrs)
  - `KiteEnterHandlerDelegate.java` - smart enter in blocks, strings
  - `KiteQuoteHandler.java` - quote pairing, escaping
  - `KiteCommenter.java` - comment/uncomment insertion/removal

- [ ] **Line Marker Tests** (2 hrs)
  - `KiteLineMarkerProvider.java` - gutter icons on declarations
  - Usage count display
  - Navigation to usages from marker click

- [ ] **Refactoring Tests** (3-4 hrs)
  - `KiteRefactoringSupportProvider.java` - rename support
  - `KiteNamesValidator.java` - identifier validation
  - `KiteRenameHandler.java` - custom rename handling
  - Edge cases: empty names, keywords, special chars

---

## New Features

### High Priority

- [ ] **Inspections Framework** (12-30 hrs)
  - Unused variables warning
  - Type mismatches inspection
  - Unreachable code (after return)
  - Missing function parameters
  - Circular imports detection
  - Register as `<localInspection>` in plugin.xml

### Medium-High Priority

- [ ] **Custom Refactorings** (8-12 hrs)
  - Extract Variable - select expression, create variable
  - Extract Function - select statements, create function
  - Inline Variable - remove var, substitute everywhere

### Medium Priority

- [ ] **Live Templates Expansion** (1-2 hrs)
  - `dec` - Decorator block (`@name(...)`)
  - `prop` - Schema property with type (`type propName`)
  - `enum` - Type alias for union types
  - `arr` - Array literal `[ ]`
  - `obj` - Object literal `{ key: value }`

- [ ] **Code Style Settings** (3-4 hrs)
  - Brace style options (same line vs new line)
  - Indentation inside braces
  - Spacing rules configuration
  - Import sorting preferences

---

## Quick Wins

| Item | Time | Value |
|------|------|-------|
| Live Templates | 1-2 hrs | Immediate productivity |
| Structure View Tests | 2-3 hrs | Completes Tier 3 testing |

---

## Completed

### DRY/CLEAN Code Refactoring (All Done)

- [x] `isWhitespace` consolidated across 5 files
- [x] `isInsideImport`/`isInsideImportStatement` refactored
- [x] `isInsideBraces` consolidated from 3 files into `KitePsiUtil`
- [x] Eliminated `KiteCompletionHelper` wrapper class (4 callers migrated to `KitePsiUtil`)
- [x] `extractSchemaProperties` already consolidated in `KiteSchemaHelper`
- [x] Removed duplicate `findDeclaration` from `KiteStringInterpolationReference`
- [x] Removed duplicate `findDeclarationName` + `findComponentName` from `KiteDuplicateDeclarationAnnotator`
- [x] Reviewed util package - all 7 helpers have distinct responsibilities
- [x] Reviewed completion providers - using shared helpers properly

### Test Coverage (Medium Priority - Done)

- [x] KiteReferenceTest - Reference resolution
- [x] KiteReferenceContributorTest - Reference registration
- [x] KiteInlayHintsProviderTest - Inlay hints
- [x] KiteImportHelperTest - Import utilities
- [x] KiteParameterInfoHandlerTest - Parameter info
- [x] KiteDocumentationProviderTest - Quick docs
- [x] KiteBreadcrumbsProviderTest - Breadcrumb navigation
- [x] KiteFormatterTest - Formatter/indentation (51 tests)

---

## Notes

### Centralized Utility Classes

| Class | Purpose |
|-------|---------|
| `KitePsiUtil` | Low-level PSI navigation (whitespace, siblings, element types) |
| `KiteDeclarationHelper` | Declaration finding and collection |
| `KitePropertyHelper` | Property collection from declarations/objects |
| `KiteSchemaHelper` | Schema property extraction and lookup |
| `KiteIdentifierContextHelper` | Identifier context analysis |
| `KiteImportValidationHelper` | Import statement validation |
| `KiteTypeInferenceHelper` | Type inference and compatibility checking |

### Codebase Metrics

- **Main Java files:** 72
- **Test files:** 34 (47% test-to-code ratio)
- **Lines of code:** 21,549 main / 9,845 test

---

## Documentation (Low Priority - Do Last)

- [ ] **Architecture Guide** - How lexer → parser → PSI → annotators work
- [ ] **Contributing Guide** - Setup for new developers
- [ ] **Debugging Guide** - How to debug the plugin, PSI viewer
- [ ] **Test Patterns Guide** - How to write tests for various features
