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
7. **How to update CLAUDE.md**
   - don't add implementation details to the top of the file - keep it short and focused on the problem
   - add a new section for each new feature or change
   - use the same format as the existing sections
8. **If you're stuck, ask for help**
   - If you're stuck, ask for help!
   - If you're stuck on a particular feature, ask for help on that specific feature
   - If you're stuck on a general problem, ask for help on the general problem
   - If you're stuck on a specific problem and can't find help, ask for help on the general problem
   - It's OK to not know the answer to a question, just ask for help!

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


### Key Files
1. `KiteFormattingModelBuilder.java` - Main formatter entry point, defines spacing rules via `SpacingBuilder`
2. `KiteBlock.java` - Represents hierarchical formatting blocks, manages indentation and child block creation


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

## Go to Declaration (Cmd+Click Navigation)

### Overview
"Go to Declaration" enables Cmd+Click (Mac) or Ctrl+Click (Windows/Linux) navigation from identifier usages to their declarations. Supports both simple identifiers and property access expressions.

### Implementation
- **File**: `src/main/java/io/kite/intellij/navigation/KiteGotoDeclarationHandler.java`
- **Registration**: Registered in `plugin.xml` as `gotoDeclarationHandler` extension

### Supported Navigation Types

1. **Simple Identifier Navigation**
   - Clicking on `server` navigates to `resource VM.Instance server { ... }`
   - Works for: variables, resources, components, schemas, functions, inputs, outputs, for-loop variables

2. **Property Access Navigation**
   - Clicking on `size` in `server.size` navigates to `size = "t2.micro"` inside the server declaration
   - Detects property access by checking for DOT token before the identifier

### Key Technical Decisions

#### Why NOT PsiReferenceContributor (Failed Approach)


**Problems encountered:**
- `PsiReferenceContributor` was never called by IntelliJ (no log output from inside the provider)
- Pattern matching with `PlatformPatterns.psiElement()` didn't work for the Kite language
- Even using `PsiReferenceService.getService().getContributedReferences()` returned 0 references

**Lesson:** For custom languages, the PsiReference system may require additional infrastructure (like `PsiElement` marker interfaces or proper `LeafPsiElement` subclasses) that wasn't present in our PSI implementation.

#### Why Direct PSI Traversal (Working Approach)

Direct PSI traversal in `GotoDeclarationHandler` is simpler and more reliable:

```java
public class KiteGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement, int offset, Editor editor) {
        // Direct PSI tree traversal to find declarations
    }
}
```

**Benefits:**
- Works immediately without additional PSI infrastructure
- Full control over resolution logic
- Easy to debug (add System.err.println to see exactly what's happening)
- Maximum performance (no framework overhead)

### Implementation Details

#### Property Access Detection

Check if an identifier is part of `object.property` by looking for DOT token before it:

```java
private PsiElement getPropertyAccessObject(PsiElement element) {
    PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
    if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.DOT) {
        PsiElement objectElement = skipWhitespaceBackward(prev.getPrevSibling());
        if (objectElement != null && objectElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
            return objectElement;
        }
    }
    return null;
}
```

#### Property Resolution Within Declarations

To resolve `size` in `server.size`:
1. Find the declaration element for `server` (e.g., `RESOURCE_DECLARATION`)
2. Search within that declaration's braces for property assignments matching `size`
3. Look for patterns: `identifier =`, `identifier :`, `identifier +=`

```java
private PsiElement findPropertyRecursive(PsiElement element, String propertyName,
                                          PsiElement sourceElement, boolean insideBraces) {
    PsiElement child = element.getFirstChild();
    boolean currentInsideBraces = insideBraces;

    while (child != null) {
        IElementType childType = child.getNode().getElementType();

        // Track brace entry/exit
        if (childType == KiteTokenTypes.LBRACE) {
            currentInsideBraces = true;
        } else if (childType == KiteTokenTypes.RBRACE) {
            currentInsideBraces = false;
        }

        // Check for property patterns inside braces
        if (currentInsideBraces && childType == KiteTokenTypes.IDENTIFIER) {
            if (propertyName.equals(child.getText()) && child != sourceElement) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN ||
                        nextType == KiteTokenTypes.COLON ||
                        nextType == KiteTokenTypes.PLUS_ASSIGN) {
                        return child;  // Found the property declaration
                    }
                }
            }
        }

        // Recurse into children (but not into nested declarations)
        if (child.getFirstChild() != null && !isDeclarationType(childType)) {
            PsiElement result = findPropertyRecursive(child, propertyName, sourceElement, currentInsideBraces);
            if (result != null) return result;
        }

        child = child.getNextSibling();
    }
    return null;
}
```

#### Whitespace Handling (Critical!)

When traversing PSI siblings, you must skip whitespace tokens. IntelliJ uses `TokenType.WHITE_SPACE` for spaces, but our lexer also defines `KiteTokenTypes.NL` for newlines:

```java
private boolean isWhitespace(IElementType type) {
    return type == TokenType.WHITE_SPACE ||  // IntelliJ's built-in whitespace
           type == KiteTokenTypes.NL ||       // Our newline token
           type == KiteTokenTypes.WHITESPACE ||
           type == KiteTokenTypes.NEWLINE;
}

private PsiElement skipWhitespaceBackward(PsiElement element) {
    while (element != null && isWhitespace(element.getNode().getElementType())) {
        element = element.getPrevSibling();
    }
    return element;
}

private PsiElement skipWhitespaceForward(PsiElement element) {
    while (element != null && isWhitespace(element.getNode().getElementType())) {
        element = element.getNextSibling();
    }
    return element;
}
```

**Important:** The formatter needs to distinguish between spaces and newlines for blank line detection, so we keep them as separate token types in the lexer.

### Declaration Types

The handler recognizes these declaration types:

```java
private boolean isDeclarationType(IElementType type) {
    return type == KiteElementTypes.VARIABLE_DECLARATION ||
           type == KiteElementTypes.INPUT_DECLARATION ||
           type == KiteElementTypes.OUTPUT_DECLARATION ||
           type == KiteElementTypes.RESOURCE_DECLARATION ||
           type == KiteElementTypes.COMPONENT_DECLARATION ||
           type == KiteElementTypes.SCHEMA_DECLARATION ||
           type == KiteElementTypes.FUNCTION_DECLARATION ||
           type == KiteElementTypes.TYPE_DECLARATION ||
           type == KiteElementTypes.FOR_STATEMENT;  // for-loop variables
}
```

### Finding the Name in a Declaration

Different declaration types have the name in different positions:

```java
private PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
    // For-loop special case: "for identifier in ..."
    if (declarationType == KiteElementTypes.FOR_STATEMENT) {
        boolean foundFor = false;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.FOR) {
                foundFor = true;
            } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                return child;
            }
            child = child.getNextSibling();
        }
    }

    // For var/input/output/resource/component/schema/function:
    // Find the last identifier before '=' or '{'
    PsiElement lastIdentifier = null;
    PsiElement child = declaration.getFirstChild();
    while (child != null) {
        IElementType childType = child.getNode().getElementType();
        if (childType == KiteTokenTypes.IDENTIFIER) {
            lastIdentifier = child;
        } else if (childType == KiteTokenTypes.ASSIGN ||
                   childType == KiteTokenTypes.LBRACE ||
                   childType == KiteTokenTypes.PLUS_ASSIGN) {
            if (lastIdentifier != null) {
                return lastIdentifier;
            }
        }
        child = child.getNextSibling();
    }
    return lastIdentifier;
}
```

### Debugging Tips

1. **Add stderr logging** (visible in Gradle output):
   ```java
   System.err.println("[KiteGoto] Processing: " + sourceElement.getText());
   ```

2. **Kill old IDE instances** before testing:
   ```bash
   pkill -9 -f "java.*idea"
   ./gradlew compileJava && ./gradlew runIde
   ```

3. **Verify handler is called**: If no debug output appears, the handler isn't being invoked (check plugin.xml registration)

4. **Check token types**: Use PSI Viewer (Tools > View PSI Structure) to see actual token types in your file

### Declaration Name Exclusion

**Problem:** Clicking on `port` in `input number port = 8080` was navigating to other declarations named `port`. But `port` here is itself a declaration name, not a reference.

**Solution:** Check if the clicked identifier is a declaration name and skip navigation:

```java
private boolean isDeclarationName(PsiElement element) {
    // Check if this identifier is followed by = or { or += or : (declaration/property pattern)
    PsiElement next = skipWhitespaceForward(element.getNextSibling());
    if (next != null) {
        IElementType nextType = next.getNode().getElementType();
        if (nextType == KiteTokenTypes.ASSIGN ||
            nextType == KiteTokenTypes.LBRACE ||
            nextType == KiteTokenTypes.PLUS_ASSIGN ||
            nextType == KiteTokenTypes.COLON) {
            // This identifier is followed by = or { or : - it's a declaration/property name
            return true;
        }
    }

    // Check if this is a for loop variable (identifier after "for" keyword)
    PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
    if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.FOR) {
        return true;
    }

    return false;
}
```

**Key insight:** An identifier is a declaration name if it's followed by `=`, `{`, `+=`, or `:`. These patterns indicate:
- `=` → variable/input/output declaration: `input number port = 8080`
- `{` → resource/component/schema/function declaration: `resource VM.Instance server {`
- `+=` → property append: `tags += { ... }`
- `:` → object property key: `{ port: 8080 }`

### Test Cases

Test file: `examples/component.kite`

| Click on | Expected Navigation |
|----------|---------------------|
| `server` in `server.size` | Line 13: `resource VM.Instance server {` |
| `size` in `server.size` | Line 14: `size = size` |
| `port` in `{ port: port }` (first) | Nothing (it's a property key) |
| `port` in `{ port: port }` (second) | Line 3: `input number port = 8080` |
| `port` in `input number port = 8080` | Nothing (it's a declaration name) |
| `firewall` in `resource SecurityGroup firewall` | Nothing (it's a declaration name) |

### What Didn't Work (Save Time)

- ❌ `PsiReferenceContributor` with pattern matching - never called for Kite language
- ❌ `PsiReferenceService.getContributedReferences()` - returned 0 references
- ❌ Using `PlatformPatterns.psiElement(LeafPsiElement.class).withLanguage()` - too restrictive
- ❌ Forgetting `TokenType.WHITE_SPACE` in whitespace check - breaks property detection
- ✅ **Solution**: Direct PSI traversal in `GotoDeclarationHandler` with comprehensive whitespace handling

### Find Usages Dropdown (Declaration Name Click)

#### Overview
When Cmd+clicking on a **declaration name** (e.g., `server` in `resource VM.Instance server {}`), a dropdown popup shows all usages of that identifier. Each usage displays:
- Context-aware colored icon (Resource=purple R, Component=blue C, etc.)
- Full line of code where the usage appears
- Location string: "in [Context Type] - filename.kite:line"

#### Implementation Files
- **Wrapper**: `src/main/java/io/kite/intellij/navigation/KiteNavigatablePsiElement.java`
- **Icon Provider**: `src/main/java/io/kite/intellij/navigation/KiteNavigationIconProvider.java`
- **Handler**: `src/main/java/io/kite/intellij/navigation/KiteGotoDeclarationHandler.java`

#### How It Works

1. `KiteGotoDeclarationHandler.getGotoDeclarationTargets()` detects if the clicked identifier is a declaration name
2. If so, calls `findUsages()` to collect all references to that name
3. Each raw `PsiElement` usage is wrapped in `KiteNavigatablePsiElement`
4. IntelliJ shows the popup using `NavigationItem.getPresentation()` from the wrapper

#### The Wrapper Pattern (Key Solution)

The wrapper class `KiteNavigatablePsiElement` provides custom presentation while maintaining navigation functionality:

```java
public class KiteNavigatablePsiElement extends FakePsiElement implements NavigationItem {
    private final PsiElement myElement;
    private final String myLineText;
    private final String myLocationString;
    private final Icon myIcon;

    public KiteNavigatablePsiElement(@NotNull PsiElement element) {
        this.myElement = element;
        this.myLineText = getLineText(element);           // Full line of code
        this.myLocationString = buildLocationString(element);  // "in Resource - file.kite:42"
        this.myIcon = getIconForContext(element);         // Context-aware icon
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() { return myLineText; }
            @Override
            public @Nullable String getLocationString() { return myLocationString; }
            @Override
            public @Nullable Icon getIcon(boolean unused) { return myIcon; }
        };
    }

    @Override
    public void navigate(boolean requestFocus) {
        // Delegate to wrapped element for actual navigation
        Navigatable navigatable = PsiNavigationSupport.getInstance().getDescriptor(myElement);
        if (navigatable != null) {
            navigatable.navigate(requestFocus);
        }
    }
}
```

#### IconProvider Integration

`FakePsiElement.getIcon()` is **final** and cannot be overridden. Instead, use an `IconProvider` extension to provide icons for the wrapper:

```java
public class KiteNavigationIconProvider extends IconProvider {
    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        // Handle our custom navigatable wrapper
        if (element instanceof KiteNavigatablePsiElement) {
            KiteNavigatablePsiElement wrapper = (KiteNavigatablePsiElement) element;
            if (wrapper.getPresentation() != null) {
                return wrapper.getPresentation().getIcon(false);
            }
        }
        // ... rest of existing code for other elements
    }
}
```

#### What Didn't Work (Provider Approaches)

Several IntelliJ extension points were tried but didn't work for the classic "Go to Declaration" popup:

1. **`ItemPresentationProvider`** - Requires `NavigationItem` interface, but `PsiElement` doesn't extend it
   - Error: Type argument `PsiElement` not within bounds

2. **`TargetPresentationProvider`** - Wrong API path
   - This is for the newer navigation API (`TargetPopupPresentation`), not the classic popup
   - Method signature: `getPresentation()` not `getTargetPresentation()`

3. **`GotoTargetRendererProvider`** - Wrong handler type
   - This works with `GotoTargetHandler`, not `GotoDeclarationHandler`
   - Our handler is `GotoDeclarationHandler` for Cmd+Click navigation

4. **Direct icon in wrapper via `getIcon()`** - Cannot override
   - Error: `getIcon(int) in FakePsiElement cannot override - overridden method is final`

5. **Returning non-wrapper elements** - Icons work but no custom presentation
   - `IconProvider` provides icons for raw `PsiElement` objects
   - But `ItemPresentation` comes from the element itself, which has no custom presentation

#### Why the Wrapper Pattern Works

The combination works because:
1. `GotoDeclarationHandler.getGotoDeclarationTargets()` can return any `PsiElement[]`
2. `FakePsiElement` is a valid `PsiElement` that IntelliJ accepts
3. `NavigationItem.getPresentation()` is checked by the popup renderer for display text
4. `IconProvider` extension is consulted for icons (bypasses final `getIcon()`)
5. Navigation delegates to the wrapped element, preserving actual file navigation

#### Usage Detection Logic

```java
private boolean isDeclarationName(PsiElement element) {
    // Declaration names are followed by = or { or += or :
    PsiElement next = skipWhitespaceForward(element.getNextSibling());
    if (next != null) {
        IElementType nextType = next.getNode().getElementType();
        if (nextType == KiteTokenTypes.ASSIGN ||
            nextType == KiteTokenTypes.LBRACE ||
            nextType == KiteTokenTypes.PLUS_ASSIGN ||
            nextType == KiteTokenTypes.COLON) {
            return true;
        }
    }
    // Also check for-loop variable
    PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
    return prev != null && prev.getNode().getElementType() == KiteTokenTypes.FOR;
}
```

When `isDeclarationName()` returns `true`, the handler finds all usages and wraps them for the popup display.

## String Interpolation Support

### Overview
String interpolation allows embedding expressions inside double-quoted strings using `${expression}` syntax. The plugin provides:
- Syntax highlighting with orange `${` and `}` delimiters
- Cmd+Click navigation from variables inside interpolations to their declarations
- Precise underlining of just the variable name (not the entire string)

### Grammar Implementation (Split Grammar Approach)

String interpolation required a **split grammar** with separate lexer and parser files because ANTLR lexer modes are needed to handle the state transitions between string content and interpolated expressions.

**Files:**
- `src/main/antlr/io/kite/intellij/parser/KiteLexer.g4` - Lexer with modes
- `src/main/antlr/io/kite/intellij/parser/KiteParser.g4` - Parser rules

#### Lexer Modes

The lexer uses two modes to handle string interpolation:

```antlr
// DEFAULT_MODE - normal code
DQUOTE: '"' -> pushMode(STRING_MODE);
SINGLE_STRING: '\'' (~['\\\r\n] | '\\' .)* '\'';

// STRING_MODE - inside double-quoted strings
mode STRING_MODE;
STRING_DQUOTE: '"' -> popMode;           // End of string
INTERP_START: '${' -> pushMode(DEFAULT_MODE);  // Start interpolation
STRING_DOLLAR: '$';                       // Lone $ (not followed by {)
STRING_ESCAPE: '\\' .;                    // Escaped character
STRING_TEXT: ~["\\$]+;                    // Regular text
```

#### Interpolation Depth Tracking

For nested interpolations like `"${obj.get("${key}")}"`, the lexer tracks depth:

```antlr
@members {
    private int interpolationDepth = 0;
}

// In DEFAULT_MODE:
LBRACE: '{' { if (interpolationDepth > 0) interpolationDepth++; };
RBRACE: '}' {
    if (interpolationDepth > 0) {
        interpolationDepth--;
        if (interpolationDepth == 0) {
            setType(INTERP_END);  // Mark as interpolation end
            popMode();            // Return to STRING_MODE
        }
    }
};

// When entering interpolation:
INTERP_START: '${' { interpolationDepth = 1; } -> pushMode(DEFAULT_MODE);
```

#### Parser Rules for Interpolated Strings

```antlr
stringLiteral
    : interpolatedString
    | SINGLE_STRING
    ;

interpolatedString
    : DQUOTE stringPart* STRING_DQUOTE
    ;

stringPart
    : STRING_TEXT                           // Regular text
    | STRING_ESCAPE                         // Escaped character
    | STRING_DOLLAR                         // Lone $ not followed by {
    | INTERP_START expression INTERP_END    // ${expression}
    ;
```

### Syntax Highlighting

#### Token Colors

| Token | Color | Description |
|-------|-------|-------------|
| `INTERP_START` (`${`) | Orange | Opens interpolation |
| `INTERP_END` (`}`) | Orange | Closes interpolation |
| `STRING_TEXT` | String color | Regular string content |
| `STRING_ESCAPE` | String escape color | Escaped characters |
| Identifier inside `${}` | Default text | Variable name |

**Implementation in `KiteSyntaxHighlighter.java`:**
```java
// Map interpolation delimiters to orange color
INTERP_DELIM_KEYS = new TextAttributesKey[]{INTERP_DELIM};
// ...
if (tokenType == KiteTokenTypes.INTERP_START || tokenType == KiteTokenTypes.INTERP_END) {
    return INTERP_DELIM_KEYS;
}
```

**INTERP_END Token Fix:**
The closing `}` inside interpolations is colored orange by using `setType(INTERP_END)` in the lexer when the brace closes an interpolation context (tracked via `interpolationDepth`).

### Navigation from Interpolated Variables

#### Reference Contributor

`KiteReferenceContributor` registers references for identifiers inside interpolations:

```java
@Override
public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    // Register for IDENTIFIER tokens (handles regular identifiers AND interpolation)
    registrar.registerReferenceProvider(
        PlatformPatterns.psiElement(),
        new KiteReferenceProvider()
    );
}
```

The `KiteReference` class resolves the identifier to its declaration by searching the file's PSI tree.

#### Precise TextRange for Underlining

The key to underlining only the variable (not the entire string) is providing a precise `TextRange` in the reference:

```java
public class KiteReference extends PsiPolyVariantReferenceBase<PsiElement> {
    @Override
    public @NotNull TextRange getRangeInElement() {
        // Return range covering just the identifier text
        return new TextRange(0, myElement.getTextLength());
    }
}
```

Since each token (including identifiers inside interpolations) is its own PSI element, the range naturally covers just that token.

### Known Limitations

#### Simple Interpolation (`$var`) Not Supported

Navigation for simple interpolation syntax (without braces) is **not yet implemented**:

```kite
// Works - brace interpolation
console.log("Port: ${port}")   // Cmd+Click on 'port' navigates to declaration

// Not yet working - simple interpolation
console.log("Port: $port")     // Cmd+Click does nothing
```

**Why:** The lexer currently emits `STRING_DOLLAR` + remaining text as separate tokens, but doesn't create a navigable identifier token for the variable name after `$`.

**Future fix:** Would require lexer changes to recognize `$identifier` pattern and emit proper tokens.

## Formatter: Line Break Preservation

### Problem
After implementing string interpolation, the formatter was collapsing declarations onto single lines:

**Input:**
```kite
var WebServer x

input number port = 8080
input string siz = "t2.micro"
```

**Incorrect Output:**
```kite
var WebServer xinput number port = 8080input string siz = "t2.micro"
```

### Root Cause
The formatter skips `NL` (newline) tokens in `shouldSkipToken()` to let IntelliJ handle indentation. However, this meant no spacing rules were forcing line breaks between declarations.

### Solution
Add explicit line break rules in `KiteBlock.getSpacing()` for declaration keywords:

```java
@Override
public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    IElementType parentType = myNode.getElementType();

    // Force line breaks between declaration keywords inside block elements
    if (isBlockElement(parentType) && child1 instanceof KiteBlock && child2 instanceof KiteBlock) {
        IElementType type2 = ((KiteBlock) child2).myNode.getElementType();
        IElementType type1 = ((KiteBlock) child1).myNode.getElementType();

        // If child2 starts a new declaration, force a line break
        if (isDeclarationKeyword(type2)) {
            return Spacing.createSpacing(0, 0, 1, true, 1);
        }

        // After closing brace, if followed by anything other than closing brace, force line break
        if (type1 == KiteTokenTypes.RBRACE && type2 != KiteTokenTypes.RBRACE) {
            return Spacing.createSpacing(0, 0, 1, true, 1);
        }
    }
    // ... rest of method
}

private boolean isDeclarationKeyword(IElementType type) {
    return type == KiteTokenTypes.INPUT ||
           type == KiteTokenTypes.OUTPUT ||
           type == KiteTokenTypes.VAR ||
           type == KiteTokenTypes.RESOURCE ||
           type == KiteTokenTypes.COMPONENT ||
           type == KiteTokenTypes.SCHEMA ||
           type == KiteTokenTypes.FUN ||
           type == KiteTokenTypes.TYPE ||
           type == KiteTokenTypes.IF ||
           type == KiteTokenTypes.FOR ||
           type == KiteTokenTypes.WHILE ||
           type == KiteTokenTypes.RETURN;
}
```

### Key Insight
The `Spacing.createSpacing(0, 0, 1, true, 1)` parameters mean:
- `minSpaces=0`, `maxSpaces=0` - no horizontal spacing
- `minLineFeeds=1` - at least one line break required
- `keepLineBreaks=true` - preserve existing line breaks
- `keepBlankLines=1` - preserve up to 1 blank line
