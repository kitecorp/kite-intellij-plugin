package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePropertyHelper;
import cloud.kitelang.intellij.util.KiteSchemaHelper;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static cloud.kitelang.intellij.completion.KiteCompletionHelper.skipWhitespaceBackward;
import static cloud.kitelang.intellij.completion.KiteCompletionHelper.skipWhitespaceForward;

/**
 * Provides general code completion for the Kite language.
 *
 * Completion types:
 * - Keywords (resource, component, var, input, output, etc.)
 * - Declared identifiers (variables, resources, components, etc.)
 * - Property access (object.property)
 * - Built-in types (string, number, boolean, etc.)
 */
public class KiteGeneralCompletionProvider extends CompletionProvider<CompletionParameters> {

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

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

        // Skip general completions inside import path strings - only show file paths
        if (KiteImportPathCompletionProvider.isInsideImportPathString(position)) {
            return;
        }

        // Skip general completions in decorator contexts - handled by KiteDecoratorCompletionProvider
        if (KiteDecoratorCompletionProvider.isInDecoratorContext(position)) {
            return;
        }

        // Skip general completions in resource contexts - handled by KiteResourceCompletionProvider
        if (KiteResourceCompletionProvider.isInResourceContext(position)) {
            return;
        }

        // Skip general completions in component definition contexts - handled by KiteComponentDefinitionCompletionProvider
        if (KiteComponentDefinitionCompletionProvider.isInComponentDefinitionContext(position)) {
            return;
        }

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

        // Add keyword completions
        addKeywordCompletions(result);

        // Add declared identifier completions
        addIdentifierCompletions(parameters.getOriginalFile(), result, position);
    }

    /**
     * Check if cursor is after a dot (property access context)
     */
    private boolean isPropertyAccessContext(PsiElement position) {
        PsiElement prev = skipWhitespaceBackward(position.getPrevSibling());
        if (prev == null) {
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
     * Add keyword completions
     */
    private void addKeywordCompletions(@NotNull CompletionResultSet result) {
        for (String keyword : TOP_LEVEL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }
        for (String keyword : CONTROL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }
        for (String keyword : LITERAL_KEYWORDS) {
            result.addElement(createKeywordLookup(keyword));
        }
    }

    /**
     * Add built-in type completions
     */
    private void addTypeCompletions(@NotNull CompletionResultSet result) {
        for (String type : BUILTIN_TYPES) {
            LookupElementBuilder element = LookupElementBuilder.create(type)
                    .withTypeText("type")
                    .withBoldness(true)
                    .withIcon(KiteStructureViewIcons.TYPE);
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }
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
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (KiteDeclarationHelper.isTypeDeclaration(declarationType)) {
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withTypeText(KiteDeclarationHelper.getTypeTextForDeclaration(declarationType))
                        .withIcon(KiteDeclarationHelper.getIconForDeclaration(declarationType))
                );
            }
        });
    }

    /**
     * Add completions for declared identifiers (variables, resources, etc.)
     */
    private void addIdentifierCompletions(PsiFile file, @NotNull CompletionResultSet result, PsiElement position) {
        Set<String> addedNames = new HashSet<>();
        boolean isValuePosition = isAfterAssignment(position);

        // Collect from current file (higher priority)
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (isValuePosition && KiteDeclarationHelper.isTypeDeclaration(declarationType)) {
                return;
            }
            if (!addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText(KiteDeclarationHelper.getTypeTextForDeclaration(declarationType))
                        .withIcon(KiteDeclarationHelper.getIconForDeclaration(declarationType));
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0));
            }
        });

        // Collect for-loop variables
        KiteDeclarationHelper.collectForLoopVariables(file, (name, element) -> {
            if (!addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("loop variable")
                        .withIcon(KiteStructureViewIcons.VARIABLE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0));
            }
        });

        // Collect from imported files
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);
        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null) continue;
            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (isValuePosition && KiteDeclarationHelper.isTypeDeclaration(declarationType)) {
                    return;
                }
                if (KiteImportHelper.isSymbolImported(name, file) && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText(KiteDeclarationHelper.getTypeTextForDeclaration(declarationType))
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteDeclarationHelper.getIconForDeclaration(declarationType));
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 50.0));
                }
            });
        }

        // Auto-import from project files
        addAutoImportCompletions(file, result, addedNames, isValuePosition);
    }

    /**
     * Add auto-import completions from project files
     */
    private void addAutoImportCompletions(PsiFile file, @NotNull CompletionResultSet result,
                                          Set<String> addedNames, boolean isValuePosition) {
        VirtualFile currentVFile = file.getVirtualFile();
        String currentPath = currentVFile != null ? currentVFile.getPath() : null;
        Project project = file.getProject();
        List<PsiFile> allKiteFiles = KiteImportHelper.getAllKiteFilesInProject(project);

        for (PsiFile projectFile : allKiteFiles) {
            if (projectFile == null) continue;
            VirtualFile vf = projectFile.getVirtualFile();
            if (vf == null || vf.getPath().equals(currentPath)) continue;

            final PsiFile targetFile = projectFile;
            KiteDeclarationHelper.collectDeclarations(projectFile, (name, declarationType, element) -> {
                if (isValuePosition && KiteDeclarationHelper.isTypeDeclaration(declarationType)) {
                    return;
                }
                if (!KiteImportHelper.isSymbolImported(name, file) && !addedNames.contains(name)) {
                    addedNames.add(name);
                    var lookup = LookupElementBuilder.create(name)
                            .withTypeText(KiteDeclarationHelper.getTypeTextForDeclaration(declarationType))
                            .withTailText(" (import from " + targetFile.getName() + ")", true)
                            .withIcon(KiteDeclarationHelper.getIconForDeclaration(declarationType))
                            .withInsertHandler(createAutoImportHandler(file, targetFile, name));
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 10.0));
                }
            });
        }
    }

    /**
     * Creates an insert handler that adds an import statement for the symbol.
     */
    private InsertHandler<LookupElement> createAutoImportHandler(PsiFile currentFile, PsiFile importFromFile, String symbolName) {
        return (context, item) -> {
            String importPath = KiteImportHelper.getRelativeImportPath(currentFile, importFromFile);
            if (importPath == null) return;

            Project project = context.getProject();
            Document document = context.getDocument();

            WriteCommandAction.runWriteCommandAction(project, () -> {
                String fileText = document.getText();
                int insertOffset = findImportInsertOffset(fileText);
                String importStatement = "import " + symbolName + " from \"" + importPath + "\"\n";

                if (!fileText.contains("import " + symbolName + " from \"" + importPath + "\"") &&
                    !fileText.contains("import " + symbolName + " from '" + importPath + "'")) {
                    document.insertString(insertOffset, importStatement);
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                }
            });
        };
    }

    /**
     * Find the offset where new import statements should be inserted.
     */
    private int findImportInsertOffset(String text) {
        int lastImportEnd = 0;
        int idx = 0;
        while (idx < text.length()) {
            int lineStart = idx;
            while (idx < text.length() && Character.isWhitespace(text.charAt(idx)) && text.charAt(idx) != '\n') {
                idx++;
            }
            if (idx + 1 < text.length() && text.charAt(idx) == '/' && text.charAt(idx + 1) == '/') {
                while (idx < text.length() && text.charAt(idx) != '\n') idx++;
                if (idx < text.length()) idx++;
                continue;
            }
            if (text.startsWith("import", idx)) {
                while (idx < text.length() && text.charAt(idx) != '\n') idx++;
                if (idx < text.length()) idx++;
                lastImportEnd = idx;
                continue;
            }
            break;
        }
        return lastImportEnd;
    }

    /**
     * Check if the cursor is after an assignment operator (in value position).
     */
    private boolean isAfterAssignment(PsiElement position) {
        PsiElement current = position.getPrevSibling();
        while (current != null) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) return false;
                if (type == KiteTokenTypes.ASSIGN) return true;
            }
            current = current.getPrevSibling();
        }
        PsiElement parent = position.getParent();
        if (parent != null) {
            current = parent.getPrevSibling();
            while (current != null) {
                if (current.getNode() != null) {
                    IElementType type = current.getNode().getElementType();
                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) return false;
                    if (type == KiteTokenTypes.ASSIGN) return true;
                }
                current = current.getPrevSibling();
            }
        }
        return false;
    }

    /**
     * Add property completions for object.property access
     */
    private void addPropertyCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        List<String> chain = buildPropertyChain(position);
        if (chain.isEmpty()) return;

        String rootName = chain.get(0);
        PsiElement declaration = KiteDeclarationHelper.findDeclaration(file, rootName);
        if (declaration == null) return;

        // Check if this is a resource declaration - show all schema properties
        if (declaration.getNode().getElementType() == KiteElementTypes.RESOURCE_DECLARATION && chain.size() == 1) {
            addResourcePropertyCompletions(file, result, declaration);
            return;
        }

        // Handle component instantiation
        PsiElement currentContext = declaration;
        if (declaration.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
            if (isComponentInstantiation(declaration)) {
                String componentTypeName = getComponentTypeName(declaration);
                if (componentTypeName != null) {
                    PsiElement componentDeclaration = findComponentDeclaration(file, componentTypeName);
                    if (componentDeclaration != null) {
                        currentContext = componentDeclaration;
                    }
                }
            }
        }

        // Navigate through the chain for nested properties
        for (int i = 1; i < chain.size(); i++) {
            currentContext = KitePropertyHelper.findPropertyValue(currentContext, chain.get(i));
            if (currentContext == null) return;
        }

        // Collect properties from the final context
        KitePropertyHelper.collectPropertiesFromContext(currentContext, (propertyName, propertyElement) -> {
            result.addElement(
                LookupElementBuilder.create(propertyName)
                    .withTypeText("property")
                    .withIcon(KiteStructureViewIcons.PROPERTY)
            );
        });
    }

    /**
     * Add property completions for resource property access
     */
    private void addResourcePropertyCompletions(PsiFile file, @NotNull CompletionResultSet result, PsiElement resourceDecl) {
        String schemaName = KiteSchemaHelper.extractResourceTypeName(resourceDecl);
        if (schemaName == null) return;

        Map<String, KiteSchemaHelper.SchemaPropertyInfo> schemaProperties = KiteSchemaHelper.findSchemaProperties(file, schemaName);
        Set<String> initializedProperties = KitePropertyHelper.collectExistingPropertyNames(resourceDecl);

        // Add initialized properties first (bold, higher priority)
        for (String propertyName : initializedProperties) {
            KiteSchemaHelper.SchemaPropertyInfo propInfo = schemaProperties.get(propertyName);
            String typeText = propInfo != null ? propInfo.type : "property";

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(typeText)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(true);
            result.addElement(PrioritizedLookupElement.withPriority(element, 200.0));
        }

        // Add remaining schema properties
        for (Map.Entry<String, KiteSchemaHelper.SchemaPropertyInfo> entry : schemaProperties.entrySet()) {
            String propertyName = entry.getKey();
            if (initializedProperties.contains(propertyName)) continue;

            KiteSchemaHelper.SchemaPropertyInfo propInfo = entry.getValue();
            String typeText = propInfo.isCloud ? propInfo.type + " (cloud)" : propInfo.type;

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(typeText)
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(false);
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }

        // Add custom properties not in schema
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
     * Check if a component declaration is an instantiation
     */
    private boolean isComponentInstantiation(PsiElement componentDecl) {
        int identifierCount = 0;
        PsiElement child = componentDecl.getFirstChild();
        while (child != null) {
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.IDENTIFIER) identifierCount++;
            else if (type == KiteTokenTypes.LBRACE) break;
            child = child.getNextSibling();
        }
        return identifierCount >= 2;
    }

    /**
     * Get the component type name from a component instantiation
     */
    @Nullable
    private String getComponentTypeName(PsiElement componentDecl) {
        boolean foundComponent = false;
        PsiElement child = componentDecl.getFirstChild();
        while (child != null) {
            IElementType type = child.getNode().getElementType();
            if (type == KiteTokenTypes.COMPONENT) foundComponent = true;
            else if (foundComponent && type == KiteTokenTypes.IDENTIFIER) return child.getText();
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Find a component declaration by type name
     */
    @Nullable
    private PsiElement findComponentDeclaration(PsiFile file, String typeName) {
        final PsiElement[] result = {null};
        KiteDeclarationHelper.collectDeclarations(file, (declName, declarationType, element) -> {
            if (declarationType == KiteElementTypes.COMPONENT_DECLARATION) {
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
     */
    private List<String> buildPropertyChain(PsiElement position) {
        List<String> chain = new ArrayList<>();
        PsiElement current = position;

        while (true) {
            PsiElement dot = findPreviousDot(current);
            if (dot == null) break;

            PsiElement identifier = skipWhitespaceBackward(dot.getPrevSibling());
            if (identifier == null || identifier.getNode() == null) break;

            if (identifier.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                chain.add(0, identifier.getText());
                current = identifier;
            } else {
                break;
            }
        }
        return chain;
    }

    /**
     * Find the previous dot token
     */
    @Nullable
    private PsiElement findPreviousDot(PsiElement element) {
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        if (prev == null) {
            PsiElement parent = element.getParent();
            if (parent != null) prev = skipWhitespaceBackward(parent.getPrevSibling());
        }
        if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == KiteTokenTypes.DOT) {
            return prev;
        }
        return null;
    }

    /**
     * Create a lookup element for a keyword
     */
    private LookupElement createKeywordLookup(String keyword) {
        return LookupElementBuilder.create(keyword)
            .withBoldness(true)
            .withTypeText("keyword");
    }
}
