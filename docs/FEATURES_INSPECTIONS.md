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

## Missing Required Properties

Schema properties without a default value are **required**. Resource instances must provide all required properties.

```kite
schema DatabaseConfig {
    string host           // Required - no default value
    number port = 5432    // Optional - has default value
}

resource DatabaseConfig myDb {
    host = "localhost"    // OK - required property provided
    // port not needed (has default)
}
```

**Detection:**
- **Warning**: `Missing required property 'propertyName'`
- Shows on the resource instance name when required properties are missing
- Implemented in: `KiteMissingPropertyInspection`
- Test file: `KiteMissingPropertyInspectionTest`

**Optional Properties:**
- Properties with default values (`= value`)

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
