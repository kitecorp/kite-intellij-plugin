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
- **Component declarations** - Collapse component blocks
- **Schema declarations** - Collapse schema blocks
- **Function declarations** - Collapse function blocks
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
