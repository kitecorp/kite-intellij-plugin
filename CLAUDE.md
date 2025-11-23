# Kite IntelliJ Plugin - Development Notes

## Structure View Icons

### Overview
Custom circular icons with centered letters for each Kite language construct type in the Structure View.

### Implementation
- **File**: `src/main/java/io/kite/intellij/structure/KiteStructureViewIcons.java`
- **Integration**: `src/main/java/io/kite/intellij/structure/KiteStructureViewElement.java`

### Icon Design
- 16x16 pixel circular icons
- 1-pixel border in construct-specific color
- Monospaced font (10pt, bold) for uniform letter appearance
- Precise centering using `Rectangle2D` bounds for cross-platform consistency

### Icon Types
| Construct | Letter | Color (RGB) |
|-----------|--------|-------------|
| Resource | R | Purple (177, 80, 243) |
| Component | C | Blue (33, 150, 243) |
| Schema | S | Green (94, 176, 39) |
| Function | F | Orange (255, 152, 0) |
| Type | T | Blue (54, 120, 244) |
| Variable | V | Purple (155, 101, 246) |
| Input | I | Yellow (255, 193, 7) |
| Output | O | Yellow (255, 193, 7) |
| Import | M | Brown (119, 78, 44) |

### Technical Details
- Uses Java2D `Graphics2D` for rendering
- Anti-aliasing enabled for smooth text and shapes
- Float-based calculations for precise positioning across different screen densities
- Text positioning formula: `textY = y + (ICON_SIZE - textHeight) / 2.0f - (float) bounds.getY()`
  - Uses actual text bounds from `FontMetrics.getStringBounds()` for pixel-perfect centering
  - Works consistently across all screen resolutions and DPI settings

### Key Files Modified
1. `KiteStructureViewIcons.java` - Icon generation and rendering
2. `KiteStructureViewElement.java` - Integration with Structure View
   - `getIconForElement()` - Maps element types to icons
   - `getPresentation()` - Wraps presentations to inject custom icons

## Comment/Uncomment Actions

### Overview
Support for toggling line and block comments using standard IDE keyboard shortcuts (Cmd+/ or Ctrl+/).

### Implementation
- **File**: `src/main/java/io/kite/intellij/KiteCommenter.java`
- **Registration**: Registered in `plugin.xml` as `lang.commenter` extension

### Comment Syntax
- **Line comments**: `//` - Comments from `//` to end of line
- **Block comments**: `/* */` - Multi-line comments

### Features
- Toggle line comment with Cmd+/ (Mac) or Ctrl+/ (Windows/Linux)
- Toggle block comment with Cmd+Shift+/ (Mac) or Ctrl+Shift+/ (Windows/Linux)
- Works on single lines or multiple selected lines
- Automatically handles indentation

### Implementation Details
Implements the `com.intellij.lang.Commenter` interface with:
- `getLineCommentPrefix()` - Returns "//"
- `getBlockCommentPrefix()` - Returns "/*"
- `getBlockCommentSuffix()` - Returns "*/"

## Code Folding

### Overview
Code folding support allows collapsing and expanding code sections for better readability and navigation.

### Implementation
- **File**: `src/main/java/io/kite/intellij/KiteFoldingBuilder.java`
- **Registration**: Registered in `plugin.xml` as `lang.foldingBuilder` extension

### Foldable Elements
- **Resource declarations** - Collapse resource blocks
- **Component declarations** - Collapse component blocks with nested content
- **Schema declarations** - Collapse schema blocks
- **Function declarations** - Collapse function blocks
- **For statements** - Collapse for loop blocks
- **While statements** - Collapse while loop blocks
- **Object literals** - Collapse object literals `{key: value, ...}` in any context (property values, decorator arguments, etc.)
- **Block comments** - Collapse multi-line comments (`/* */`)

### Features
- Click the folding markers in the editor gutter to collapse/expand
- Only regions spanning multiple lines are foldable
- Collapsed regions show placeholder text (e.g., `{...}` for blocks, `/*...*/` for comments)
- Nothing is collapsed by default

### Implementation Details
Extends `com.intellij.lang.folding.FoldingBuilderEx` and implements:
- `buildFoldRegions()` - Identifies foldable regions by traversing the PSI tree
- `getPlaceholderText()` - Returns placeholder text for collapsed regions
- `isCollapsedByDefault()` - Returns `false` to keep all regions expanded initially

## Code Formatting

### Overview
Automatic code formatting with the 'Reformat Code' action (Cmd+Alt+L on Mac, Ctrl+Alt+L on Windows/Linux).

### Implementation
- **Main File**: `src/main/java/io/kite/intellij/formatter/KiteFormattingModelBuilder.java`
- **Block Structure**: `src/main/java/io/kite/intellij/formatter/KiteBlock.java`
- **Registration**: Registered in `plugin.xml` as `lang.formatter` extension

### Formatting Rules

#### Spacing
- **Space before opening brace**: `resource Type name {` (1 space before `{`)
- **Space after keywords**: All keywords (resource, component, schema, fun, var, if, etc.) followed by 1 space
- **Assignment operators**: Space around `=`, `+=`, `-=`, `*=`, `/=`
- **Arithmetic operators**: Space around `+`, `-`, `*`, `/`, `%`
- **Relational operators**: Space around `<`, `>`, `<=`, `>=`, `==`, `!=`
- **Logical operators**: Space around `&&`, `||`; no space before `!`
- **Other operators**:
  - Arrow (`->`) has 1 space on each side
  - Range (`..`) has no spaces
  - Union (`|`) has 1 space on each side
- **Brackets**: No spaces inside `[]` brackets
- **Parentheses**: No spaces inside `()` parentheses
- **Comma**: Space after `,`, no space before
- **Colon**: Space after `:`, no space before (for property assignments)
- **Semicolon**: Space after `;`
- **No space after `@`**: Decorators use `@decorator` (no space between `@` and name)
- **No space around dots**: Member access uses `object.property` (no spaces)

#### Indentation
- **Block elements** indent their content with normal indentation (4 spaces by default)
- **Indented elements**:
  - Content inside resource/component/schema declarations
  - Content inside function bodies
  - Content inside for/while loop bodies
  - Content inside object literals
  - Content inside array literals
- **Braces** (`{` and `}`) remain at the parent indentation level
- **Continuation indentation** for wrapped function parameters

#### Alignment
- **Object literals**: Colons in object property assignments are vertically aligned
  ```kite
  @tags({
    Environment : "production",
    ManagedBy   : "kite",
    CostCenter  : "engineering"
  })
  ```
- **Decorator arguments**: Colons in named decorator arguments are vertically aligned
  ```kite
  @validate(
    regex : "^[a-z0-9-]+$",
    flag  : 'i'
  )
  ```
- **Resource/Component/Schema blocks**: Assignment operators (`=`) are vertically aligned within groups
  ```kite
  component WebServer api {
    input number port = 8080
    input string size = "t2.micro"

    var x  = 1
    var yp = 2

    output string endpoint = server.publicIp
  }
  ```
- **Grouping logic**: Consecutive lines of the same type align together
  - Consecutive `input` declarations align with each other
  - Consecutive `output` declarations align with each other
  - Consecutive `var` declarations align with each other
  - Groups are separated by blank lines or different statement types
- **How it works**: The formatter identifies groups of similar consecutive declarations, finds the longest property name in each group, and adds padding so alignment happens within each group independently
- This makes all structured code more readable and easier to scan while respecting logical separation

### Key Files
1. `KiteFormattingModelBuilder.java` - Main formatter entry point, defines spacing rules via `SpacingBuilder`
2. `KiteBlock.java` - Represents hierarchical formatting blocks, manages indentation and child block creation

### Features
- Format entire file with Cmd+Alt+L (Mac) or Ctrl+Alt+L (Windows/Linux)
- Format selected text only by selecting code first, then using the reformat shortcut
- Consistent spacing around keywords, operators, and delimiters
- Proper indentation for nested structures
- Preserves semantic meaning while improving readability
