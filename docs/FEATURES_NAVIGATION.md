# Navigation & Structure

[Back to Features Index](Features.md)

## Go to Class (Cmd+O / Ctrl+N)

Navigate to schemas and components by name:

- Opens a popup to search by name
- Fuzzy matching supported
- Shows file location
- Works across all project files

**Finds:**
- Schemas
- Components

## Go to Symbol (Cmd+Alt+O / Ctrl+Alt+Shift+N)

Navigate to any declaration by name:

- Opens a popup to search by name
- Fuzzy matching supported
- Shows file location and type icon
- Works across all project files

**Finds:**
- Schemas
- Components
- Functions
- Variables
- Resources
- Type aliases

## Go to Declaration (Cmd+Click / Ctrl+Click)

Navigate to the definition of:

- Variables, inputs, outputs
- Functions
- Schemas
- Components
- Resource and component instances
- Imported symbols (navigates to source file)
- Function parameters (within function body)
- Resource properties (navigates to schema property definition)

## Find Usages

When clicking on a declaration, all usages are highlighted:

- Standard identifier references
- String interpolation usage (`$var` and `${var}`)

## Breadcrumbs

Shows hierarchical path in editor header:

- File > Schema/Component/Function > Property/Parameter

## Structure View

### View Hierarchy

Shows file structure in tool window with:

- Imports
- Schemas and their properties
- Components and their inputs/outputs
- Functions
- Resources
- Variables

### Icons

| Element   | Color      | Letter |
|-----------|------------|--------|
| Resource  | Purple     | R      |
| Component | Blue       | C      |
| Schema    | Green      | S      |
| Function  | Orange     | F      |
| Type      | Blue       | T      |
| Variable  | Purple     | V      |
| Input     | Amber      | I      |
| Output    | Lime       | O      |
| Import    | Brown      | M      |
| Property  | Cornflower | P      |

## Implementation Files

| Feature        | File                                         |
|----------------|----------------------------------------------|
| Navigation     | `navigation/KiteGotoDeclarationHandler.java` |
| Go to Class    | `navigation/KiteGotoClassContributor.java`   |
| Go to Symbol   | `navigation/KiteGotoSymbolContributor.java`  |
| Structure View | `structure/KiteStructureViewElement.java`    |
| Structure Icons| `structure/KiteStructureViewIcons.java`      |

## Test Files

| Feature          | Test File                      |
|------------------|--------------------------------|
| Go to Class/Symbol | `KiteGotoContributorTest.java` |
