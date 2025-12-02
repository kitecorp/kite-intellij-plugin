package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePropertyHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import cloud.kitelang.intellij.util.KiteSchemaHelper;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides code completion inside resource blocks.
 * Handles:
 * - Schema property name completion (left side of =)
 * - Value completion (right side of =)
 * - Property access on resources (resource.property)
 */
public class KiteResourceCompletionProvider extends CompletionProvider<CompletionParameters> {

    /**
     * Check if position is inside a resource block.
     * Public static method for other providers to check resource context.
     */
    public static boolean isInResourceContext(@NotNull PsiElement position) {
        return getEnclosingResourceContext(position) != null;
    }

    /**
     * Get the resource context if we're inside a resource block.
     * Returns null if not inside a resource block.
     */
    @Nullable
    static ResourceContext getEnclosingResourceContext(PsiElement position) {
        PsiElement current = position;
        while (current != null) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.RESOURCE_DECLARATION) {
                if (KitePsiUtil.isInsideBraces(position, current)) {
                    String schemaName = KiteSchemaHelper.extractResourceTypeName(current);
                    if (schemaName != null) {
                        return new ResourceContext(schemaName, current);
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    // ========== Resource Context Detection ==========

    /**
     * Check if the cursor is before the assignment operator (on the left side of =).
     */
    static boolean isBeforeAssignment(PsiElement position) {
        PsiElement current = position.getPrevSibling();
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

        return true;
    }

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
     * Add schema property completions with high priority.
     * Excludes properties that are already defined in the resource block.
     * Excludes @cloud properties (they are set by the cloud provider).
     */
    private void addSchemaPropertyCompletions(PsiFile file, @NotNull CompletionResultSet result, ResourceContext resourceContext) {
        Map<String, KiteSchemaHelper.SchemaPropertyInfo> schemaProperties =
                KiteSchemaHelper.findSchemaProperties(file, resourceContext.schemaName);

        Set<String> existingProperties = KitePropertyHelper.collectExistingPropertyNames(resourceContext.resourceDeclaration);

        for (Map.Entry<String, KiteSchemaHelper.SchemaPropertyInfo> entry : schemaProperties.entrySet()) {
            String propertyName = entry.getKey();
            KiteSchemaHelper.SchemaPropertyInfo propInfo = entry.getValue();

            // Skip properties that are already defined
            if (existingProperties.contains(propertyName)) {
                continue;
            }

            LookupElementBuilder element = LookupElementBuilder.create(propertyName)
                    .withTypeText(propInfo.type())
                    .withIcon(KiteStructureViewIcons.PROPERTY)
                    .withBoldness(true)
                    .withInsertHandler((ctx, item) -> {
                        ctx.getDocument().insertString(ctx.getTailOffset(), " = ");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                    });

            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
        }
    }

    // ========== Schema Property Completion ==========

    /**
     * Add value completions for the right side of assignments in resource blocks.
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
    }

    // ========== Value Completion (Right Side of =) ==========

    /**
     * Add value completions from imported files.
     */
    private void addValueCompletionsFromImports(PsiFile file, @NotNull CompletionResultSet result, Set<String> addedNames) {
        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);

        for (PsiFile importedFile : importedFiles) {
            if (importedFile == null) continue;

            // Variables
            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.VARIABLE_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("variable")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.VARIABLE);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 450.0));
                }
            });

            // Inputs
            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.INPUT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("input")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.INPUT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 400.0));
                }
            });

            // Outputs
            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.OUTPUT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("output")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.OUTPUT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 350.0));
                }
            });

            // Resources
            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.RESOURCE_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("resource")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.RESOURCE);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 250.0));
                }
            });

            // Components
            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
                if (declarationType == KiteElementTypes.COMPONENT_DECLARATION && !addedNames.contains(name)) {
                    addedNames.add(name);
                    LookupElementBuilder lookup = LookupElementBuilder.create(name)
                            .withTypeText("component")
                            .withTailText(" (" + importedFile.getName() + ")", true)
                            .withIcon(KiteStructureViewIcons.COMPONENT);
                    result.addElement(PrioritizedLookupElement.withPriority(lookup, 150.0));
                }
            });

            // Functions
            KiteDeclarationHelper.collectDeclarations(importedFile, (name, declarationType, element) -> {
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
     * Context information about an enclosing resource block.
     */
    record ResourceContext(String schemaName, PsiElement resourceDeclaration) {
    }
}
