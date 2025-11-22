# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an IntelliJ IDEA plugin for **Kite**, an Infrastructure as Code (IaC) programming language designed as an alternative to Terraform.

**Kite Language Overview:**
- Developed by EchoStream SRL (Romania)
- Uses ANTLR4 for parsing (grammar: `Kite.g4`)
- Multi-cloud provisioning (AWS, Azure, GCP)
- File extension: `.kite`
- Two-phase execution model (evaluation → execution, similar to Terraform's plan → apply)

## Kite Language Reference

### Key Language Constructs

The plugin needs to support these primary language features:

**Resource Declarations** - Infrastructure resource definitions
```kite
@provisionOn(["aws"])
resource S3.Bucket photos {
  name = "my-photos-bucket"
  region = "us-east-1"
}
```

**Component Declarations** - Collections of resources with inputs/outputs
```kite
component WebServer api {
  input number port = 8080
  resource VM.Instance server {
    size = "t2.micro"
  }
  output string endpoint = server.publicIp
}
```

**Schema Declarations** - Type definitions for structured data
```kite
schema DatabaseConfig {
  string host
  number port = 5432
  boolean ssl = true
}
```

**Type Declarations** - Union types and type aliases
```kite
type Environment = "dev" | "staging" | "prod"
type Status = "active" | "inactive" | "pending"
```

**Function Declarations** - First-class functions with type signatures
```kite
fun add(number x, number y) number {
  return x + y
}

// Function types: (param1Type, param2Type) -> returnType
type MathOp = (number, number) -> number
```

**Decorators** - 15 built-in decorators for validation and metadata
```kite
@existing        // Reference existing cloud resources
@sensitive       // Mark sensitive data
@dependsOn([resources])
@tags({key: value})
@provisionOn(["aws", "azure"])
@minValue(n), @maxValue(n)
@minLength(n), @maxLength(n)
@validate(regex: "pattern")
@allowed([values])
@unique
@nonEmpty
@description("text")
@count(n)
```

**Import Statements** - File-based code reuse
```kite
import * from "stdlib.kite"
```

**Control Flow** - Blocks required (no braceless if/while)
```kite
// Valid - both with/without parens
if (condition) { doSomething() }
if condition { doSomething() }

while (condition) { body }
while condition { body }

for i in 0..10 { console.log(i) }
```

**Array Comprehensions** - Three distinct forms
```kite
// Form 1: Compact
[for i in 0..10: i * 2]

// Form 2: Block (resource generation)
[for env in environments]
resource Bucket photos {
  name = "photos-${env}"
}

// Form 3: Standalone loop
for i in 0..10 { console.log(i) }
```

### Language Syntax Rules

**Statement Separators**: Newlines (`\n`) OR semicolons (`;`) are interchangeable everywhere
```kite
var x = 1; var y = 2    // Semicolons
var x = 1
var y = 2               // Newlines
```

**Object Literals**: Use colons for properties, commas required between properties, trailing commas allowed
```kite
var config = {
  env: "production",
  port: 8080,
  type: "web",      // Keywords allowed as property names
  features: {
    auth: true,
  },  // Trailing comma OK
}
```

**Keywords as Property Names**: Reserved words can be object keys (e.g., `type`, `for`, `if`)

**Unary Operators**: Prefix and postfix increment/decrement
```kite
++x  // Prefix
x++  // Postfix
--x, x--
```

### Type System

- Strong typing with type inference
- Union types: `type Status = "active" | "inactive"`
- Array types: `[number]`, `[string][]` (multi-dimensional)
- Object types: `object`, `{}`, `object({})`
- Function types: `(number, string) -> boolean`
- Type keywords: `object`, `any`, `number`, `string`, `boolean`, `null`

**Union Type Normalization**: Union types deduplicate by type kind, not literal values
- `type Numbers = 1 | 2 | 3` → normalizes to `number`
- `type Mixed = 1 | "hello" | true` → `boolean | number | string` (alphabetically sorted)

### Grammar Location

The ANTLR4 grammar is located in the Kite language project:
- **Grammar file**: `../kite/lang/src/main/antlr/Kite.g4`
- **Generated parser**: `../kite/lang/build/generated-src/antlr/main/io/kite/syntax/ast/generated/`

The plugin should reference this grammar for syntax highlighting, code completion, and PSI structure.

## Build System

This project uses Gradle with the IntelliJ Platform Gradle Plugin. Common commands:

```bash
# Build the plugin
./gradlew build

# Run the plugin in a sandboxed IDE instance
./gradlew runIde

# Run tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin

# Build plugin distribution
./gradlew buildPlugin
```

The built plugin ZIP will be in `build/distributions/`.

## Plugin Development

### Plugin Descriptor

The plugin configuration is defined in `src/main/resources/META-INF/plugin.xml`. This file declares:
- Plugin metadata (name, version, description)
- Extension points and extensions
- Actions and action groups
- Services and components
- Dependencies on other plugins

### Project Structure

Typical IntelliJ plugin structure:
- `src/main/java/` or `src/main/kotlin/` - Plugin source code
- `src/main/resources/` - Resources including plugin.xml, icons, etc.
- `src/test/` - Test files
- `build.gradle.kts` or `build.gradle` - Build configuration

### Key Plugin Components for Language Support

**Language Definition**: Define the Kite language and file type
- Implement `Language` for `.kite` files
- Create `FileType` for file recognition
- Register file type in plugin.xml

**Lexer/Parser**: Syntax highlighting and code structure
- Use Grammar-Kit or integrate ANTLR4 grammar
- Implement `ParserDefinition`
- Create `KiteTokenTypes` and `KiteElementTypes`

**PSI (Program Structure Interface)**: Code structure and navigation
- Create PSI elements for resources, components, schemas, types, functions
- Implement `PsiElement` hierarchy matching Kite AST
- Support reference resolution (e.g., resource dependencies)

**Syntax Highlighter**: Color coding
- Implement `SyntaxHighlighter` for keywords, strings, decorators, comments
- Define color scheme in `KiteColorSettingsPage`

**Code Completion**: Autocomplete suggestions
- Implement `CompletionContributor` for keywords, decorators, built-in types
- Context-aware completion (e.g., decorator names, resource types)

**Code Folding**: Collapsible code blocks
- Implement `FoldingBuilder` for resource/component/schema bodies, functions, imports

**Formatter**: Code formatting
- Implement `FormattingModelBuilder` for consistent indentation and spacing
- Handle flexible statement separators (newlines vs semicolons)

**Annotator**: Error highlighting and warnings
- Implement `Annotator` for semantic errors (type mismatches, invalid decorators)
- Highlight unresolved references

**Structure View**: File structure outline
- Implement `StructureViewModel` showing resources, components, schemas, functions

**Documentation Provider**: Quick documentation (Ctrl+Q)
- Implement `DocumentationProvider` for decorators, built-in functions, types

### IntelliJ Platform SDK

This plugin uses the IntelliJ Platform SDK. Key concepts:

- **PSI (Program Structure Interface)**: The layer that parses code and creates a syntax tree
- **VirtualFile**: Represents files in the IDE's virtual file system
- **Document**: Text representation of a file
- **Editor**: The component for viewing and editing documents
- **Project/Module**: Organizational structures

When working with PSI or documents, always use read/write actions and invoke on the correct thread (EDT for UI, read action for reading PSI, write action for modifying PSI).

### Testing

Run specific test classes:
```bash
./gradlew test --tests "com.kite.intellij.SpecificTestClass"
```

IntelliJ plugin tests should extend:
- `BasePlatformTestCase` for PSI/language tests
- `LightPlatformCodeInsightFixtureTestCase` for code insight features

### Debugging

The `runIde` task launches a separate IDE instance with the plugin installed. You can attach a debugger to this instance for debugging plugin code.

## Related Kite Language Project

The Kite language implementation is in a sibling directory:
- **Path**: `/Users/mimedia/IdeaProjects/kite`
- **Detailed documentation**: `../kite/lang/CLAUDE.md`
- **Build commands**: See kite project for testing language features

## Platform Version Compatibility

Check `build.gradle` or `build.gradle.kts` for the target IntelliJ platform version and compatibility range. The plugin may need to support multiple IDE versions.