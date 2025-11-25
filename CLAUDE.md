# Kite IntelliJ Plugin - Development Notes

## Development Best Practices

### Problem-Solving Approach
When implementing new features or fixing bugs, follow these principles to avoid overthinking:

1. **Apply existing patterns first** - Before creating new complex solutions, look for similar working patterns in the codebase
   - Example: VAR declaration alignment was solved by applying the same pattern used for INPUT/OUTPUT declarations (just 3 lines in `buildFileChildren()`)
   - Don't create recursive schema flattening when simple declaration inlining already works

2. **Ask questions whenever you feel like it** - When unsure about requirements, approach, or implementation details, ask before coding
   - Clarify ambiguity upfront rather than making assumptions
   - Better to ask and get it right than to overthink and guess wrong

3. **Listen to user hints immediately** - When the user suggests a specific approach, try it first before exploring alternatives
   - User hints are often based on knowing what already works
   - Direct suggestions like "use the same algorithm that you do for input/output" should be tried first

4. **Debug actual problems, not assumed problems** - Focus on what's actually broken
   - If code isn't working, don't assume plugin loading issues - check the actual code logic first
   - Use `gradle runIde` to test changes and verify the actual behavior

5. **Prefer simple solutions** - The simplest solution is often correct
   - If a fix requires more than a few lines, reconsider the approach
   - Complexity should match the problem - don't over-engineer

6. **When stuck, step back** - If struggling for more than a few attempts:
   - Look at similar working code
   - Ask: "What's the simplest change that could work?"
   - Consider that you might be solving the wrong problem

### Testing
- Use the `examples/` directory for test files (e.g., `test_*.kite`)
- Run the plugin with `gradle runIde` to test changes
- Test files should cover edge cases and different formatting scenarios

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

### Implementation Details: Indentation

#### Critical Discovery: Newline Token Handling
The formatter must **skip creating blocks for newline tokens** (`NL` and `NEWLINE`). IntelliJ's formatter handles newlines internally to apply indentation. If you create blocks for newline tokens, indentation will not work.

**Implementation:**
```java
private boolean shouldSkipToken(IElementType type) {
    return type == TokenType.WHITE_SPACE ||
           type == KiteTokenTypes.NL ||
           type == KiteTokenTypes.NEWLINE;
}
```

#### Brace Tracking for Block Indentation
Only content **between `{` and `}`** should be indented. Track brace state while building child blocks:

1. Initialize `boolean insideBraces = false`
2. Create blocks for each child token
3. **After** creating the block, update state:
   - If token is `LBRACE`: set `insideBraces = true`
   - If token is `RBRACE`: set `insideBraces = false`
4. Pass `insideBraces` to `getChildIndent(childType, insideBraces)`

**Why update AFTER?** The brace itself shouldn't be indented, but everything following it should be.

#### Code Style Settings Provider
Required for IntelliJ to know the indent size. Create `KiteLanguageCodeStyleSettingsProvider` and register in `plugin.xml`:

```java
@Override
public void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                              @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.INDENT_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 8;
    indentOptions.TAB_SIZE = 4;
    indentOptions.USE_TAB_CHARACTER = false;
}
```

#### What Didn't Work (Save Time Debugging)
- ❌ Using `Indent.getNormalIndent()` without code style provider (no indent size)
- ❌ Creating blocks for newline tokens (prevents indentation entirely)
- ❌ Tracking braces BEFORE creating blocks (indents the wrong elements)
- ❌ Using different `Language.INSTANCE` for SpacingBuilder vs FormattingModel
- ✅ **Solution**: Skip newlines + track braces AFTER + use `Indent.getSpaceIndent(4)`

#### Understanding IntelliJ's Indent Types: A Deep Dive

**CRITICAL: The Three Indent Types**

IntelliJ Platform provides three distinct indent types, each working differently:

1. **`Indent.getSpaceIndent(n)`** - ABSOLUTE positioning from column 0
   - Creates a fixed indent of exactly `n` spaces from the start of the line
   - NOT relative to parent indentation
   - Example: `getSpaceIndent(4)` always produces 4 spaces, regardless of nesting level
   - **Use case**: Rare - only when you need absolute column positioning

2. **`Indent.getNormalIndent()`** - Relative indent using INDENT_SIZE
   - Adds one indentation level relative to the parent block
   - Uses `INDENT_SIZE` from code style settings (2 spaces in Kite)
   - Example: If parent is at 4 spaces, child with `getNormalIndent()` will be at 6 spaces (4 + 2)
   - **Use case**: Standard block content (resource bodies, function bodies, etc.)

3. **`Indent.getContinuationIndent()`** - Relative indent using CONTINUATION_INDENT_SIZE
   - Adds one continuation level relative to the parent block
   - Uses `CONTINUATION_INDENT_SIZE` from code style settings (4 spaces in Kite)
   - Example: If parent is at 4 spaces, child with `getContinuationIndent()` will be at 8 spaces (4 + 4)
   - **Use case**: Wrapped parameters, nested literals, deeper nesting that needs visual distinction

**Current Kite Settings:**
```java
indentOptions.INDENT_SIZE = 2;
indentOptions.CONTINUATION_INDENT_SIZE = 4;
indentOptions.TAB_SIZE = 2;
```

#### Case Study: Object Literal Indentation Fix

**Problem:** Object literal properties were not getting proper indentation when "Reformat Code" was executed.

**Expected behavior:**
```kite
  resource VM.Instance server {
    tag = {
      Name       : "web-server",  // <- 6 spaces (4 for resource + 2 for literal content)
      Environment: "production"   // <- 6 spaces
    }                            // <- 4 spaces (aligned with property)
  }
```

**Debugging Journey:**

1. **First Failed Attempt - Using `getSpaceIndent(2)`:**
   ```java
   // WRONG - creates absolute 2 spaces from column 0
   if (parentType == KiteElementTypes.OBJECT_LITERAL) {
       return Indent.getSpaceIndent(2);
   }
   ```
   **Result:** Properties appeared at column 2 instead of column 6
   **Why it failed:** `getSpaceIndent()` ignores parent indentation entirely

2. **Second Failed Attempt - Using `getNormalIndent()`:**
   ```java
   // DIDN'T WORK - added only 2 spaces but wasn't enough visual nesting
   if (parentType == KiteElementTypes.OBJECT_LITERAL) {
       return Indent.getNormalIndent();
   }
   ```
   **Result:** Code compiled and ran but indentation didn't change visually
   **Why it failed:** `getNormalIndent()` only adds INDENT_SIZE (2 spaces), which wasn't sufficient for the visual nesting needed in object literals. The formatter needed continuation indent for proper visual hierarchy.

3. **Final Working Solution - Using `getContinuationIndent()`:**
   ```java
   // CORRECT - adds 4 spaces relative to parent (CONTINUATION_INDENT_SIZE)
   if (parentType == KiteElementTypes.OBJECT_LITERAL || parentType == KiteElementTypes.ARRAY_LITERAL) {
       // Opening braces don't get indented
       if (childType == KiteTokenTypes.LBRACE || childType == KiteTokenTypes.LBRACK) {
           return Indent.getNoneIndent();
       }
       // Closing braces get normal indent to align with the property
       if (childType == KiteTokenTypes.RBRACE || childType == KiteTokenTypes.RBRACK) {
           return Indent.getNormalIndent();
       }
       // All content (identifiers, colons, values) gets continuation indent
       return Indent.getContinuationIndent();
   }
   ```
   **Result:** Properties correctly indented at 6 spaces (4 parent + 4 continuation), closing brace at 4 spaces
   **Why it worked:** `getContinuationIndent()` provides the deeper nesting visual that object/array literal content needs

**Key Insight:** Object literals and array literals need **continuation indent** for their content, not normal indent. This creates proper visual hierarchy:
- Resource/component body uses normal indent (2 spaces) → 4 total
- Property value (object literal) content uses continuation indent (4 spaces) → 8 total (but 6 in practice due to property already being at 2)

**Implementation in KiteBlock.java (lines 956-988):**
```java
// OBJECT_LITERAL as a block element itself needs proper indent when inside braces
if (elementType == KiteElementTypes.OBJECT_LITERAL) {
    System.err.println("[KiteBlock.getChildIndent] Block element is OBJECT_LITERAL, insideBraces=" + insideBraces);
    if (insideBraces) {
        System.err.println("[KiteBlock.getChildIndent] --> Returning getNormalIndent() for OBJECT_LITERAL block inside braces");
        return Indent.getNormalIndent();
    }
    return Indent.getNoneIndent();
}

// Special case: for object/array literals, content (except braces) gets indented
// This check must come FIRST to ensure it takes precedence
if (parentType == KiteElementTypes.OBJECT_LITERAL || parentType == KiteElementTypes.ARRAY_LITERAL) {
    System.err.println("[KiteBlock.getChildIndent] *** OBJECT/ARRAY LITERAL PARENT DETECTED ***");
    // The opening braces/brackets themselves don't get indented
    if (childType == KiteTokenTypes.LBRACE || childType == KiteTokenTypes.LBRACK) {
        System.err.println("[KiteBlock.getChildIndent] --> Returning getNoneIndent() for opening brace/bracket");
        return Indent.getNoneIndent();
    }
    // Closing braces should align with the opening of the literal (normal indent from parent)
    if (childType == KiteTokenTypes.RBRACE) {
        System.err.println("[KiteBlock.getChildIndent] --> Returning getNormalIndent() for closing brace");
        return Indent.getNormalIndent();
    }
    if (childType == KiteTokenTypes.RBRACK) {
        System.err.println("[KiteBlock.getChildIndent] --> Returning getNormalIndent() for closing bracket");
        return Indent.getNormalIndent();
    }
    // Everything else (identifiers, colons, strings, etc.) gets continuation indent for deeper nesting
    System.err.println("[KiteBlock.getChildIndent] --> Returning getContinuationIndent() for content");
    return Indent.getContinuationIndent();
}
```

#### Debugging Tips for Formatter Issues

1. **Add Debug Logging:**
   ```java
   System.err.println("[KiteBlock.getChildIndent] parentType=" + parentType + ", childType=" + childType);
   ```
   Use `System.err` to see output in IDE's stderr even when running from Gradle

2. **Check Actual vs Expected:**
   - Create test files in `examples/` with comments showing expected indentation
   - Run "Reformat Code" (Cmd+Alt+L) to see actual results
   - Compare line-by-line

3. **Force Clean Builds:**
   ```bash
   ./gradlew clean compileJava --no-build-cache --rerun-tasks
   ```
   Gradle's build cache can mask code changes

4. **Kill Background Processes:**
   ```bash
   killall -9 java
   ```
   Multiple IDE instances can cause confusion about which version is running

5. **Verify Code Style Settings:**
   Check `KiteLanguageCodeStyleSettingsProvider.java` to confirm INDENT_SIZE and CONTINUATION_INDENT_SIZE values

6. **Test the Indent Type:**
   If unsure which indent type to use, try all three and observe the differences:
   - `getSpaceIndent(n)` → absolute positioning
   - `getNormalIndent()` → adds INDENT_SIZE
   - `getContinuationIndent()` → adds CONTINUATION_INDENT_SIZE

7. **Common Mistakes:**
   - ❌ Using `getSpaceIndent()` when you want relative indentation
   - ❌ Using `getNormalIndent()` for deeply nested structures that need visual distinction
   - ❌ Not handling opening and closing braces separately
   - ❌ Not checking `insideBraces` state for block elements
   - ❌ Assuming code changes are live without verifying via clean build

#### RESOLVED: Nested Object Literal Indentation

**Problem:** Nested object literals were not being indented correctly because the parser was creating flat token structures instead of hierarchical PSI elements.

**Root Cause:** The `parseObjectLiteral()` method in `KitePsiParser.java` was counting braces but not recursively creating nested `OBJECT_LITERAL` elements:
```java
// OLD CODE - just counted braces, no nesting
int braceDepth = 1;
while (!builder.eof() && braceDepth > 0) {
    if (tokenType == KiteTokenTypes.LBRACE) braceDepth++;
    else if (tokenType == KiteTokenTypes.RBRACE) braceDepth--;
    builder.advanceLexer();
}
```

**Solution:** Two changes were needed:

1. **Parser Fix** (`KitePsiParser.java`): Recursively create nested `OBJECT_LITERAL` and `ARRAY_LITERAL` elements:
```java
private void parseObjectLiteral(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();  // Consume opening brace

    while (!builder.eof()) {
        IElementType tokenType = builder.getTokenType();
        if (tokenType == KiteTokenTypes.LBRACE) {
            parseObjectLiteral(builder);  // Recurse for nested objects
        } else if (tokenType == KiteTokenTypes.LBRACK) {
            parseArrayLiteral(builder);   // Recurse for nested arrays
        } else if (tokenType == KiteTokenTypes.RBRACE) {
            builder.advanceLexer();
            break;
        } else {
            builder.advanceLexer();
        }
    }
    marker.done(KiteElementTypes.OBJECT_LITERAL);
}
```

2. **Formatter Fix** (`KiteBlock.java`): Reorder checks in `getChildIndent()` so `OBJECT_LITERAL` parent handling runs BEFORE the generic literal handling, and give nested literals `getNormalIndent()`:
```java
// Handle OBJECT_LITERAL parents FIRST
if (parentType == KiteElementTypes.OBJECT_LITERAL) {
    if (childType == KiteTokenTypes.LBRACE) return Indent.getNoneIndent();
    if (childType == KiteTokenTypes.RBRACE) return Indent.getNoneIndent();
    // Nested literals get normal indent for proper nesting levels
    if (childType == KiteElementTypes.OBJECT_LITERAL ||
        childType == KiteElementTypes.ARRAY_LITERAL) {
        return Indent.getNormalIndent();
    }
    return Indent.getNormalIndent();
}
```

**Expected behavior (2 spaces per nesting level):**
```kite
resource VM.Instance server {
  tag = {
    Name       : "web-server",
    Environment: "production"
    New        : {
      a          : "b"
    }
  }
}
```

**Test file:** `/Users/mimedia/IdeaProjects/kite-intellij-plugin/examples/component.kite`

#### Inline vs Multi-line Literal Formatting

**Problem:** Single-line objects like `{ port: port, protocol: "tcp" }` should NOT have alignment padding before colons, but multi-line objects should.

**Solution:** Use `isMultiLine()` check to distinguish formatting approach:
```java
private boolean isMultiLine(ASTNode node) {
    String text = node.getText();
    return text.contains("\n");
}
```

**Implementation Pattern:**
1. In `buildAlignedChildren()` and `inlineLiteralChildren()`:
   - If `isMultiLine(objectLiteralNode)` → use `inlineObjectWithAlignment()` for colon alignment
   - If NOT multi-line → use `inlineLiteralChildren()` without alignment padding
2. In `getSpacing()`:
   - Only force line breaks for multi-line literals
   ```java
   if (parentType == KiteElementTypes.OBJECT_LITERAL || parentType == KiteElementTypes.ARRAY_LITERAL) {
       if (isMultiLine(myNode) && child1 instanceof KiteBlock && child2 instanceof KiteBlock) {
           // Force line breaks only for multi-line literals
       }
   }
   ```

**Key Method - `inlineObjectWithAlignment()`:**
- Two-pass algorithm: first find max key length among direct children, then build blocks with padding
- Recursively calls itself for nested multi-line objects
- Recursively calls `inlineLiteralChildren()` for nested single-line objects

#### Alignment Groups Break on Comments

**Problem:** Comments between declarations should break alignment groups so declarations above and below are aligned independently.

**Solution:** Add comment detection as group separators in `identifyAlignmentGroups()`:
```java
// Comments also separate alignment groups (they indicate logical sections)
if (childType == KiteTokenTypes.LINE_COMMENT || childType == KiteTokenTypes.BLOCK_COMMENT) {
    hasBlankLineSinceLastDecl = true;  // Treat comment as group separator
}
```

**Example:**
```kite
var x  = 1     // Group 1
var yp = 2     // Group 1 (aligns with x)

// This comment breaks the group
var a = 3      // Group 2 (fresh alignment, no padding)
```

#### Schema Property Alignment with Type + Name

**Problem:** Schema properties have format `type propName = value` and alignment was only using `propName` length, causing incorrect spacing.

**Solution:** Track `previousTypeIdentifier` and calculate combined length:
```java
ASTNode previousIdentifier = null;
ASTNode previousTypeIdentifier = null;  // For schema properties

// When calculating key length for alignment:
if (myNode.getElementType() == KiteElementTypes.SCHEMA_DECLARATION && previousTypeIdentifier != null) {
    // Schema properties: combine type + space + propName
    keyLength = previousTypeIdentifier.getTextLength() + 1 + previousIdentifier.getTextLength();
} else {
    // Normal properties: just propName
    keyLength = previousIdentifier.getTextLength();
}
```

**Result:**
```kite
schema DatabaseConfig {
  string  host        // "string" (6) + 1 + "host" (4) = 11
  number  port = 5432 // "number" (6) + 1 + "port" (4) = 11
  boolean ssl  = true // "boolean" (7) + 1 + "ssl" (3) = 11
}
```

### Features
- Format entire file with Cmd+Alt+L (Mac) or Ctrl+Alt+L (Windows/Linux)
- Format selected text only by selecting code first, then using the reformat shortcut
- Consistent spacing around keywords, operators, and delimiters
- Proper indentation for nested structures (2 spaces per level in Kite)
- Preserves semantic meaning while improving readability

## File Type Icon

### Overview
Custom SVG icon for `.kite` files displayed in the IDE file tree.

### Implementation
- **File**: `src/main/resources/icons/kite-file.svg`
- **Registration**: Registered in `plugin.xml` via `<icon>` attribute on `fileType` extension

### Icon Design
- 16x16 pixel SVG
- Four-panel kite shape representing the Kite language
- Gradient fills for depth (light blue top-right to dark blue bottom-left)
- Subtle drop shadow using SVG filter
- 12° rotation for dynamic appearance
- Subtle spine lines for structural definition

### Color Scheme
- Top-right (light source): `#6BB9F0` → `#4A9BD9` gradient
- Top-left: `#4AA3DF`
- Bottom-right: `#2E86C1`
- Bottom-left (shadow): `#2980B9` → `#1A5276` gradient
- Spine lines: `#1A5276` with 0.3-0.5 opacity
