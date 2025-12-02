# Refactoring

[Back to Features Index](Features.md)

## Rename (F2)

Rename identifiers with automatic reference updates:

- Variables, inputs, outputs
- Functions
- Schemas, components
- Function parameters
- In-place editing with preview

## Extract Variable (Cmd+Alt+V / Ctrl+Alt+V)

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

## Implementation Files

| Feature             | File                                             |
|---------------------|--------------------------------------------------|
| Extract Variable    | `refactoring/KiteIntroduceVariableHandler.java`  |
| Refactoring Support | `refactoring/KiteRefactoringSupportProvider.java`|

## Test Files

| Feature          | Test File                    |
|------------------|------------------------------|
| Extract Variable | `KiteExtractVariableTest.java`|
| Refactoring      | `KiteRefactoringTest.java`   |
