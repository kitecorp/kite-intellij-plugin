# Minimal Refactoring Plan: KiteCompletionContributor.java

## Current State
- **File**: `src/main/java/cloud/kitelang/intellij/completion/KiteCompletionContributor.java`
- **Current size**: 1541 lines
- **Target reduction**: ~200-300 lines

## Summary of Changes

| Change | Lines Removed | Risk |
|--------|---------------|------|
| Remove dead code `findDotBeforePosition()` | ~19 lines | None |
| Remove duplicate `isBeforeAssignment()` | ~43 lines | None |
| Remove duplicate `isInsideBraces()` | ~23 lines | None |
| Use `KiteCompletionHelper` whitespace utilities | ~22 lines | Low |
| **Total** | **~107 lines** | Low |

**Note**: The initial estimate of ~200-300 lines was optimistic. After detailed analysis, the safe minimal changes yield ~107 lines. The three property collection methods (`visitPropertiesInContext`, `collectPropertiesFromObjectLiteral`, `collectPropertiesFromDeclaration`) are **not** true duplicates - they have different semantics and visitor signatures, so consolidating them would require restructuring.

---

## Step-by-Step Plan

### Step 1: Remove Dead Code - `findDotBeforePosition()` (Lines 1055-1076)

**Analysis**: 
- `findDotBeforePosition()` at line 1058 is **never called** anywhere in the codebase
- `findPreviousDot()` at line 699 is the method that IS used (called at line 677)
- These methods are functionally identical

**Action**: Delete lines 1055-1076 (the `findDotBeforePosition` method including its Javadoc)

**Lines removed**: ~22 lines

---

### Step 2: Remove Duplicate `isBeforeAssignment()` (Lines 1249-1297)

**Analysis**:
- `KiteCompletionContributor.isBeforeAssignment()` at line 1255 is **never called** within the file
- The only usage in this file is `isAfterAssignment()` at line 417, which is a **different method** (returns opposite boolean)
- `KiteResourceCompletionProvider.isBeforeAssignment()` at line 131 is `static` and already used externally
- `KiteComponentDefinitionCompletionProvider.isBeforeAssignment()` at line 347 has slightly different logic (checks next sibling, not previous)

**Action**: Delete lines 1249-1297 (the `isBeforeAssignment` method including its Javadoc comment)

**Lines removed**: ~49 lines (including Javadoc at 1249-1254)

---

### Step 3: Remove Duplicate `isInsideBraces()` (Lines 1222-1247)

**Analysis**:
- `KiteCompletionContributor.isInsideBraces()` at line 1225 is **never called** within the file
- `KiteResourceCompletionProvider.isInsideBraces()` at line 101 is `static` and available for reuse
- The implementations are identical

**Action**: Delete lines 1222-1247 (the `isInsideBraces` method including its Javadoc and section comment)

**Lines removed**: ~26 lines

---

### Step 4: Replace Local Whitespace Utilities with KiteCompletionHelper

**Analysis**:
`KiteCompletionContributor` has these local methods (lines 1180-1202):
- `skipWhitespaceBackward()` - identical to `KiteCompletionHelper.skipWhitespaceBackward()`
- `skipWhitespaceForward()` - identical to `KiteCompletionHelper.skipWhitespaceForward()`
- `isWhitespace()` - identical to `KiteCompletionHelper.isWhitespace()`

**Action**:
1. Add import: `import static cloud.kitelang.intellij.completion.KiteCompletionHelper.*;`
2. Delete lines 1180-1202 (the three whitespace helper methods)

**Lines removed**: ~22 lines

---

## Implementation Order

1. **Step 4 first** - Replace whitespace utilities with static imports (safest, enables verification)
2. **Step 1** - Remove `findDotBeforePosition()` dead code
3. **Step 2** - Remove unused `isBeforeAssignment()`
4. **Step 3** - Remove unused `isInsideBraces()`

This order allows incremental testing after each step.

---

## Verification Steps

After each step, run:
```bash
./gradlew clean compileJava
```

After all steps, run:
```bash
./gradlew runIde
```

Then test completion in:
1. Top-level context (keywords, identifiers)
2. Property access context (`server.`)
3. Type context (after `var`)

---

## What NOT to Change (Considered but Rejected)

### Property Collection Methods
The three methods have different purposes:
- `visitPropertiesInContext()` - Returns property **values** for chained navigation
- `collectPropertiesFromObjectLiteral()` - Collects from `{...}` literals
- `collectPropertiesFromDeclaration()` - Collects from declarations with special component handling

Consolidating these would require:
- Creating a unified visitor interface
- Adding mode flags or strategy pattern
- Risk of breaking existing behavior

**Decision**: Not worth the risk for ~60-80 lines savings.

### `isAfterAssignment()` vs `isBeforeAssignment()`
These return **opposite** booleans and serve different call sites:
- `isAfterAssignment()` - Used for checking if in value position (returns true when AFTER `=`)
- `isBeforeAssignment()` - Used for checking if in property name position (returns true when BEFORE `=`)

While logically `isAfterAssignment() == !isBeforeAssignment()`, keeping both avoids confusion at call sites.

**Decision**: Keep `isAfterAssignment()` as-is. Only remove the **unused** `isBeforeAssignment()` copy.

---

## Critical Files for Implementation

1. **`src/main/java/cloud/kitelang/intellij/completion/KiteCompletionContributor.java`** - Main file to modify
2. **`src/main/java/cloud/kitelang/intellij/completion/KiteCompletionHelper.java`** - Already has whitespace utilities to reuse
3. **`src/main/java/cloud/kitelang/intellij/completion/KiteResourceCompletionProvider.java`** - Reference for `isBeforeAssignment()` and `isInsideBraces()` implementations
4. **`examples/simple.kite`** - Test file for verification

---

## Expected Final Result

- **Lines removed**: ~107 lines
- **New file size**: ~1434 lines
- **Risk level**: Low (removing only dead/duplicate code)
- **Behavioral changes**: None
