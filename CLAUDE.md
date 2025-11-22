# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kite IntelliJ IDEA plugin. Kite provides AI-powered code completions and documentation for developers.

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

### Key Components

IntelliJ plugins typically use these patterns:

**Actions**: User-triggered operations that extend `AnAction`. Register in plugin.xml under `<actions>`.

**Extensions**: Extend IDE functionality by implementing extension points. Register in plugin.xml under `<extensions>`.

**Services**: Singleton components managed by the platform. Can be application-level, project-level, or module-level.

**Listeners**: React to IDE events (file changes, project opening, etc.).

### Testing

Run specific test classes:
```bash
./gradlew test --tests "com.kite.intellij.SpecificTestClass"
```

### Debugging

The `runIde` task launches a separate IDE instance with the plugin installed. You can attach a debugger to this instance for debugging plugin code.

## IntelliJ Platform SDK

This plugin uses the IntelliJ Platform SDK. Key concepts:

- **PSI (Program Structure Interface)**: The layer that parses code and creates a syntax tree
- **VirtualFile**: Represents files in the IDE's virtual file system
- **Document**: Text representation of a file
- **Editor**: The component for viewing and editing documents
- **Project/Module**: Organizational structures

When working with PSI or documents, always use read/write actions and invoke on the correct thread (EDT for UI, read action for reading PSI, write action for modifying PSI).

## Platform Version Compatibility

Check `build.gradle` or `build.gradle.kts` for the target IntelliJ platform version and compatibility range. The plugin may need to support multiple IDE versions.