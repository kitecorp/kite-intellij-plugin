# Kite IntelliJ Plugin - Todo List

## Test Gaps

All test gaps have been addressed! See Completed section below.

---

## Skipped/Deferred Features

> **Check this section first before suggesting other work.**

These features were considered but deferred for later implementation:

- [x] **Extract Variable Refactoring** - Select expression, create variable (21 tests)
- [ ] **Extract Function Refactoring** - Select statements, create function with parameters
- [ ] **Inline Variable Refactoring** - Remove variable, substitute value everywhere

---

## New Features

### High Priority - Inspections Framework ✅ ALL COMPLETED (12 inspections, 177 tests)

#### Phase 1: Foundation ✅ COMPLETED
- [x] Create `inspection/` package
- [x] Create `KiteInspectionBase.java` abstract class
- [x] Update plugin.xml with inspection group
- [x] Create `KiteInspectionTestBase.java` for tests

#### Phase 2: Tier 1 - High Value ✅ COMPLETED
- [x] `KiteUnusedVariableInspection` + tests (23 tests)
- [x] `KiteUnusedInputOutputInspection` + tests (15 tests)
- [x] `KiteUnreachableCodeInspection` + tests (16 tests)

#### Phase 3: Tier 2 - Type Safety ✅ COMPLETED
- [x] `KiteMissingPropertyInspection` + tests (16 tests)
- [x] `KiteUnknownDecoratorInspection` + tests (16 tests)

#### Phase 4: Tier 3 - Code Quality ✅ COMPLETED
- [x] `KiteCircularImportInspection` + tests (8 tests)
- [x] `KiteShadowedVariableInspection` + tests (14 tests)
- [x] `KiteUnusedParameterInspection` + tests (13 tests)
- [x] `KiteEmptyBlockInspection` + tests (18 tests)

#### Phase 5: Tier 4 - Style ✅ COMPLETED
- [x] `KiteNamingConventionInspection` + tests (16 tests)
- [x] `KiteLargeFunctionInspection` + tests (10 tests)
- [x] `KiteDeepNestingInspection` + tests (12 tests)

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

### Test Coverage (All Done)

#### Medium Priority Tests (Previously Completed)
- [x] KiteReferenceTest - Reference resolution
- [x] KiteReferenceContributorTest - Reference registration
- [x] KiteInlayHintsProviderTest - Inlay hints
- [x] KiteImportHelperTest - Import utilities
- [x] KiteParameterInfoHandlerTest - Parameter info
- [x] KiteDocumentationProviderTest - Quick docs
- [x] KiteBreadcrumbsProviderTest - Breadcrumb navigation
- [x] KiteFormatterTest - Formatter/indentation (51 tests)

#### Test Gaps (Now Completed - 174 tests)
- [x] KiteStructureViewTest - Structure view elements, model, icons (33 tests)
- [x] KiteAnnotatorTest - Semantic highlighting, type coloring, interpolation (32 tests)
- [x] KiteCommenterTest - Comment syntax configuration (9 tests)
- [x] KiteQuoteHandlerTest - Quote handling and strings (14 tests)
- [x] KiteLineMarkerProviderTest - Gutter icons on declarations (15 tests)
- [x] KiteNamesValidatorTest - Identifier validation, keywords (57 tests)
- [x] KiteRefactoringTest - Rename support, declarations (14 tests)

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

- **Main Java files:** 84 (includes 13 inspection files + base class)
- **Test files:** 55 (includes 13 inspection test files + base class)
- **Total tests:** 475+ tests across all test files
- **Inspection tests:** 177 tests (12 inspections)

---

## Documentation (Low Priority - Do Last)

- [ ] **Architecture Guide** - How lexer → parser → PSI → annotators work
- [ ] **Contributing Guide** - Setup for new developers
- [ ] **Debugging Guide** - How to debug the plugin, PSI viewer
- [ ] **Test Patterns Guide** - How to write tests for various features
