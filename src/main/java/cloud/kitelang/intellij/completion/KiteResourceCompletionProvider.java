package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Provides code completion inside resource blocks.
 * Handles:
 * - Schema property name completion (left side of =)
 * - Value completion (right side of =)
 * - Property access on resources (resource.property)
 */
public class KiteResourceCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        // Check if we're inside a resource block
        ResourceContext resourceContext = getEnclosingResourceContext(position);
        if (resourceContext == null) {
            return; // Not in a resource block
        }

        // Check if we're on the LEFT side of = (property name) or RIGHT side (value)
        if (isBeforeAssignment(position)) {
            // LEFT side: only schema properties (non-@cloud)
            addSchemaPropertyCompletions(file, result, resourceContext);
        } else {
            // RIGHT side: variables, resources, components, functions
            addValueCompletions(file, result);
        }
    }

    /**
     * Check if position is inside a resource block.
     * Public static method for other providers to check resource context.
     */
    public static boolean isInResourceContext(@NotNull PsiElement position) {
        return getEnclosingResourceContext(position) != null;
    }

    // ========== Resource Context Detection ==========

    /**
     * Context information about an enclosing resource block.
     */
    static class ResourceContext {
        final String schemaName;
        final PsiElement resourceDeclaration;

        ResourceContext(String schemaName, PsiElement resourceDeclaration) {
            this.schemaName = schemaName;
            this.resourceDeclaration = resourceDeclaration;
        }
    }

    /**
     * Get the resource context if we're inside a resource block.
     * Returns null if not inside a resource block.
     */
    @Nullable
    static ResourceContext getEnclosingResourceContext(PsiElement position) {
        // Walk up the PSI tree to find an enclosing RESOURCE_DECLARATION
        PsiElement current = position;
        while (current != null) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.RESOURCE_DECLARATION) {
                // Found resource declaration - check if we're inside the braces
                if (isInsideBraces(position, current)) {
                    String schemaName = extractResourceTypeName(current);
                    if (schemaName != null) {
                        return new ResourceContext(schemaName, current);
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if position is inside the braces of a declaration.
     */
    static boolean isInsideBraces(PsiElement position, PsiElement declaration) {
        int posOffset = position.getTextOffset();

        // Find LBRACE and RBRACE positions
        int lbraceOffset = -1;
        int rbraceOffset = -1;

        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE && lbraceOffset == -1) {
                    lbraceOffset = child.getTextOffset();
                } else if (type == KiteTokenTypes.RBRACE) {
                    rbraceOffset = child.getTextOffset();
                }
            }
            child = child.getNextSibling();
        }

        return lbraceOffset != -1 && rbraceOffset != -1 &&
               posOffset > lbraceOffset && posOffset < rbraceOffset;
    }

    /**
     * Check if the cursor is before the assignment operator (on the left side of =).
     * Returns true if we're typing a property name, false if we're typing a value.
     */
    static boolean isBeforeAssignment(PsiElement position) {
        // Walk backward to see if there's an '=' on the same line before us
        PsiElement current = position.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();

                // If we hit a newline, we're at the start of a new property - on left side
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    return true;
                }

                // If we hit an '=', we're on the right side (value position)
                if (type == KiteTokenTypes.ASSIGN) {
                    return false;
                }
            }
            current = current.getPrevSibling();
        }

        // Also check parent's siblings
        PsiElement parent = position.getParent();
        if (parent != null) {
            current = parent.getPrevSibling();
            while (current != null) {
                if (current.getNode() != null) {
                    IElementType type = current.getNode().getElementType();

                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                        return true;
                    }

                    if (type == KiteTokenTypes.ASSIGN) {
                        return false;
                    }
                }
                current = current.getPrevSibling();
            }
        }

        // Default to left side (property name position)
        return true;
    }

    /**
     * Extract the resource type name from a resource declaration.
     * Pattern: resource TypeName instanceName { ... }
     */
    @Nullable
    static String extractResourceTypeName(PsiElement resourceDecl) {
        boolean foundResource = false;
        PsiElement child = resourceDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.RESOURCE) {
                    foundResource = true;
                } else if (foundResource && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier is the type name
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    // ========== Schema Property Completion ==========

    /**
     * Information about a schema property.
     */
    static class SchemaPropertyInfo {
        final String type;
        final boolean isCloud;

        SchemaPropertyInfo(String type, boolean isCloud) {
            this.type = type;
            this.isCloud = isCloud;
        }
    }

    /**
     * Add schema property completions with high priority.
     * Excludes properties that are already defined in the resource block.
     * Excludes @cloud properties (they are set by the cloud provider).
     */
    private void addSchemaPropertyCompletions(PsiFile file, @NotNull CompletionResultSet result, ResourceContext resourceContext) {
        Map<String, SchemaPropertyInfo> schemaProperties = findSchemaProperties(file, resourceContext.schemaName);

        // Collect already-defined properties in the resource block
        Set<String> existingProperties = collectExistingPropertyNames(resourceContext.resourceDeclaration);

        for (Map.Entry<String, SchemaPropertyInfo> entry : schemaProperties.entrySet()) {
            String propertyName = entry.getKey();
            SchemaPropertyInfo propInfo = entry.getValue();

            // Skip properties that are already defined in the resource
            if (existingProperties.contains(propertyName)) {
                continue;
            }

            // Skip @cloud properties - they are set by the cloud provider, not by the user
            if (propInfo.isCloud) {
                continue;
            }

            // Create lookup element with high priority
            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(propInfo.type)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(true)
                    .withInsertHandler((ctx, item) -> {
                        // Add " = " after property name
                        ctx.getDocument().insertString(ctx.getTailOffset(), " = ");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                    });

            // Use PrioritizedLookupElement to boost priority
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }
    }

    /**
     * Collect the names of properties already defined in a resource block.
     */
    private Set<String> collectExistingPropertyNames(PsiElement resourceDecl) {
        Set<String> propertyNames = new HashSet<>();
        int braceDepth = 0;

        PsiElement child = resourceDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                } else if (type == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                } else if (braceDepth == 1 && type == KiteTokenTypes.IDENTIFIER) {
                    // Check if this identifier is followed by = (it's a property definition)
                    PsiElement next = KiteCompletionHelper.skipWhitespaceForward(child.getNextSibling());
                    if (next != null && next.getNode() != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.PLUS_ASSIGN) {
                            propertyNames.add(child.getText());
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }

        return propertyNames;
    }

    /**
     * Find schema properties by name. Returns a map of property name to SchemaPropertyInfo.
     * Searches in current file and imported files.
     */
    private Map<String, SchemaPropertyInfo> findSchemaProperties(PsiFile file, String schemaName) {
        Map<String, SchemaPropertyInfo> properties = new HashMap<>();

        // Search in current file
        findSchemaPropertiesRecursive(file, schemaName, properties);

        // If not found, search in imported files
        if (properties.isEmpty()) {
            findSchemaPropertiesInImports(file, schemaName, properties, new HashSet<>());
        }

        return properties;
    }

    /**
     * Recursively search for a schema and extract its properties.
     */
    private void findSchemaPropertiesRecursive(PsiElement element, String schemaName, Map<String, SchemaPropertyInfo> properties) {
        if (element == null || element.getNode() == null) return;

        if (element.getNode().getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            // Check if this is the schema we're looking for
            String name = extractSchemaName(element);
            if (schemaName.equals(name)) {
                extractSchemaProperties(element, properties);
                return;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findSchemaPropertiesRecursive(child, schemaName, properties);
            if (!properties.isEmpty()) return; // Found it
            child = child.getNextSibling();
        }
    }

    /**
     * Search for schema properties in imported files.
     */
    private void findSchemaPropertiesInImports(PsiFile file, String schemaName,
                                               Map<String, SchemaPropertyInfo> properties, Set<String> visited) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null || importedFile.getVirtualFile() == null) continue;

            String path = importedFile.getVirtualFile().getPath();
            if (visited.contains(path)) continue;
            visited.add(path);

            findSchemaPropertiesRecursive(importedFile, schemaName, properties);
            if (!properties.isEmpty()) return;

            // Recursively check imports
            findSchemaPropertiesInImports(importedFile, schemaName, properties, visited);
            if (!properties.isEmpty()) return;
        }
    }

    /**
     * Get the schema name from a schema declaration.
     */
    @Nullable
    private String extractSchemaName(PsiElement schemaDecl) {
        boolean foundSchema = false;
        PsiElement child = schemaDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.SCHEMA) {
                    foundSchema = true;
                } else if (foundSchema && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Extract property definitions from a schema.
     * Pattern inside schema: [@cloud] type propertyName [= defaultValue]
     * Properties with @cloud annotation are marked as cloud properties.
     */
    private void extractSchemaProperties(PsiElement schemaDecl, Map<String, SchemaPropertyInfo> properties) {
        boolean insideBraces = false;
        String currentType = null;
        boolean isCloudProperty = false;

        PsiElement child = schemaDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBraces) {
                    // Skip whitespace and newlines
                    if (type == KiteTokenTypes.WHITESPACE ||
                        type == KiteTokenTypes.NL ||
                        type == KiteTokenTypes.NEWLINE ||
                        type == com.intellij.psi.TokenType.WHITE_SPACE) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Check for @cloud annotation
                    if (type == KiteTokenTypes.AT) {
                        // Look at next sibling to see if it's "cloud"
                        PsiElement next = KiteCompletionHelper.skipWhitespaceForward(child.getNextSibling());
                        if (next != null && next.getNode() != null &&
                            next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER &&
                            "cloud".equals(next.getText())) {
                            isCloudProperty = true;
                        }
                        child = child.getNextSibling();
                        continue;
                    }

                    // Handle 'any' keyword as a type
                    if (type == KiteTokenTypes.ANY) {
                        currentType = "any";
                        child = child.getNextSibling();
                        continue;
                    }

                    // Track type -> name pattern
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        String text = child.getText();
                        // Skip "cloud" if we just saw @
                        if ("cloud".equals(text) && isCloudProperty) {
                            child = child.getNextSibling();
                            continue;
                        }
                        if (currentType == null) {
                            // First identifier is the type
                            currentType = text;
                        } else {
                            // Second identifier is the property name
                            properties.put(text, new SchemaPropertyInfo(currentType, isCloudProperty));
                            currentType = null;
                            isCloudProperty = false; // Reset for next property
                        }
                    }

                    // Reset on newline or assignment (end of property definition)
                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.ASSIGN) {
                        currentType = null;
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    // ========== Value Completion (Right Side of =) ==========

    /**
     * Add value completions for the right side of assignments in resource blocks.
     * Shows variables, inputs, outputs, resources, components, and functions in that priority order.
     * Includes items from imported files.
     */
    private void addValueCompletions(PsiFile file, @NotNull CompletionResultSet result) {
        Set<String> addedNames = new HashSet<>();

        // 1. Variables (highest priority)
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.VARIABLE_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("variable")
                        .withIcon(KiteStructureViewIcons.VARIABLE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 500.0));
            }
        });

        // 2. Inputs
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.INPUT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("input")
                        .withIcon(KiteStructureViewIcons.INPUT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 450.0));
            }
        });

        // 3. Outputs
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.OUTPUT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("output")
                        .withIcon(KiteStructureViewIcons.OUTPUT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 400.0));
            }
        });

        // 4. Resources
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.RESOURCE_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("resource")
                        .withIcon(KiteStructureViewIcons.RESOURCE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 300.0));
            }
        });

        // 5. Components
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.COMPONENT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("component")
                        .withIcon(KiteStructureViewIcons.COMPONENT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 200.0));
            }
        });

        // 6. Functions (lowest priority among these)
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.FUNCTION_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("function")
                        .withIcon(KiteStructureViewIcons.FUNCTION);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0));
            }
        });

        // Also add from imported files with slightly lower priority
        addValueCompletionsFromImports(file, result, addedNames);
    }

    /**
     * Add value completions from imported files.
     */
    private void addValueCompletionsFromImports(PsiFile file, @NotNull CompletionResultSet result, Set<String> addedNames) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null) continue;

            // Variables from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.VARIABLE_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("variable")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.VARIABLE);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 450.0));
                }
            });

            // Inputs from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.INPUT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("input")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.INPUT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 400.0));
                }
            });

            // Outputs from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.OUTPUT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("output")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.OUTPUT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 350.0));
                }
            });

            // Resources from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.RESOURCE_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("resource")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.RESOURCE);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 250.0));
                }
            });

            // Components from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.COMPONENT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("component")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.COMPONENT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 150.0));
                }
            });

            // Functions from imports
            collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.FUNCTION_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("function")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.FUNCTION);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 50.0));
                }
            });
        }
    }

    // ========== Declaration Collection Utilities ==========

    /**
     * Collect all declarations from the file.
     */
    private void collectDeclarations(PsiFile file, DeclarationVisitor visitor) {
        collectDeclarationsRecursive(file, visitor);
    }

    private void collectDeclarationsRecursive(PsiElement element, DeclarationVisitor visitor) {
        if (element == null) return;

        IElementType elementType = element.getNode().getElementType();

        if (isDeclarationType(elementType)) {
            String name = findNameInDeclaration(element, elementType);
            if (name != null && !name.isEmpty()) {
                visitor.visit(name, elementType, element);
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            collectDeclarationsRecursive(child, visitor);
            child = child.getNextSibling();
        }
    }

    /**
     * Check if an element type is a declaration.
     */
    private boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION;
    }

    /**
     * Find the name in a declaration.
     */
    private String findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        // Function special case: "fun functionName(...) returnType {"
        if (declarationType == KiteElementTypes.FUNCTION_DECLARATION) {
            boolean foundFun = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                } else if (childType == KiteTokenTypes.LPAREN) {
                    break;
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // For other declarations: find the last identifier before '=' or '{'
        String lastIdentifier = null;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child.getText();
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

    @FunctionalInterface
    private interface DeclarationVisitor {
        void visit(String name, IElementType declarationType, PsiElement element);
    }
}
