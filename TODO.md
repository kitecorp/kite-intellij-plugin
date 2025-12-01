# Kite IntelliJ Plugin - Todo List

## Test Gaps

All test gaps have been addressed! See Completed section below.

---

## New Features

### High Priority - Inspections Framework (19-27 hrs)

#### Phase 1: Foundation (2-3 hrs) ✅ COMPLETED
- [x] Create `inspection/` package
- [x] Create `KiteInspectionBase.java` abstract class
- [x] Update plugin.xml with inspection group
- [x] Create `KiteInspectionTestBase.java` for tests

#### Phase 2: Tier 1 - High Value (5-7 hrs) ✅ COMPLETED
- [x] `KiteUnusedVariableInspection` + tests (23 tests)
- [x] `KiteUnusedInputOutputInspection` + tests (15 tests)
- [x] `KiteUnreachableCodeInspection` + tests (16 tests)

#### Phase 3: Tier 2 - Type Safety (6-8 hrs)
- [ ] `KiteTypeMismatchInspection` + tests
- [ ] `KiteMissingPropertyInspection` + tests
- [ ] `KiteUnknownDecoratorInspection` + tests

#### Phase 4: Tier 3 - Code Quality (4-6 hrs)
- [ ] `KiteCircularImportInspection` + tests
- [ ] `KiteShadowedVariableInspection` + tests
- [ ] `KiteUnusedParameterInspection` + tests
- [ ] `KiteEmptyBlockInspection` + tests

#### Phase 5: Tier 4 - Style (2-3 hrs)
- [ ] `KiteNamingConventionInspection` + tests
- [ ] `KiteLargeFunctionInspection` + tests
- [ ] `KiteDeepNestingInspection` + tests

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

- **Main Java files:** 75 (includes 3 new inspection files)
- **Test files:** 46 (includes 3 new inspection test files)
- **Total tests:** 350+ tests across all test files
- **Inspection tests:** 54 tests (23 + 15 + 16)

---

## Documentation (Low Priority - Do Last)

- [ ] **Architecture Guide** - How lexer → parser → PSI → annotators work
- [ ] **Contributing Guide** - Setup for new developers
- [ ] **Debugging Guide** - How to debug the plugin, PSI viewer
- [ ] **Test Patterns Guide** - How to write tests for various features
