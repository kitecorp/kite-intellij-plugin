# Kite IntelliJ Plugin - Development Notes

## Development Principles

1. **Apply existing patterns first** - Look for similar working code before creating new solutions
2. **Ask questions when unsure** - Clarify requirements before coding
3. **Listen to user hints** - Try user suggestions first
4. **Debug actual problems** - Focus on what's actually broken
5. **Prefer simple solutions** - Complexity should match the problem
6. **When stuck, step back** - Look at similar working code

## Commands

```bash
./gradlew runIde          # Run plugin in sandbox IDE
./gradlew buildPlugin     # Build plugin ZIP
./gradlew clean build     # Clean rebuild
```

## Testing

- Test files: `examples/` directory
- Main test file: `examples/component.kite`

## Key Implementation Notes

### Formatter Indent Types

```java
Indent.getSpaceIndent(n)      // ABSOLUTE - n spaces from column 0
Indent.getNormalIndent()       // RELATIVE - adds INDENT_SIZE (2 spaces)
Indent.getContinuationIndent() // RELATIVE - adds CONTINUATION_INDENT_SIZE (4 spaces)
```

### Whitespace Handling

Always check for both IntelliJ and Kite whitespace types:
```java
type == TokenType.WHITE_SPACE ||  // IntelliJ platform
type == KiteTokenTypes.NL ||
type == KiteTokenTypes.WHITESPACE
```

### Navigation: Declaration vs Reference

An identifier is a **declaration name** (not navigable) if followed by `=`, `{`, `+=`, or `:`.
Otherwise it's a **reference** (navigable to its declaration).

### Force Clean Builds

```bash
./gradlew clean compileJava --no-build-cache --rerun-tasks
killall -9 java  # Kill lingering IDE instances
```

## File Structure

| Feature | Main File |
|---------|-----------|
| Syntax Highlighting | `highlighting/KiteSyntaxHighlighter.java` |
| Formatter | `formatter/KiteBlock.java` |
| Go to Declaration | `navigation/KiteGotoDeclarationHandler.java` |
| Find Usages | `navigation/KiteNavigatablePsiElement.java` |
| Code Completion | `completion/KiteCompletionContributor.java` |
| Breadcrumbs | `KiteBreadcrumbsProvider.java` |
| Quick Documentation | `documentation/KiteDocumentationProvider.java` |
| Structure View | `structure/KiteStructureViewElement.java` |
| References | `reference/KiteReferenceContributor.java` |
