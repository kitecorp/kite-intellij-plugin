# Inspections & Quick Fixes

[Back to Features Index](Features.md)

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

## Indexed Access Validation

Resources and components can be indexed via `@count` decorator or for-loops. The plugin validates indexed access patterns.

### Indexed Resource Creation

| Source | Index Type | Example |
|--------|-----------|---------|
| `@count(n)` decorator | Numeric 0 to n-1 | `@count(3) resource vm server {}` → `server[0..2]` |
| Range loop `for i in 0..n` | Numeric start to end-1 | `for i in 0..5 { resource vm server {} }` → `server[0..4]` |
| Array loop `for x in [...]` | String keys | `for env in ["dev","prod"] { resource vm db {} }` → `db["dev"], db["prod"]` |

### Validation Errors

```kite
// Error: 'server' is not an indexed resource
resource vm server { }
var x = server[0]  // Error

// Error: Index out of bounds
@count(3)
resource vm server { }
var x = server[5]  // Error: Index 5 is out of bounds (valid: 0-2)

// Error: Wrong index type
for env in ["dev", "prod"] {
    resource vm server { }
}
var x = server[0]  // Error: uses string keys, not numeric indices

// Error: Invalid key
for env in ["dev", "prod"] {
    resource vm server { }
}
var x = server["staging"]  // Error: "staging" is not valid
```

**Skip Validation:**
- Variable indices like `server[i]` are skipped (only literal values are validated)
- Expression indices like `server[count-1]` are skipped

**Implementation:**
- File: `highlighting/KiteTypeCheckingAnnotator.java`
- Helper: `util/KiteIndexedResourceHelper.java`
- Test: `highlighting/KiteTypeCheckingAnnotatorTest.java`

---

## Missing Required Properties

A schema property is **required** if it has no default value AND is not marked with `@cloud`. Resource instances must provide all required properties.

```kite
schema DatabaseConfig {
    string host           // Required - no default value
    number port = 5432    // Optional - has default value
    @cloud string arn     // Optional - set by cloud provider
}

resource DatabaseConfig myDb {
    host = "localhost"    // OK - required property provided
    // port not needed (has default)
    // arn not needed (cloud-provided)
}
```

**Detection:**
- **Warning**: `Missing required property 'propertyName'`
- Shows on the resource instance name when required properties are missing
- Implemented in: `KiteMissingPropertyInspection`
- Test file: `KiteMissingPropertyInspectionTest`

**Optional Properties:**
- Properties with default values (`= value`)
- Properties with `@cloud` decorator (set by cloud provider)

## Quick Fixes

### Available Quick Fixes

| Quick Fix                | Trigger                   | Action                                        |
|--------------------------|---------------------------|-----------------------------------------------|
| Remove unused import     | Unused import warning     | Removes import or symbol                      |
| Add import               | Undefined symbol error    | Adds import when symbol exists in project     |
| Wildcard to Named        | `import *` statement      | Converts `import *` to explicit named imports |
| Optimize imports         | Cmd+Alt+O / Ctrl+Alt+O    | Removes all unused imports at once            |
| Add missing property     | Missing property warning  | Adds property with type-appropriate default   |

### Add Import Quick Fix

When an undefined symbol is detected, the quick fix searches project files:

- **Availability**: Shows when symbol exists in another file that can be imported
- **Multiple candidates**: If symbol exists in multiple files, offers choice
- **Existing imports**: Adds to existing import from same file (`import a` → `import a, b`)
- **Limitation**: Schema names in type positions are excluded from type checking validation

### Add Missing Property Quick Fix

When a resource is missing required schema properties, this quick fix adds them:

```kite
// Before applying quick fix
schema Config {
    string host
    number port
}
resource Config server {
}  // Warning: Missing required property 'host'

// After applying "Add missing property 'host'" quick fix
resource Config server {
    host = ""  // Added with type-appropriate default
}
```

**Type-Appropriate Defaults:**

| Type      | Default Value |
|-----------|---------------|
| string    | `""`          |
| number    | `0`           |
| boolean   | `false`       |
| any       | `null`        |
| type[]    | `[]`          |
| custom    | `{}`          |

## Implementation Files

| Feature                    | File                                             |
|----------------------------|--------------------------------------------------|
| Type Checking              | `highlighting/KiteTypeCheckingAnnotator.java`    |
| Missing Property Inspection| `inspection/KiteMissingPropertyInspection.java`  |
| Add Import Quick Fix       | `quickfix/AddImportQuickFix.java`                |
| Add Missing Property Fix   | `quickfix/AddRequiredPropertyQuickFix.java`      |

## Test Files

| Feature              | Test File                                |
|----------------------|------------------------------------------|
| Missing Property     | `KiteMissingPropertyInspectionTest.java` |
| Add Missing Property | `AddRequiredPropertyQuickFixTest.java`   |
