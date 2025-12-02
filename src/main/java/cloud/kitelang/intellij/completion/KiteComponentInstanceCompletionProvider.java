package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePropertyHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
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

/**
 * Provides code completion inside component instance blocks.
 * Handles:
 * - Input property name completion (left side of =) - shows inputs from component definition
 * - Value completion (right side of =)
 *
 * Component instance: component TypeName instanceName { ... } (two identifiers before {)
 * Component definition: component TypeName { ... } (one identifier before {)
 */
public class KiteComponentInstanceCompletionProvider extends CompletionProvider<CompletionParameters> {

    /**
     * Check if position is inside a component instance block.
     * Public static method for other providers to check component instance context.
     */
    public static boolean isInComponentInstanceContext(@NotNull PsiElement position) {
        return getEnclosingComponentInstanceContext(position) != null;
    }

    /**
     * Get the component instance context if we're inside a component instance block.
     * Returns null if not inside a component instance block.
     */
    @Nullable
    static ComponentInstanceContext getEnclosingComponentInstanceContext(PsiElement position) {
        PsiElement current = position;
        while (current != null) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.COMPONENT_DECLARATION) {
                if (KitePsiUtil.isInsideBraces(position, current)) {
                    // Check if this is an instance (two identifiers) vs definition (one identifier)
                    if (isComponentInstance(current)) {
                        String typeName = extractComponentTypeName(current);
                        if (typeName != null) {
                            return new ComponentInstanceContext(typeName, current);
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Check if a COMPONENT_DECLARATION is an instance (not a definition).
     * An instance has two identifiers before the opening brace.
     * A definition has only one identifier.
     */
    private static boolean isComponentInstance(PsiElement componentDeclaration) {
        int identifierCount = 0;
        boolean foundLBrace = false;

        for (PsiElement child = componentDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE) {
                    foundLBrace = true;
                    break;
                }
                if (type == KiteTokenTypes.IDENTIFIER) {
                    identifierCount++;
                }
            }
        }

        // Instance has two identifiers before {, definition has one
        return foundLBrace && identifierCount == 2;
    }

    /**
     * Extract the type name (first identifier) from a component declaration.
     * For "component WebServer server { }" returns "WebServer"
     */
    @Nullable
    private static String extractComponentTypeName(PsiElement componentDeclaration) {
        for (PsiElement child = componentDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();
                if (type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier is the type name
                }
                if (type == KiteTokenTypes.LBRACE) {
                    break; // Past the header
                }
            }
        }
        return null;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        // Check if we're inside a component instance body
        ComponentInstanceContext instanceContext = getEnclosingComponentInstanceContext(position);
        if (instanceContext == null) {
            return; // Not in a component instance context
        }

        // Check if we're on the LEFT side of = (property name) or RIGHT side (value)
        if (KiteResourceCompletionProvider.isBeforeAssignment(position)) {
            // LEFT side: only input properties from the component definition
            addInputPropertyCompletions(file, result, instanceContext);
        } else {
            // RIGHT side: variables, resources, components, functions
            addValueCompletions(file, result);
        }
    }

    /**
     * Add input property completions from the component definition.
     * Excludes properties that are already defined in the instance block.
     */
    private void addInputPropertyCompletions(PsiFile file, @NotNull CompletionResultSet result,
                                              ComponentInstanceContext instanceContext) {
        // Find the component definition
        PsiElement componentDef = findComponentDefinition(file, instanceContext.typeName);
        if (componentDef == null) {
            return;
        }

        // Collect input properties from the component definition
        Map<String, String> inputProperties = collectComponentInputs(componentDef);

        // Get already defined properties in the instance
        Set<String> existingProperties = KitePropertyHelper.collectExistingPropertyNames(instanceContext.componentInstance);

        // Add completions for undefined input properties
        for (Map.Entry<String, String> entry : inputProperties.entrySet()) {
            String propertyName = entry.getKey();
            String propertyType = entry.getValue();

            // Skip properties that are already defined
            if (existingProperties.contains(propertyName)) {
                continue;
            }

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(propertyType)
                    .withIcon(KiteStructureViewIcons.INPUT)
                    .withBoldness(true)
                    .withInsertHandler((ctx, item) -> {
                        ctx.getDocument().insertString(ctx.getTailOffset(), " = ");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                    });

            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }
    }

    /**
     * Find a component definition by type name in the current file and imported files.
     */
    @Nullable
    private PsiElement findComponentDefinition(PsiFile file, String typeName) {
        // Search in current file
        PsiElement result = findComponentDefinitionInFile(file, typeName);
        if (result != null) {
            return result;
        }

        // Search in imported files
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);
        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null) continue;
            result = findComponentDefinitionInFile(importedFile, typeName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Find a component definition by type name in a specific file.
     */
    @Nullable
    private PsiElement findComponentDefinitionInFile(PsiFile file, String typeName) {
        final PsiElement[] result = {null};
        KiteDeclarationHelper.collectDeclarations(file, (declName, declarationType, element) -> {
            if (declarationType == KiteElementTypes.COMPONENT_DECLARATION) {
                // Check if this is a definition (not instance) and matches the type name
                if (!isComponentInstance(element) && typeName.equals(declName)) {
                    result[0] = element;
                }
            }
        });
        return result[0];
    }

    /**
     * Collect input properties from a component definition.
     * Returns a map of property name to type.
     */
    private Map<String, String> collectComponentInputs(PsiElement componentDef) {
        Map<String, String> inputs = new LinkedHashMap<>();
        int braceDepth = 0;

        for (PsiElement child = componentDef.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                } else if (type == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                } else if (braceDepth == 1 && type == KiteElementTypes.INPUT_DECLARATION) {
                    // Extract type and name from INPUT_DECLARATION
                    String[] typeAndName = extractTypeAndName(child);
                    if (typeAndName != null) {
                        inputs.put(typeAndName[1], typeAndName[0]);
                    }
                }
            }
        }

        return inputs;
    }

    /**
     * Extract type and name from an input/output declaration.
     * Pattern: input type name = value
     * Returns [type, name] or null if extraction fails.
     */
    @Nullable
    private String[] extractTypeAndName(PsiElement declaration) {
        String foundType = null;
        String foundName = null;

        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                // Skip input/output keyword
                if (type == KiteTokenTypes.INPUT || type == KiteTokenTypes.OUTPUT) {
                    continue;
                }

                // Handle 'any' keyword as a type
                if (type == KiteTokenTypes.ANY) {
                    foundType = "any";
                    continue;
                }

                if (type == KiteTokenTypes.IDENTIFIER) {
                    if (foundType == null) {
                        foundType = child.getText();
                    } else if (foundName == null) {
                        foundName = child.getText();
                    }
                }

                // Stop at assignment
                if (type == KiteTokenTypes.ASSIGN) {
                    break;
                }
            }
        }

        if (foundType != null && foundName != null) {
            return new String[]{foundType, foundName};
        }
        return null;
    }

    /**
     * Add value completions for the right side of assignments in component instance blocks.
     */
    private void addValueCompletions(PsiFile file, @NotNull CompletionResultSet result) {
        Set<String> addedNames = new HashSet<>();

        // Variables (highest priority)
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.VARIABLE_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("variable")
                        .withIcon(KiteStructureViewIcons.VARIABLE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 500.0));
            }
        });

        // Inputs
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.INPUT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("input")
                        .withIcon(KiteStructureViewIcons.INPUT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 450.0));
            }
        });

        // Outputs
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.OUTPUT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("output")
                        .withIcon(KiteStructureViewIcons.OUTPUT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 400.0));
            }
        });

        // Resources
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.RESOURCE_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("resource")
                        .withIcon(KiteStructureViewIcons.RESOURCE);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 300.0));
            }
        });

        // Components
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.COMPONENT_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("component")
                        .withIcon(KiteStructureViewIcons.COMPONENT);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 200.0));
            }
        });

        // Functions
        KiteDeclarationHelper.collectDeclarations(file, (name, declarationType, element) -> {
            if (declarationType == KiteElementTypes.FUNCTION_DECLARATION && !addedNames.contains(name)) {
                addedNames.add(name);
                LookupElementBuilder lookup = LookupElementBuilder.create(name)
                        .withTypeText("function")
                        .withIcon(KiteStructureViewIcons.FUNCTION);
                result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0));
            }
        });

        // Add from imported files
        addValueCompletionsFromImports(file, result, addedNames);

        // Add auto-import completions from project files
        addAutoImportCompletions(file, result, addedNames);
    }

    /**
     * Add value completions from imported files.
     */
    private void addValueCompletionsFromImports(PsiFile file, @NotNull CompletionResultSet result, Set<String> addedNames) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null) continue;

            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (!addedNames.contains(name) && !KiteDeclarationHelper.isTypeDeclaration(declarationType)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText(KiteDeclarationHelper.getTypeTextForDeclaration(declarationType))
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteDeclarationHelper.getIconForDeclaration(declarationType));
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 50.0));
                }
            });
        }
    }

    /**
     * Add auto-import completions from project files that are not yet imported.
     */
    private void addAutoImportCompletions(PsiFile file, @NotNull CompletionResultSet result, Set<String> addedNames) {
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
                if (KiteDeclarationHelper.isTypeDeclaration(declarationType)) {
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
     * Context information about an enclosing component instance block.
     */
    record ComponentInstanceContext(String typeName, PsiElement componentInstance) {
    }
}
