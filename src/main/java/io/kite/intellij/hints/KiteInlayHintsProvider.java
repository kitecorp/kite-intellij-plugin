package io.kite.intellij.hints;

import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import io.kite.intellij.reference.KiteImportHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides inlay hints for the Kite language.
 * <p>
 * Features:
 * - Type hints for variables that have inferred types (e.g., var x = "hello" shows ": string")
 * - Parameter name hints in function calls (e.g., greet("Alice", 30) shows "name:" and "age:")
 */
public class KiteInlayHintsProvider implements InlayHintsProvider<KiteInlayHintsProvider.Settings> {

    private static final SettingsKey<Settings> KEY = new SettingsKey<>("kite.inlay.hints");

    @Override
    public boolean isVisibleInSettings() {
        return true;
    }

    @NotNull
    @Override
    public SettingsKey<Settings> getKey() {
        return KEY;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
        return "Kite";
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return """
                // Type hints show inferred types for variables
                var message = "Hello, Kite!"
                var count = 42
                var enabled = true
                
                // Parameter hints show names in function calls
                fun greet(name string, age number) {
                    console.log(name, age)
                }
                
                greet("Alice", 30)
                """;
    }

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull Settings settings) {
        return new ImmediateConfigurable() {
            @NotNull
            @Override
            public JComponent createComponent(@NotNull ChangeListener listener) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

                JCheckBox typeHintsCheckBox = new JCheckBox("Show type hints for variables", settings.showTypeHints);
                typeHintsCheckBox.addActionListener(e -> {
                    settings.showTypeHints = typeHintsCheckBox.isSelected();
                    listener.settingsChanged();
                });
                panel.add(typeHintsCheckBox);

                JCheckBox parameterHintsCheckBox = new JCheckBox("Show parameter name hints", settings.showParameterHints);
                parameterHintsCheckBox.addActionListener(e -> {
                    settings.showParameterHints = parameterHintsCheckBox.isSelected();
                    listener.settingsChanged();
                });
                panel.add(parameterHintsCheckBox);

                return panel;
            }
        };
    }

    @NotNull
    @Override
    public Settings createSettings() {
        return new Settings();
    }

    @Override
    public boolean isLanguageSupported(@NotNull Language language) {
        return language.is(KiteLanguage.INSTANCE);
    }

    @Nullable
    @Override
    public InlayHintsCollector getCollectorFor(@NotNull PsiFile file,
                                               @NotNull Editor editor,
                                               @NotNull Settings settings,
                                               @NotNull InlayHintsSink sink) {
        return new KiteInlayHintsCollector(editor, settings);
    }

    /**
     * Settings for Kite inlay hints.
     */
    public static class Settings {
        public boolean showTypeHints = true;
        public boolean showParameterHints = true;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Settings settings = (Settings) o;
            return showTypeHints == settings.showTypeHints &&
                   showParameterHints == settings.showParameterHints;
        }

        @Override
        public int hashCode() {
            return 31 * Boolean.hashCode(showTypeHints) + Boolean.hashCode(showParameterHints);
        }
    }

    /**
     * Collector that traverses PSI and creates inlay hints.
     */
    private static class KiteInlayHintsCollector implements InlayHintsCollector {
        private final Editor editor;
        private final Settings settings;

        KiteInlayHintsCollector(Editor editor, Settings settings) {
            this.editor = editor;
            this.settings = settings;
        }

        @Override
        public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
            ASTNode node = element.getNode();
            if (node == null) return true;

            IElementType elementType = node.getElementType();

            // Show type hints for VARIABLE declarations without explicit type
            if (settings.showTypeHints && elementType == KiteElementTypes.VARIABLE_DECLARATION) {
                collectTypeHints(element, sink);
            }

            // Show type hints for RESOURCE property assignments
            if (settings.showTypeHints && elementType == KiteElementTypes.RESOURCE_DECLARATION) {
                collectResourcePropertyTypeHints(element, sink);
            }

            // Show type hints for COMPONENT instantiation input assignments
            if (settings.showTypeHints && elementType == KiteElementTypes.COMPONENT_DECLARATION) {
                // Check if this is a component instantiation (has two identifiers: type and instance name)
                if (isComponentInstantiation(element.getNode())) {
                    collectComponentInputTypeHints(element, sink);
                }
            }

            // Show parameter hints for function calls
            if (settings.showParameterHints) {
                // Function calls are identifiers followed by parentheses with arguments
                if (elementType == KiteTokenTypes.IDENTIFIER) {
                    collectParameterHints(element, sink);
                }
            }

            return true;
        }

        /**
         * Collect type hints for VAR declarations.
         * Shows inferred type when the type is not explicitly specified.
         * <p>
         * Example: var x = "hello"  ->  var x: string = "hello"
         */
        private void collectTypeHints(PsiElement varDeclaration, InlayHintsSink sink) {
            ASTNode node = varDeclaration.getNode();
            if (node == null) return;


            // Check if type is already explicitly specified
            // VAR_DECLARATION structure: VAR <type>? <name> = <value>
            ASTNode[] children = node.getChildren(null);

            boolean hasExplicitType = false;
            ASTNode nameNode = null;
            ASTNode valueNode = null;
            ASTNode assignNode = null;  // Track the = sign position
            boolean foundEquals = false;

            for (ASTNode child : children) {
                IElementType childType = child.getElementType();

                // Skip whitespace and newlines (both Kite tokens and IntelliJ's built-in whitespace)
                if (childType == KiteTokenTypes.WHITESPACE ||
                    childType == KiteTokenTypes.NL ||
                    childType == KiteTokenTypes.NEWLINE ||
                    childType == TokenType.WHITE_SPACE) {
                    continue;
                }

                // Skip the VAR keyword
                if (childType == KiteTokenTypes.VAR) {
                    continue;
                }

                // Skip decorators (identified by @ symbol)
                if (childType == KiteTokenTypes.AT) {
                    // Skip the decorator and its content
                    continue;
                }

                if (childType == KiteTokenTypes.ASSIGN) {
                    assignNode = child;  // Remember the = position
                    foundEquals = true;
                    continue;
                }

                if (!foundEquals) {
                    // Before equals: could be type or name
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        if (nameNode == null) {
                            // First identifier - could be type or name
                            nameNode = child;
                        } else {
                            // Second identifier - first was type, this is name
                            hasExplicitType = true;
                            nameNode = child;
                        }
                    }
                    // Check for built-in type keywords (object, any) or type identifiers
                    else if (isTypeKeyword(childType) ||
                             (childType == KiteTokenTypes.IDENTIFIER && isBuiltInTypeName(child.getText()))) {
                        hasExplicitType = true;
                    }
                } else {
                    // After equals: this is the value
                    valueNode = child;
                    break;
                }
            }

            // Only show hint if no explicit type and we have a name and value
            if (!hasExplicitType && nameNode != null && valueNode != null) {
                String inferredType = inferType(valueNode);
                if (inferredType != null) {
                    // Place hint right after the variable name
                    int offset = nameNode.getTextRange().getEndOffset();
                    PresentationFactory factory = new PresentationFactory(editor);
                    // Use roundWithBackground for proper vertical baseline alignment
                    InlayPresentation text = factory.smallText(":" + inferredType);
                    InlayPresentation presentation = factory.roundWithBackground(text);
                    sink.addInlineElement(offset, true, presentation, false);
                }
            }
        }

        /**
         * Collect type hints for resource property assignments.
         * Looks up the property type from the matching schema.
         * <p>
         * Example: resource DatabaseConfig photos { host = "..." } shows ": string" after "host"
         */
        private void collectResourcePropertyTypeHints(PsiElement resourceDeclaration, InlayHintsSink sink) {
            ASTNode node = resourceDeclaration.getNode();
            if (node == null) return;


            // Get the resource type name (first identifier after RESOURCE keyword)
            String schemaName = getResourceTypeName(node);
            if (schemaName == null) return;

            // Find the matching schema
            PsiFile file = resourceDeclaration.getContainingFile();
            if (file == null) return;

            java.util.Map<String, String> schemaProperties = findSchemaProperties(file, schemaName);
            if (schemaProperties.isEmpty()) return;

            // Find property assignments inside the resource body
            boolean insideBraces = false;
            for (ASTNode child : node.getChildren(null)) {
                IElementType childType = child.getElementType();

                if (childType == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                    continue;
                }
                if (childType == KiteTokenTypes.RBRACE) {
                    break;
                }

                // Look for property assignments: identifier = value
                if (insideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                    String propertyName = child.getText();

                    // Check if followed by =
                    ASTNode next = child.getTreeNext();
                    while (next != null && isWhitespaceToken(next.getElementType())) {
                        next = next.getTreeNext();
                    }

                    if (next != null && next.getElementType() == KiteTokenTypes.ASSIGN) {
                        // This is a property assignment - look up the type from schema
                        String propertyType = schemaProperties.get(propertyName);
                        if (propertyType != null) {
                            int offset = child.getTextRange().getEndOffset();
                            PresentationFactory factory = new PresentationFactory(editor);
                            InlayPresentation text = factory.smallText(":" + propertyType);
                            InlayPresentation presentation = factory.roundWithBackground(text);
                            sink.addInlineElement(offset, true, presentation, false);
                        }
                    }
                }
            }
        }

        /**
         * Get the resource type name from a resource declaration.
         * Pattern: resource TypeName instanceName { ... }
         * Returns the first identifier after RESOURCE keyword.
         */
        @Nullable
        private String getResourceTypeName(ASTNode resourceNode) {
            boolean foundResource = false;
            for (ASTNode child : resourceNode.getChildren(null)) {
                IElementType childType = child.getElementType();

                if (childType == KiteTokenTypes.RESOURCE) {
                    foundResource = true;
                    continue;
                }

                if (foundResource && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier is the type name
                }

                if (childType == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            return null;
        }

        /**
         * Find schema properties by name. Returns a map of property name to type.
         * Searches in current file and imported files.
         */
        private java.util.Map<String, String> findSchemaProperties(PsiFile file, String schemaName) {
            java.util.Map<String, String> properties = new java.util.HashMap<>();

            // Search in current file
            findSchemaPropertiesRecursive(file.getNode(), schemaName, properties);

            // If not found, search in imported files
            if (properties.isEmpty()) {
                findSchemaPropertiesInImports(file, schemaName, properties, new HashSet<>());
            }

            return properties;
        }

        /**
         * Recursively search for a schema and extract its properties.
         */
        private void findSchemaPropertiesRecursive(ASTNode node, String schemaName, java.util.Map<String, String> properties) {
            if (node == null) return;

            if (node.getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
                // Check if this is the schema we're looking for
                String name = getSchemaName(node);
                if (schemaName.equals(name)) {
                    extractSchemaProperties(node, properties);
                    return;
                }
            }

            // Recurse into children
            for (ASTNode child : node.getChildren(null)) {
                findSchemaPropertiesRecursive(child, schemaName, properties);
                if (!properties.isEmpty()) return; // Found it
            }
        }

        /**
         * Search for schema properties in imported files.
         */
        private void findSchemaPropertiesInImports(PsiFile file, String schemaName,
                                                   java.util.Map<String, String> properties, Set<String> visited) {
            List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

            for (PsiFile importedFile : importedFiles) {
                if (importedFile == null || importedFile.getVirtualFile() == null) continue;

                String path = importedFile.getVirtualFile().getPath();
                if (visited.contains(path)) continue;
                visited.add(path);

                findSchemaPropertiesRecursive(importedFile.getNode(), schemaName, properties);
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
        private String getSchemaName(ASTNode schemaNode) {
            boolean foundSchema = false;
            for (ASTNode child : schemaNode.getChildren(null)) {
                IElementType childType = child.getElementType();

                if (childType == KiteTokenTypes.SCHEMA) {
                    foundSchema = true;
                    continue;
                }

                if (foundSchema && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                }

                if (childType == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            return null;
        }

        /**
         * Extract property definitions from a schema.
         * Pattern inside schema: type propertyName [= defaultValue]
         */
        private void extractSchemaProperties(ASTNode schemaNode, java.util.Map<String, String> properties) {
            boolean insideBraces = false;
            String currentType = null;

            for (ASTNode child : schemaNode.getChildren(null)) {
                IElementType childType = child.getElementType();

                if (childType == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                    continue;
                }
                if (childType == KiteTokenTypes.RBRACE) {
                    break;
                }

                if (!insideBraces) continue;

                // Skip whitespace and newlines
                if (isWhitespaceToken(childType)) {
                    continue;
                }

                // Handle 'any' keyword as a type
                if (childType == KiteTokenTypes.ANY) {
                    currentType = "any";
                    continue;
                }

                // Track type -> name pattern
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    String text = child.getText();
                    if (currentType == null) {
                        // First identifier is the type
                        currentType = text;
                    } else {
                        // Second identifier is the property name
                        properties.put(text, currentType);
                        currentType = null;
                    }
                }

                // Reset on newline or assignment (end of property definition)
                if (childType == KiteTokenTypes.NL || childType == KiteTokenTypes.ASSIGN) {
                    currentType = null;
                }
            }
        }

        /**
         * Check if token type is whitespace.
         */
        private boolean isWhitespaceToken(IElementType type) {
            return type == KiteTokenTypes.WHITESPACE ||
                   type == KiteTokenTypes.NL ||
                   type == KiteTokenTypes.NEWLINE ||
                   type == TokenType.WHITE_SPACE;
        }

        /**
         * Check if a COMPONENT_DECLARATION is a component instantiation (not a definition).
         * Instantiation: component TypeName instanceName { ... } (two identifiers)
         * Definition: component TypeName { ... } (one identifier)
         */
        private boolean isComponentInstantiation(ASTNode componentNode) {
            int identifierCount = 0;
            boolean foundComponent = false;

            for (ASTNode child : componentNode.getChildren(null)) {
                IElementType type = child.getElementType();

                if (type == KiteTokenTypes.COMPONENT) {
                    foundComponent = true;
                    continue;
                }

                if (foundComponent && type == KiteTokenTypes.IDENTIFIER) {
                    identifierCount++;
                }

                if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }

            // Two identifiers means it's an instantiation (TypeName instanceName)
            return identifierCount == 2;
        }

        /**
         * Get the component type name from a component instantiation.
         * Pattern: component TypeName instanceName { ... }
         * Returns the first identifier after COMPONENT keyword.
         */
        @Nullable
        private String getComponentTypeName(ASTNode componentNode) {
            boolean foundComponent = false;
            for (ASTNode child : componentNode.getChildren(null)) {
                IElementType childType = child.getElementType();

                if (childType == KiteTokenTypes.COMPONENT) {
                    foundComponent = true;
                    continue;
                }

                if (foundComponent && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier is the type name
                }

                if (childType == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            return null;
        }

        /**
         * Collect type hints for component instantiation input assignments.
         * Looks up the input types from the component definition.
         * <p>
         * Example: component WebServer api { name = "payments" } shows ":string" after "name"
         */
        private void collectComponentInputTypeHints(PsiElement componentInstantiation, InlayHintsSink sink) {
            ASTNode node = componentInstantiation.getNode();
            if (node == null) return;


            // Get the component type name (first identifier after COMPONENT keyword)
            String componentTypeName = getComponentTypeName(node);
            if (componentTypeName == null) return;

            // Find the component definition and get input types
            PsiFile file = componentInstantiation.getContainingFile();
            if (file == null) return;

            java.util.Map<String, String> inputTypes = findComponentInputTypes(file, componentTypeName);
            if (inputTypes.isEmpty()) return;

            // Find input assignments inside the component body
            boolean insideBraces = false;
            for (ASTNode child : node.getChildren(null)) {
                IElementType childType = child.getElementType();

                if (childType == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                    continue;
                }
                if (childType == KiteTokenTypes.RBRACE) {
                    break;
                }

                // Look for input assignments: identifier = value
                if (insideBraces && childType == KiteTokenTypes.IDENTIFIER) {
                    String inputName = child.getText();

                    // Check if followed by =
                    ASTNode next = child.getTreeNext();
                    while (next != null && isWhitespaceToken(next.getElementType())) {
                        next = next.getTreeNext();
                    }

                    if (next != null && next.getElementType() == KiteTokenTypes.ASSIGN) {
                        // This is an input assignment - look up the type from component definition
                        String inputType = inputTypes.get(inputName);
                        if (inputType != null) {
                            int offset = child.getTextRange().getEndOffset();
                            PresentationFactory factory = new PresentationFactory(editor);
                            InlayPresentation text = factory.smallText(":" + inputType);
                            InlayPresentation presentation = factory.roundWithBackground(text);
                            sink.addInlineElement(offset, true, presentation, false);
                        }
                    }
                }
            }
        }

        /**
         * Find component input types by searching for the component definition.
         * Returns a map of input name to type.
         */
        private java.util.Map<String, String> findComponentInputTypes(PsiFile file, String componentTypeName) {
            java.util.Map<String, String> inputTypes = new java.util.HashMap<>();

            // Search in current file
            findComponentInputTypesRecursive(file.getNode(), componentTypeName, inputTypes);

            // If not found, search in imported files
            if (inputTypes.isEmpty()) {
                findComponentInputTypesInImports(file, componentTypeName, inputTypes, new HashSet<>());
            }

            return inputTypes;
        }

        /**
         * Recursively search for a component definition and extract its input types.
         */
        private void findComponentInputTypesRecursive(ASTNode node, String componentTypeName, java.util.Map<String, String> inputTypes) {
            if (node == null) return;

            if (node.getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
                // Check if this is the component definition (not instantiation) we're looking for
                if (!isComponentInstantiation(node)) {
                    String name = getComponentTypeName(node);
                    if (componentTypeName.equals(name)) {
                        extractComponentInputTypes(node, inputTypes);
                        return;
                    }
                }
            }

            // Recurse into children
            for (ASTNode child : node.getChildren(null)) {
                findComponentInputTypesRecursive(child, componentTypeName, inputTypes);
                if (!inputTypes.isEmpty()) return; // Found it
            }
        }

        /**
         * Search for component input types in imported files.
         */
        private void findComponentInputTypesInImports(PsiFile file, String componentTypeName,
                                                      java.util.Map<String, String> inputTypes, Set<String> visited) {
            List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

            for (PsiFile importedFile : importedFiles) {
                if (importedFile == null || importedFile.getVirtualFile() == null) continue;

                String path = importedFile.getVirtualFile().getPath();
                if (visited.contains(path)) continue;
                visited.add(path);

                findComponentInputTypesRecursive(importedFile.getNode(), componentTypeName, inputTypes);
                if (!inputTypes.isEmpty()) return;

                // Recursively check imports
                findComponentInputTypesInImports(importedFile, componentTypeName, inputTypes, visited);
                if (!inputTypes.isEmpty()) return;
            }
        }

        /**
         * Extract input declarations from a component definition.
         * Pattern inside component: input type name [= defaultValue]
         */
        private void extractComponentInputTypes(ASTNode componentNode, java.util.Map<String, String> inputTypes) {
            boolean insideBraces = false;

            for (ASTNode child : componentNode.getChildren(null)) {
                IElementType childType = child.getElementType();

                if (childType == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                    continue;
                }
                if (childType == KiteTokenTypes.RBRACE) {
                    break;
                }

                if (!insideBraces) continue;

                // Look for INPUT_DECLARATION elements
                if (childType == KiteElementTypes.INPUT_DECLARATION) {
                    extractInputDeclaration(child, inputTypes);
                }
            }
        }

        /**
         * Extract type and name from an input declaration.
         * Pattern: input type name [= defaultValue]
         */
        private void extractInputDeclaration(ASTNode inputDeclNode, java.util.Map<String, String> inputTypes) {
            boolean foundInput = false;
            String currentType = null;

            for (ASTNode child : inputDeclNode.getChildren(null)) {
                IElementType childType = child.getElementType();

                // Skip whitespace
                if (isWhitespaceToken(childType)) continue;

                if (childType == KiteTokenTypes.INPUT) {
                    foundInput = true;
                    continue;
                }

                if (!foundInput) continue;

                // Handle 'any' keyword as type
                if (childType == KiteTokenTypes.ANY) {
                    currentType = "any";
                    continue;
                }

                // Handle array suffix []
                if (childType == KiteElementTypes.ARRAY_LITERAL && currentType != null) {
                    currentType = currentType + "[]";
                    continue;
                }

                // Track type -> name pattern
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    if (currentType == null) {
                        // First identifier is the type
                        currentType = child.getText();
                    } else {
                        // Second identifier is the input name
                        inputTypes.put(child.getText(), currentType);
                        return; // Done with this input declaration
                    }
                }

                // Stop at = (rest is default value)
                if (childType == KiteTokenTypes.ASSIGN) {
                    return;
                }
            }
        }

        /**
         * Check if the element type is a type keyword (object, any) or an identifier that
         * represents a type. In Kite, built-in types like string, number, boolean are
         * just identifiers in type position.
         */
        private boolean isTypeKeyword(IElementType type) {
            // Only OBJECT and ANY are actual type keywords in KiteTokenTypes
            // string, number, boolean are regular identifiers when used as types
            return type == KiteTokenTypes.OBJECT ||
                   type == KiteTokenTypes.ANY;
        }

        /**
         * Check if an identifier text represents a built-in type name.
         */
        private boolean isBuiltInTypeName(String text) {
            return "string".equals(text) ||
                   "number".equals(text) ||
                   "boolean".equals(text) ||
                   "object".equals(text) ||
                   "any".equals(text) ||
                   "void".equals(text);
        }

        /**
         * Infer the type from a value node.
         */
        private String inferType(ASTNode valueNode) {
            if (valueNode == null) return null;

            IElementType valueType = valueNode.getElementType();

            // String literal - can be STRING token or DQUOTE (opening quote of interpolated string)
            // Also check for SINGLE_STRING which is single-quoted strings
            if (valueType == KiteTokenTypes.STRING ||
                valueType == KiteTokenTypes.DQUOTE ||
                valueType == KiteTokenTypes.SINGLE_STRING) {
                return "string";
            }

            // Number literal
            if (valueType == KiteTokenTypes.NUMBER) {
                return "number";
            }

            // Boolean literals
            if (valueType == KiteTokenTypes.TRUE || valueType == KiteTokenTypes.FALSE) {
                return "boolean";
            }

            // Null literal
            if (valueType == KiteTokenTypes.NULL) {
                return "null";
            }

            // Object literal
            if (valueType == KiteElementTypes.OBJECT_LITERAL) {
                return "object";
            }

            // Array literal
            if (valueType == KiteElementTypes.ARRAY_LITERAL) {
                return "array";
            }

            // For other expressions (identifiers, function calls, etc.),
            // we can't easily infer the type without more sophisticated analysis
            return null;
        }

        /**
         * Collect parameter name hints for function calls.
         * Shows parameter names before arguments in function calls.
         * <p>
         * Example: greet("Alice", 30) shows "name:" before "Alice" and "age:" before 30
         */
        private void collectParameterHints(PsiElement identifier, InlayHintsSink sink) {
            // Check if this identifier is followed by parentheses (function call)
            ASTNode identifierNode = identifier.getNode();
            if (identifierNode == null) return;

            ASTNode sibling = identifierNode.getTreeNext();

            // Skip whitespace
            while (sibling != null &&
                   (sibling.getElementType() == KiteTokenTypes.WHITESPACE ||
                    sibling.getElementType() == KiteTokenTypes.NL)) {
                sibling = sibling.getTreeNext();
            }

            // Check if followed by LPAREN
            if (sibling == null || sibling.getElementType() != KiteTokenTypes.LPAREN) {
                return;
            }

            // Check that this is NOT a function declaration (identifier preceded by 'fun')
            ASTNode prevSibling = identifierNode.getTreePrev();
            while (prevSibling != null &&
                   (prevSibling.getElementType() == KiteTokenTypes.WHITESPACE ||
                    prevSibling.getElementType() == KiteTokenTypes.NL ||
                    prevSibling.getElementType() == TokenType.WHITE_SPACE)) {
                prevSibling = prevSibling.getTreePrev();
            }
            if (prevSibling != null && prevSibling.getElementType() == KiteTokenTypes.FUN) {
                return; // This is a function declaration, not a call
            }

            // This is a function call - find the function declaration to get parameter names
            String functionName = identifierNode.getText();
            PsiFile file = identifier.getContainingFile();
            if (file == null) return;

            // Find function declaration with this name
            List<String> parameterNames = findFunctionParameters(file, functionName);
            if (parameterNames.isEmpty()) return;

            // Collect arguments
            List<ASTNode> arguments = collectArguments(sibling);

            // Add hints for each argument
            PresentationFactory factory = new PresentationFactory(editor);
            for (int i = 0; i < Math.min(arguments.size(), parameterNames.size()); i++) {
                ASTNode arg = arguments.get(i);
                String paramName = parameterNames.get(i);

                // Only show hint if the argument is not already a named argument
                if (!isNamedArgument(arg)) {
                    int offset = arg.getTextRange().getStartOffset();
                    // Use roundWithBackground for proper vertical baseline alignment
                    InlayPresentation text = factory.smallText(paramName + ":");
                    InlayPresentation presentation = factory.roundWithBackground(text);
                    sink.addInlineElement(offset, true, presentation, false);
                }
            }
        }

        /**
         * Find parameter names for a function by searching for its declaration.
         * First searches in the current file, then in imported files.
         */
        private List<String> findFunctionParameters(PsiFile file, String functionName) {
            List<String> params = new ArrayList<>();

            // Search the current file for function declarations
            searchForFunctionDeclaration(file.getNode(), functionName, params);

            // If not found, search in imported files
            if (params.isEmpty()) {
                searchFunctionParametersInImports(file, functionName, params, new HashSet<>());
            }

            return params;
        }

        /**
         * Search for function parameters in imported files.
         */
        private void searchFunctionParametersInImports(PsiFile file, String functionName,
                                                       List<String> params, Set<String> visitedPaths) {
            List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

            for (PsiFile importedFile : importedFiles) {
                if (importedFile == null || importedFile.getVirtualFile() == null) {
                    continue;
                }

                String filePath = importedFile.getVirtualFile().getPath();
                if (visitedPaths.contains(filePath)) {
                    continue; // Already visited
                }
                visitedPaths.add(filePath);

                // Search in this imported file
                searchForFunctionDeclaration(importedFile.getNode(), functionName, params);
                if (!params.isEmpty()) {
                    return; // Found it
                }

                // Recursively search in files imported by this file
                searchFunctionParametersInImports(importedFile, functionName, params, visitedPaths);
                if (!params.isEmpty()) {
                    return; // Found it
                }
            }
        }

        /**
         * Recursively search for a function declaration and extract its parameter names.
         */
        private void searchForFunctionDeclaration(ASTNode node, String functionName, List<String> params) {
            if (node == null) return;

            if (node.getElementType() == KiteElementTypes.FUNCTION_DECLARATION) {
                // Check if this is the function we're looking for
                ASTNode[] children = node.getChildren(null);
                boolean foundFun = false;
                boolean foundName = false;
                boolean inParams = false;

                for (ASTNode child : children) {
                    IElementType childType = child.getElementType();

                    if (childType == KiteTokenTypes.FUN) {
                        foundFun = true;
                        continue;
                    }

                    if (foundFun && !foundName && childType == KiteTokenTypes.IDENTIFIER) {
                        if (child.getText().equals(functionName)) {
                            foundName = true;
                        } else {
                            return; // Not the function we're looking for
                        }
                        continue;
                    }

                    if (foundName && childType == KiteTokenTypes.LPAREN) {
                        inParams = true;
                        continue;
                    }

                    if (inParams && childType == KiteTokenTypes.RPAREN) {
                        return; // Done collecting parameters
                    }

                    if (inParams && childType == KiteTokenTypes.IDENTIFIER) {
                        // Parameters in Kite: fun greet(string name, number age)
                        // Type comes FIRST, then the name
                        // So we need to check: if PREVIOUS non-whitespace was an identifier,
                        // then THIS is the parameter name
                        ASTNode prev = child.getTreePrev();
                        while (prev != null &&
                               (prev.getElementType() == KiteTokenTypes.WHITESPACE ||
                                prev.getElementType() == KiteTokenTypes.NL ||
                                prev.getElementType() == TokenType.WHITE_SPACE)) {
                            prev = prev.getTreePrev();
                        }

                        // If previous is an identifier (the type), current is the param name
                        if (prev != null &&
                            (prev.getElementType() == KiteTokenTypes.IDENTIFIER ||
                             isTypeKeyword(prev.getElementType()))) {
                            params.add(child.getText());
                        }
                    }
                }
                return;
            }

            // Recurse into children
            for (ASTNode child : node.getChildren(null)) {
                searchForFunctionDeclaration(child, functionName, params);
                if (!params.isEmpty()) return; // Found it
            }
        }

        /**
         * Collect argument nodes from a function call.
         * Returns the first token of each argument (for hint placement).
         */
        private List<ASTNode> collectArguments(ASTNode lparenNode) {
            List<ASTNode> arguments = new ArrayList<>();

            ASTNode current = lparenNode.getTreeNext();
            boolean expectingNewArg = true;
            int parenDepth = 0;
            int braceDepth = 0;
            int bracketDepth = 0;
            int stringDepth = 0; // Track nested string depth instead of toggle


            while (current != null) {
                IElementType type = current.getElementType();

                // Exit when we hit the closing RPAREN at depth 0
                if (type == KiteTokenTypes.RPAREN && parenDepth == 0 && stringDepth == 0) {
                    break;
                }

                // Track nesting depth
                if (type == KiteTokenTypes.LPAREN) parenDepth++;
                else if (type == KiteTokenTypes.RPAREN) parenDepth--;
                else if (type == KiteTokenTypes.LBRACE) braceDepth++;
                else if (type == KiteTokenTypes.RBRACE) braceDepth--;
                else if (type == KiteTokenTypes.LBRACK) bracketDepth++;
                else if (type == KiteTokenTypes.RBRACK) bracketDepth--;

                // Track string boundaries - handle both tokenization styles
                // STRING/SINGLE_STRING are atomic, DQUOTE/STRING_DQUOTE mark interpolated string boundaries
                if (type == KiteTokenTypes.DQUOTE) {
                    // Opening quote - enter string
                    stringDepth = 1;
                } else if (type == KiteTokenTypes.STRING_DQUOTE) {
                    // Closing quote - exit string
                    stringDepth = 0;
                }
                // Skip over STRING_TEXT and INTERP tokens - they're inside strings
                // STRING and SINGLE_STRING are atomic and don't affect string depth

                // Comma at top level marks end of argument
                if (type == KiteTokenTypes.COMMA && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && stringDepth == 0) {
                    expectingNewArg = true;
                    current = current.getTreeNext();
                    continue;
                }

                // Skip whitespace and newlines
                if (type == KiteTokenTypes.WHITESPACE ||
                    type == KiteTokenTypes.NL ||
                    type == KiteTokenTypes.NEWLINE ||
                    type == TokenType.WHITE_SPACE) {
                    current = current.getTreeNext();
                    continue;
                }

                // Skip string content tokens - they're part of the string argument, not new arguments
                if (type == KiteTokenTypes.STRING_TEXT ||
                    type == KiteTokenTypes.INTERP_START ||
                    type == KiteTokenTypes.INTERP_END ||
                    type == KiteTokenTypes.INTERP_IDENTIFIER ||
                    type == KiteTokenTypes.INTERP_SIMPLE) {
                    current = current.getTreeNext();
                    continue;
                }

                // First non-whitespace, non-string-content token after comma or start is the argument start
                if (expectingNewArg) {
                    arguments.add(current);
                    expectingNewArg = false;
                }

                current = current.getTreeNext();
            }

            return arguments;
        }

        /**
         * Check if an argument is already a named argument (name: value or name = value).
         */
        private boolean isNamedArgument(ASTNode arg) {
            // Look for colon or equals after an identifier
            if (arg.getElementType() != KiteTokenTypes.IDENTIFIER) {
                return false;
            }

            ASTNode next = arg.getTreeNext();
            while (next != null &&
                   (next.getElementType() == KiteTokenTypes.WHITESPACE ||
                    next.getElementType() == KiteTokenTypes.NL)) {
                next = next.getTreeNext();
            }

            return next != null &&
                   (next.getElementType() == KiteTokenTypes.COLON ||
                    next.getElementType() == KiteTokenTypes.ASSIGN);
        }
    }
}
