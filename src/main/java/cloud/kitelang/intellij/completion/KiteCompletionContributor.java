package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Provides code completion for the Kite language.
 *
 * Completion types:
 * - Keywords (resource, component, var, input, output, etc.)
 * - Declared identifiers (variables, resources, components, etc.)
 * - Property access (object.property)
 * - Built-in types (string, number, boolean, etc.)
 */
public class KiteCompletionContributor extends CompletionContributor {

    // Keywords that can start a declaration or statement
    private static final String[] TOP_LEVEL_KEYWORDS = {
        "resource", "component", "schema", "fun", "type",
        "var", "input", "output", "import"
    };

    // Control flow keywords
    private static final String[] CONTROL_KEYWORDS = {
        "if", "else", "for", "while", "in", "return"
    };

    // Built-in types
    private static final String[] BUILTIN_TYPES = {
        "string", "number", "boolean", "object", "any"
    };

    // Built-in array types
    private static final String[] BUILTIN_ARRAY_TYPES = {
            "string[]", "number[]", "boolean[]", "object[]", "any[]"
    };

    // Literals
    private static final String[] LITERAL_KEYWORDS = {
        "true", "false", "null", "this"
    };

    public KiteCompletionContributor() {
        // General completion provider for Kite files
        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(KiteLanguage.INSTANCE),
            new CompletionProvider<>() {
                @Override
                protected void addCompletions(@NotNull CompletionParameters parameters,
                                              @NotNull ProcessingContext context,
                                              @NotNull CompletionResultSet result) {
                    PsiElement position = parameters.getPosition();
                    PsiElement originalPosition = parameters.getOriginalPosition();

                    // Check if we're after a dot (property access)
                    if (isPropertyAccessContext(position)) {
                        addPropertyCompletions(parameters, result);
                        return;
                    }

                    // Check if we're in a type position
                    if (isTypeContext(position)) {
                        addTypeCompletions(result);
                        addDeclaredTypeCompletions(parameters.getOriginalFile(), result);
                        return;
                    }

                    // Check if we're inside a resource block
                    ResourceContext resourceContext = getEnclosingResourceContext(position);
                    if (resourceContext != null) {
                        // Check if we're on the LEFT side of = (property name) or RIGHT side (value)
                        if (isBeforeAssignment(position)) {
                            // LEFT side: only schema properties (non-@cloud)
                            addSchemaPropertyCompletions(parameters.getOriginalFile(), result, resourceContext);
                            return;
                        } else {
                            // RIGHT side: variables, resources, components, functions
                            addValueCompletions(parameters.getOriginalFile(), result);
                            return;
                        }
                    }

                    // Add keyword completions
                    addKeywordCompletions(result, position);

                    // Add declared identifier completions
                    addIdentifierCompletions(parameters.getOriginalFile(), result, position);
                }
            }
        );
    }

    /**
     * Check if cursor is after a dot (property access context)
     */
    private boolean isPropertyAccessContext(PsiElement position) {
        PsiElement prev = skipWhitespaceBackward(position.getPrevSibling());
        if (prev == null) {
            // Check parent's previous sibling
            PsiElement parent = position.getParent();
            if (parent != null) {
                prev = skipWhitespaceBackward(parent.getPrevSibling());
            }
        }

        if (prev != null && prev.getNode() != null) {
            return prev.getNode().getElementType() == KiteTokenTypes.DOT;
        }
        return false;
    }

    /**
     * Check if cursor is in a type position (after var, input, output, etc.)
     */
    private boolean isTypeContext(PsiElement position) {
        PsiElement prev = skipWhitespaceBackward(position.getPrevSibling());
        if (prev == null) {
            PsiElement parent = position.getParent();
            if (parent != null) {
                prev = skipWhitespaceBackward(parent.getPrevSibling());
            }
        }

        if (prev != null && prev.getNode() != null) {
            IElementType prevType = prev.getNode().getElementType();
            return prevType == KiteTokenTypes.VAR ||
                   prevType == KiteTokenTypes.INPUT ||
                   prevType == KiteTokenTypes.OUTPUT ||
                   prevType == KiteTokenTypes.RESOURCE ||
                   prevType == KiteTokenTypes.COLON;  // For function return types
        }
        return false;
    }

    /**
     * Add keyword completions based on context
     */
    private void addKeywordCompletions(@NotNull CompletionResultSet result, PsiElement position) {
        // Top-level keywords
        for (String keyword : TOP_LEVEL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }

        // Control flow keywords
        for (String keyword : CONTROL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }

        // Literals
        for (String keyword : LITERAL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }
    }

    /**
     * Add built-in type completions
     */
    private void addTypeCompletions(@NotNull CompletionResultSet result) {
        // Basic types (higher priority)
        for (String type : BUILTIN_TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withTypeText("type")
                    .withBoldness(true)
                    .withIcon(KiteStructureViewIcons.TYPE);
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }

        // Array types (slightly lower priority so they appear after basic types)
        for (String type : BUILTIN_ARRAY_TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withTypeText("array type")
                    .withBoldness(true)
                    .withIcon(KiteStructureViewIcons.TYPE);
            result.addElement(PrioritizedLookupElement.withPriority(element, 90.0));
        }
    }

    /**
     * Add completions for declared types (schemas, type aliases)
     */
    private void addDeclaredTypeCompletions(PsiFile file, @NotNull CompletionResultSet result) {
        collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.SCHEMA_DECLARATION ||
                declarationType == KiteElementTypes.TYPE_DECLARATION ||
                declarationType == KiteElementTypes.COMPONENT_DECLARATION) {
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withTypeText(getTypeTextForDeclaration(declarationType))
                        .withIcon(getIconForDeclaration(declarationType))
                );
            }
        });
    }

    /**
     * Add completions for declared identifiers (variables, resources, etc.)
     */
    private void addIdentifierCompletions(PsiFile file, @NotNull CompletionResultSet result, PsiElement position) {
        Set<String> addedNames = new HashSet<>();

        collectDeclarations(file, (name, declarationType, element) -> {
            if (!addedNames.contains(name)) {
                addedNames.add(name);
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withTypeText(getTypeTextForDeclaration(declarationType))
                        .withIcon(getIconForDeclaration(declarationType))
                );
            }
        });

        // Also collect for-loop variables
        collectForLoopVariables(file, (name, element) -> {
            if (!addedNames.contains(name)) {
                addedNames.add(name);
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withTypeText("loop variable")
                        .withIcon(KiteStructureViewIcons.VARIABLE)
                );
            }
        });
    }

    /**
     * Add property completions for object.property access (supports chained access like server.tag.)
     * For resources, shows all schema properties with initialized ones bold and first.
     */
    private void addPropertyCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        // Build the property chain by walking backwards (e.g., "server.tag" -> ["server", "tag"])
        List<String> chain = buildPropertyChain(position);
        if (chain.isEmpty()) return;

        // Start with the first element (should be a declaration)
        String rootName = chain.get(0);
        PsiElement declaration = findDeclaration(file, rootName);

        if (declaration == null) return;

        // Check if this is a resource declaration - if so, show all schema properties
        if (declaration.getNode().getElementType() == KiteElementTypes.RESOURCE_DECLARATION && chain.size() == 1) {
            addResourcePropertyCompletions(file, result, declaration);
            return;
        }

        // Check if this is a component instantiation (e.g., "component WebServer serviceA { ... }")
        // If so, we need to get outputs from the component TYPE declaration, not the instance
        PsiElement currentContext = declaration;
        if (declaration.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
            if (isComponentInstantiation(declaration)) {
                // Get the component type name (e.g., "WebServer" from "component WebServer serviceA")
                String componentTypeName = getComponentTypeName(declaration);
                if (componentTypeName != null) {
                    // Find the component TYPE declaration (e.g., "component WebServer { ... }")
                    PsiElement componentDeclaration = findComponentDeclaration(file, componentTypeName);
                    if (componentDeclaration != null) {
                        currentContext = componentDeclaration;
                    }
                }
            }
        }

        // Navigate through the chain for nested properties
        for (int i = 1; i < chain.size(); i++) {
            String propertyName = chain.get(i);
            currentContext = findPropertyValue(currentContext, propertyName);
            if (currentContext == null) return;
        }

        // Collect properties from the final context
        collectPropertiesFromContext(currentContext, (propertyName, propertyElement) -> {
            result.addElement(
                LookupElementBuilder.create(propertyName)
                    .withTypeText("property")
                    .withIcon(KiteStructureViewIcons.PROPERTY)
            );
        });
    }

    /**
     * Add property completions for resource property access (photos.host).
     * Shows all schema properties with:
     * - Initialized properties shown bold and first in the list
     * - Uninitialized properties shown normally
     * - Cloud properties are included (they can be read, just not set)
     */
    private void addResourcePropertyCompletions(PsiFile file, @NotNull CompletionResultSet result, PsiElement resourceDecl) {
        // Get the schema/type name for this resource
        String schemaName = extractResourceTypeName(resourceDecl);
        if (schemaName == null) return;

        // Get all schema properties
        Map<String, SchemaPropertyInfo> schemaProperties = findSchemaProperties(file, schemaName);

        // Get properties that are initialized in the resource
        Set<String> initializedProperties = collectExistingPropertyNames(resourceDecl);

        // Add initialized properties first (bold, higher priority)
        for (String propertyName : initializedProperties) {
            SchemaPropertyInfo propInfo = schemaProperties.get(propertyName);
            String typeText = propInfo != null ? propInfo.type : "property";

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(typeText)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(true);

            // Higher priority for initialized properties
            result.addElement(PrioritizedLookupElement.withPriority(element, 200.0));
        }

        // Add remaining schema properties (not bold, lower priority)
        for (Map.Entry<String, SchemaPropertyInfo> entry : schemaProperties.entrySet()) {
            String propertyName = entry.getKey();

            // Skip if already added as initialized
            if (initializedProperties.contains(propertyName)) {
                continue;
            }

            SchemaPropertyInfo propInfo = entry.getValue();
            String typeText = propInfo.type;

            // Add cloud indicator to type text
            if (propInfo.isCloud) {
                typeText = typeText + " (cloud)";
            }

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(typeText)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(false);

            // Lower priority for non-initialized properties
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }

        // Also add any custom properties defined in the resource but not in schema
        // (in case the schema doesn't capture all properties)
        for (String propertyName : initializedProperties) {
            if (!schemaProperties.containsKey(propertyName)) {
                LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                        .withTypeText("property")
                        .withIcon(KiteStructureViewIcons.PROPERTY)
                        .withBoldness(true);

                result.addElement(PrioritizedLookupElement.withPriority(element, 200.0));
            }
        }
    }

    /**
     * Check if a component declaration is an instantiation (has both type and instance name)
     * e.g., "component WebServer serviceA { ... }" is an instantiation
     * e.g., "component WebServer { ... }" is a declaration (defines the component)
     */
    private boolean isComponentInstantiation(PsiElement componentDecl) {
        // Count identifiers before the opening brace
        int identifierCount = 0;
        PsiElement child = componentDecl.getFirstChild();
        while (child != null) {
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.IDENTIFIER) {
                identifierCount++;
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
            child = child.getNextSibling();
        }
        // Instantiation has 2 identifiers: TypeName and instanceName
        // Declaration has 1 identifier: TypeName
        return identifierCount >= 2;
    }

    /**
     * Get the component type name from a component instantiation
     * e.g., "component WebServer serviceA { ... }" returns "WebServer"
     */
    private String getComponentTypeName(PsiElement componentDecl) {
        // The first identifier after "component" keyword is the type name
        boolean foundComponent = false;
        PsiElement child = componentDecl.getFirstChild();
        while (child != null) {
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.COMPONENT) {
                foundComponent = true;
            } else if (foundComponent && type == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Find a component declaration (not instantiation) by type name
     * e.g., find "component WebServer { ... }" (the declaration that defines WebServer)
     */
    private PsiElement findComponentDeclaration(PsiFile file, String typeName) {
        final PsiElement[] result = {null};

        collectDeclarations(file, (declName, declarationType, element) -> {
            if (declarationType == KiteElementTypes.COMPONENT_DECLARATION) {
                // Check if this is a declaration (not instantiation) with matching type name
                if (!isComponentInstantiation(element)) {
                    String componentTypeName = getComponentTypeName(element);
                    if (typeName.equals(componentTypeName)) {
                        result[0] = element;
                    }
                }
            }
        });

        return result[0];
    }

    /**
     * Build a chain of property names from the cursor position backwards
     * e.g., "server.tag." returns ["server", "tag"]
     */
    private List<String> buildPropertyChain(PsiElement position) {
        List<String> chain = new ArrayList<>();

        PsiElement current = position;

        // Walk backwards collecting identifiers and dots
        while (true) {
            // Skip to find the dot before current position
            PsiElement dot = findPreviousDot(current);
            if (dot == null) break;

            // Find the identifier before the dot
            PsiElement identifier = skipWhitespaceBackward(dot.getPrevSibling());
            if (identifier == null || identifier.getNode() == null) break;

            IElementType type = identifier.getNode().getElementType();
            if (type == KiteTokenTypes.IDENTIFIER) {
                chain.add(0, identifier.getText()); // Add to front
                current = identifier;
            } else {
                break;
            }
        }

        return chain;
    }

    /**
     * Find the previous dot token relative to the given element
     */
    private PsiElement findPreviousDot(PsiElement element) {
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        if (prev == null) {
            PsiElement parent = element.getParent();
            if (parent != null) {
                prev = skipWhitespaceBackward(parent.getPrevSibling());
            }
        }
        if (prev != null && prev.getNode() != null &&
            prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            return prev;
        }
        return null;
    }

    /**
     * Find the value element of a property within a context
     * e.g., for "tag = { ... }", returns the object literal element
     */
    private PsiElement findPropertyValue(PsiElement context, String propertyName) {
        final PsiElement[] result = {null};

        // Search for the property in the context
        visitPropertiesInContext(context, (name, valueElement) -> {
            if (name.equals(propertyName) && result[0] == null) {
                result[0] = valueElement;
            }
        });

        return result[0];
    }

    /**
     * Visit properties in a context and get their value elements
     */
    private void visitPropertiesInContext(PsiElement context, PropertyValueVisitor visitor) {
        int braceDepth = 0;
        PsiElement child = context.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                braceDepth--;
            } else if (braceDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                // Found a property at depth 1
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        // Find the value after = or :
                        PsiElement value = skipWhitespaceForward(next.getNextSibling());
                        if (value != null) {
                            visitor.visit(child.getText(), value);
                        }
                    }
                }
            }

            // Recurse into nested PSI elements but track brace depth
            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                visitPropertiesInContextRecursive(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private void visitPropertiesInContextRecursive(PsiElement element, PropertyValueVisitor visitor, int currentDepth) {
        PsiElement child = element.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                currentDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentDepth--;
            } else if (currentDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        PsiElement value = skipWhitespaceForward(next.getNextSibling());
                        if (value != null) {
                            visitor.visit(child.getText(), value);
                        }
                    }
                }
            }

            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                visitPropertiesInContextRecursive(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Collect properties from a context element (could be a declaration or object literal)
     */
    private void collectPropertiesFromContext(PsiElement context, PropertyVisitor visitor) {
        // If context is an object literal (starts with {), collect its properties
        if (context.getNode().getElementType() == KiteElementTypes.OBJECT_LITERAL ||
            context.getNode().getElementType() == KiteTokenTypes.LBRACE ||
            (context.getText() != null && context.getText().trim().startsWith("{"))) {
            collectPropertiesFromObjectLiteral(context, visitor);
        } else {
            // It's a declaration, use existing method
            collectPropertiesFromDeclaration(context, visitor);
        }
    }

    /**
     * Collect properties from an object literal
     */
    private void collectPropertiesFromObjectLiteral(PsiElement objectLiteral, PropertyVisitor visitor) {
        int braceDepth = 0;
        PsiElement child = objectLiteral.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                braceDepth--;
            } else if (braceDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        visitor.visit(child.getText(), child);
                    }
                }
            }

            if (child.getFirstChild() != null) {
                collectPropertiesFromObjectLiteralRecursive(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private void collectPropertiesFromObjectLiteralRecursive(PsiElement element, PropertyVisitor visitor, int currentDepth) {
        PsiElement child = element.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                currentDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentDepth--;
            } else if (currentDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        visitor.visit(child.getText(), child);
                    }
                }
            }

            if (child.getFirstChild() != null) {
                collectPropertiesFromObjectLiteralRecursive(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    @FunctionalInterface
    private interface PropertyValueVisitor {
        void visit(String name, PsiElement valueElement);
    }

    /**
     * Create a lookup element for a keyword
     */
    private LookupElement createKeywordLookup(String keyword) {
        return LookupElementBuilder.create(keyword)
            .withBoldness(true)
            .withTypeText("keyword");
    }

    /**
     * Collect all declarations from the file
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
     * Collect for-loop variables
     */
    private void collectForLoopVariables(PsiFile file, ForLoopVariableVisitor visitor) {
        collectForLoopVariablesRecursive(file, visitor);
    }

    private void collectForLoopVariablesRecursive(PsiElement element, ForLoopVariableVisitor visitor) {
        if (element == null) return;

        IElementType elementType = element.getNode().getElementType();

        if (elementType == KiteElementTypes.FOR_STATEMENT) {
            // Find identifier after "for" keyword
            boolean foundFor = false;
            PsiElement child = element.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    visitor.visit(child.getText(), child);
                    break;
                } else if (childType == KiteTokenTypes.IN) {
                    break;  // Stop if we hit "in" keyword
                }
                child = child.getNextSibling();
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            collectForLoopVariablesRecursive(child, visitor);
            child = child.getNextSibling();
        }
    }

    /**
     * Find a declaration by name
     */
    private PsiElement findDeclaration(PsiFile file, String name) {
        final PsiElement[] result = {null};

        collectDeclarations(file, (declName, declarationType, element) -> {
            if (name.equals(declName) && result[0] == null) {
                result[0] = element;
            }
        });

        return result[0];
    }

    /**
     * Collect only direct properties from inside a declaration's braces (not nested)
     * For server., should show "size" and "tag" but NOT "Environment", "Name", etc.
     * For components, only collects OUTPUT declarations (inputs are not accessible from outside).
     */
    private void collectPropertiesFromDeclaration(PsiElement declaration, PropertyVisitor visitor) {
        // Check if this is a component declaration
        boolean isComponent = declaration.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION;

        int braceDepth = 0;
        PsiElement child = declaration.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                braceDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                braceDepth--;
            } else if (braceDepth == 1) {
                // For components: only OUTPUT_DECLARATION is accessible from outside
                // (inputs are parameters passed IN, outputs are values exposed OUT)
                if (isComponent && childType == KiteElementTypes.OUTPUT_DECLARATION) {
                    String name = findNameInDeclaration(child, childType);
                    if (name != null && !name.isEmpty()) {
                        visitor.visit(name, child);
                    }
                }
                // For non-components: collect INPUT_DECLARATION and OUTPUT_DECLARATION
                else if (!isComponent && (childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION)) {
                    String name = findNameInDeclaration(child, childType);
                    if (name != null && !name.isEmpty()) {
                        visitor.visit(name, child);
                    }
                }
                // Check for identifier followed by = or : (for resources)
                else if (childType == KiteTokenTypes.IDENTIFIER) {
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
                    if (next != null) {
                        IElementType nextType = next.getNode().getElementType();
                        if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                            visitor.visit(child.getText(), child);
                        }
                    }
                }
            }

            // Recurse into nested PSI elements but track brace depth
            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                collectPropertiesAtDepth(child, visitor, braceDepth);
            }

            child = child.getNextSibling();
        }
    }

    private void collectPropertiesAtDepth(PsiElement element, PropertyVisitor visitor, int currentDepth) {
        PsiElement child = element.getFirstChild();

        while (child != null) {
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                currentDepth++;
            } else if (childType == KiteTokenTypes.RBRACE) {
                currentDepth--;
            } else if (currentDepth == 1 && childType == KiteTokenTypes.IDENTIFIER) {
                // Only collect at depth 1 (direct children)
                PsiElement next = skipWhitespaceForward(child.getNextSibling());
                if (next != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN || nextType == KiteTokenTypes.COLON) {
                        visitor.visit(child.getText(), child);
                    }
                }
            }

            if (child.getFirstChild() != null && !isDeclarationType(childType)) {
                collectPropertiesAtDepth(child, visitor, currentDepth);
            }

            child = child.getNextSibling();
        }
    }

    /**
     * Find the dot token before the current position
     */
    private PsiElement findDotBeforePosition(PsiElement position) {
        PsiElement prev = skipWhitespaceBackward(position.getPrevSibling());
        if (prev != null && prev.getNode() != null &&
            prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            return prev;
        }

        // Check parent's context
        PsiElement parent = position.getParent();
        if (parent != null) {
            prev = skipWhitespaceBackward(parent.getPrevSibling());
            if (prev != null && prev.getNode() != null &&
                prev.getNode().getElementType() == KiteTokenTypes.DOT) {
                return prev;
            }
        }

        return null;
    }

    /**
     * Check if an element type is a declaration
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
     * Find the name in a declaration
     */
    private String findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        // For-loop special case: "for identifier in ..."
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                }
                child = child.getNextSibling();
            }
            return null;
        }

        // Function special case: "fun functionName(...) returnType {"
        // The function name is the first identifier after 'fun'
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
                    break; // Stop at opening paren if no identifier found
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

    /**
     * Get display text for a declaration type
     */
    private String getTypeTextForDeclaration(IElementType type) {
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return "variable";
        if (type == KiteElementTypes.INPUT_DECLARATION) return "input";
        if (type == KiteElementTypes.OUTPUT_DECLARATION) return "output";
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return "resource";
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return "component";
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return "schema";
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return "function";
        if (type == KiteElementTypes.TYPE_DECLARATION) return "type";
        return "identifier";
    }

    /**
     * Get icon for a declaration type
     */
    private Icon getIconForDeclaration(IElementType type) {
        if (type == KiteElementTypes.VARIABLE_DECLARATION) return KiteStructureViewIcons.VARIABLE;
        if (type == KiteElementTypes.INPUT_DECLARATION) return KiteStructureViewIcons.INPUT;
        if (type == KiteElementTypes.OUTPUT_DECLARATION) return KiteStructureViewIcons.OUTPUT;
        if (type == KiteElementTypes.RESOURCE_DECLARATION) return KiteStructureViewIcons.RESOURCE;
        if (type == KiteElementTypes.COMPONENT_DECLARATION) return KiteStructureViewIcons.COMPONENT;
        if (type == KiteElementTypes.SCHEMA_DECLARATION) return KiteStructureViewIcons.SCHEMA;
        if (type == KiteElementTypes.FUNCTION_DECLARATION) return KiteStructureViewIcons.FUNCTION;
        if (type == KiteElementTypes.TYPE_DECLARATION) return KiteStructureViewIcons.TYPE;
        return null;
    }

    // Whitespace helpers
    private PsiElement skipWhitespaceBackward(PsiElement element) {
        while (element != null && isWhitespace(element)) {
            element = element.getPrevSibling();
        }
        return element;
    }

    private PsiElement skipWhitespaceForward(PsiElement element) {
        while (element != null && isWhitespace(element)) {
            element = element.getNextSibling();
        }
        return element;
    }

    private boolean isWhitespace(PsiElement element) {
        if (element == null || element.getNode() == null) return false;
        IElementType type = element.getNode().getElementType();
        return type == com.intellij.psi.TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE;
    }

    // Visitor interfaces
    @FunctionalInterface
    private interface DeclarationVisitor {
        void visit(String name, IElementType declarationType, PsiElement element);
    }

    @FunctionalInterface
    private interface ForLoopVariableVisitor {
        void visit(String name, PsiElement element);
    }

    @FunctionalInterface
    private interface PropertyVisitor {
        void visit(String name, PsiElement element);
    }

    // ========== Schema Property Completion ==========

    /**
     * Context information about an enclosing resource block.
     */
    private static class ResourceContext {
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
    private ResourceContext getEnclosingResourceContext(PsiElement position) {
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
    private boolean isInsideBraces(PsiElement position, PsiElement declaration) {
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
     * <p>
     * We check by walking backward on the same line - if we find '=' before newline, we're on right side.
     */
    private boolean isBeforeAssignment(PsiElement position) {
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

    /**
     * Extract the resource type name from a resource declaration.
     * Pattern: resource TypeName instanceName { ... }
     */
    @Nullable
    private String extractResourceTypeName(PsiElement resourceDecl) {
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
                    PsiElement next = skipWhitespaceForward(child.getNextSibling());
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
     * Information about a schema property.
     */
    private static class SchemaPropertyInfo {
        final String type;
        final boolean isCloud;

        SchemaPropertyInfo(String type, boolean isCloud) {
            this.type = type;
            this.isCloud = isCloud;
        }
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
                        PsiElement next = skipWhitespaceForward(child.getNextSibling());
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
}
