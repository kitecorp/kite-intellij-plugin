# Screenshots Guide for Kite Plugin

Use these instructions to capture screenshots for the JetBrains Marketplace.

## Recommended Screenshots (in order of importance)

### 1. Syntax Highlighting (`01-syntax-highlighting.png`)

- **File:** `showcase.kite`
- **What to show:** Full editor view with colorful code
- **Features visible:** Purple keywords, blue types, green strings, gray comments, orange decorators
- **Tip:** Collapse the Structure View to focus on code

### 2. Code Completion (`02-code-completion.png`)

- **File:** `completion.kite`
- **What to show:** Completion popup with type suggestions
- **How:** After `var ` type a space, then trigger completion (Ctrl+Space)
- **Features visible:** string, number, boolean, string[], number[] options with icons

### 3. Structure View (`03-structure-view.png`)

- **File:** `showcase.kite`
- **What to show:** Structure panel with hierarchical tree
- **How:** Open Structure tool window (Cmd+7 / Alt+7)
- **Features visible:** Color-coded icons for schema, component, resource, function, variable

### 4. Go to Declaration (`04-go-to-declaration.png`)

- **File:** `showcase.kite`
- **What to show:** Underlined reference when hovering with Cmd/Ctrl
- **How:** Hold Cmd and hover over `defaultRegion` on line 40
- **Features visible:** Underlined clickable reference

### 5. Quick Documentation (`05-quick-documentation.png`)

- **File:** `showcase.kite`
- **What to show:** Documentation popup
- **How:** Place cursor on `WebServer` component and press F1 (Mac) or Ctrl+Q
- **Features visible:** Component documentation with inputs/outputs listed

### 6. Find Usages (`06-find-usages.png`)

- **File:** `showcase.kite`
- **What to show:** Usages popup dropdown
- **How:** Cmd+Click on declaration name like `environment` variable
- **Features visible:** List of usages with file locations

### 7. Error Highlighting (`07-error-highlighting.png`)

- **File:** `type-errors.kite`
- **What to show:** Red squiggles on type errors
- **Features visible:** Error underlines on mismatched types, warning on undefined

### 8. Inlay Hints (`08-inlay-hints.png`)

- **File:** `showcase.kite`
- **What to show:** Inline type hints and parameter hints
- **How:** Make sure Inlay Hints are enabled in Settings
- **Features visible:** `:string` type hints, `name:` parameter hints

### 9. Parameter Info (`09-parameter-info.png`)

- **File:** `completion.kite`
- **What to show:** Parameter tooltip in function call
- **How:** Place cursor inside `greet()` parentheses and press Cmd+P / Ctrl+P
- **Features visible:** Parameter names and types with current param highlighted

### 10. Rename Refactoring (`10-rename-refactoring.png`)

- **File:** `showcase.kite`
- **What to show:** Rename dialog or inline rename mode
- **How:** Select `environment` and press Shift+F6
- **Features visible:** All occurrences highlighted for rename

## Tips for Good Screenshots

1. **Window size:** 1400x900 or similar (not too wide)
2. **Theme:** Use default IntelliJ Light or Darcula theme
3. **Font size:** Increase editor font to 14-16pt for readability
4. **Hide distractions:** Close unnecessary panels, toolbars
5. **Crop:** Focus on the relevant area, don't include entire IDE
6. **Format:** PNG for crisp text

## File Naming

Save screenshots as:

- `01-syntax-highlighting.png`
- `02-code-completion.png`
- `03-structure-view.png`
- etc.

This ordering reflects feature importance for the marketplace listing.
