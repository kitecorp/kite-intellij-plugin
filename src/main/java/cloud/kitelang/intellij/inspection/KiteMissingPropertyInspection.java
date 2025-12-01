package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import cloud.kitelang.intellij.util.KiteSchemaHelper;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Inspection that detects missing required properties in resources.
 * A required property is one defined in the schema without a default value.
 */
public class KiteMissingPropertyInspection extends KiteInspectionBase {

    @Override
    public @NotNull String getShortName() {
        return "KiteMissingProperty";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        checkResourcesRecursive(file, file, manager, isOnTheFly, problems);
    }

    private void checkResourcesRecursive(PsiElement element,
                                          PsiFile file,
                                          InspectionManager manager,
                                          boolean isOnTheFly,
                                          List<ProblemDescriptor> problems) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            checkResourceDeclaration(element, file, manager, isOnTheFly, problems);
        }

        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Check if this is a component instantiation (not type definition)
            if (KiteSchemaHelper.isComponentInstantiation(element)) {
                checkComponentInstance(element, file, manager, isOnTheFly, problems);
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkResourcesRecursive(child, file, manager, isOnTheFly, problems);
            child = child.getNextSibling();
        }
    }

    private void checkResourceDeclaration(PsiElement resourceDecl,
                                           PsiFile file,
                                           InspectionManager manager,
                                           boolean isOnTheFly,
                                           List<ProblemDescriptor> problems) {
        // Get schema type name
        var schemaName = KiteSchemaHelper.extractResourceTypeName(resourceDecl);
        if (schemaName == null) return;

        // Find the schema and get required properties
        var requiredProperties = findRequiredSchemaProperties(file, schemaName);
        if (requiredProperties.isEmpty()) return;

        // Get properties defined in this resource
        var definedProperties = extractResourceProperties(resourceDecl);

        // Find missing required properties
        var resourceNameElement = findResourceNameElement(resourceDecl);
        for (var required : requiredProperties) {
            if (!definedProperties.contains(required)) {
                var targetElement = resourceNameElement != null ? resourceNameElement : resourceDecl;
                var problem = createWarning(
                        manager,
                        targetElement,
                        "Missing required property '" + required + "'",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }

    private void checkComponentInstance(PsiElement componentDecl,
                                          PsiFile file,
                                          InspectionManager manager,
                                          boolean isOnTheFly,
                                          List<ProblemDescriptor> problems) {
        // Get component type name
        var componentTypeName = KiteSchemaHelper.extractComponentTypeName(componentDecl);
        if (componentTypeName == null) return;

        // Find the component definition and get required inputs
        var requiredInputs = findRequiredComponentInputs(file, componentTypeName);
        if (requiredInputs.isEmpty()) return;

        // Get inputs defined in this component instance
        var definedInputs = extractComponentInstanceProperties(componentDecl);

        // Find missing required inputs
        var instanceNameElement = findComponentInstanceNameElement(componentDecl);
        for (var required : requiredInputs) {
            if (!definedInputs.contains(required)) {
                var targetElement = instanceNameElement != null ? instanceNameElement : componentDecl;
                var problem = createWarning(
                        manager,
                        targetElement,
                        "Missing required property '" + required + "'",
                        isOnTheFly
                );
                problems.add(problem);
            }
        }
    }

    /**
     * Find required properties from a schema (properties without default values).
     */
    private Set<String> findRequiredSchemaProperties(PsiFile file, String schemaName) {
        var required = new HashSet<String>();
        var foundSchema = new boolean[]{false};

        // Search in current file
        findSchemaAndExtractRequired(file, schemaName, required, foundSchema);

        // If not found, search in imported files
        if (!foundSchema[0]) {
            KiteImportHelper.forEachImport(file, importedFile ->
                    findSchemaAndExtractRequired(importedFile, schemaName, required, foundSchema));
        }

        return required;
    }

    private void findSchemaAndExtractRequired(PsiElement element, String schemaName, Set<String> required, boolean[] foundSchema) {
        if (element == null || element.getNode() == null || foundSchema[0]) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.SCHEMA_DECLARATION) {
            var name = KiteSchemaHelper.extractSchemaName(element);
            if (schemaName.equals(name)) {
                foundSchema[0] = true;
                extractRequiredSchemaProperties(element, required);
                return;
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            findSchemaAndExtractRequired(child, schemaName, required, foundSchema);
            if (foundSchema[0]) return;
            child = child.getNextSibling();
        }
    }

    /**
     * Extract required properties (those without default values) from a schema.
     */
    private void extractRequiredSchemaProperties(PsiElement schemaDecl, Set<String> required) {
        boolean insideBraces = false;
        String currentType = null;
        String currentPropertyName = null;
        boolean hasDefault = false;

        var child = schemaDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    // End of block - check last property
                    if (currentPropertyName != null && !hasDefault) {
                        required.add(currentPropertyName);
                    }
                    break;
                } else if (insideBraces) {
                    // Skip whitespace
                    if (KitePsiUtil.isWhitespace(type)) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Skip decorators (@ followed by identifier)
                    if (type == KiteTokenTypes.AT) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Handle 'any' keyword as type
                    if (type == KiteTokenTypes.ANY) {
                        currentType = "any";
                        child = child.getNextSibling();
                        continue;
                    }

                    // Skip array literal brackets
                    if (type == KiteElementTypes.ARRAY_LITERAL) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Track type -> name pattern
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        var text = child.getText();
                        // Skip decorator names like "cloud", "minValue", etc.
                        if (isDecoratorName(text)) {
                            child = child.getNextSibling();
                            continue;
                        }

                        if (currentType == null) {
                            currentType = text;
                        } else {
                            currentPropertyName = text;
                        }
                    }

                    // = means property has default
                    if (type == KiteTokenTypes.ASSIGN) {
                        hasDefault = true;
                    }

                    // Newline ends property definition
                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                        if (currentPropertyName != null && !hasDefault) {
                            required.add(currentPropertyName);
                        }
                        currentType = null;
                        currentPropertyName = null;
                        hasDefault = false;
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private boolean isDecoratorName(String name) {
        return Set.of("cloud", "minValue", "maxValue", "minLength", "maxLength",
                "nonEmpty", "validate", "allowed", "unique", "existing",
                "sensitive", "dependsOn", "tags", "provider", "description",
                "count").contains(name);
    }

    /**
     * Find required inputs from a component definition (inputs without default values).
     */
    private Set<String> findRequiredComponentInputs(PsiFile file, String componentName) {
        var required = new HashSet<String>();
        var foundComponent = new boolean[]{false};

        // Search in current file
        findComponentAndExtractRequired(file, componentName, required, foundComponent);

        // If not found, search in imported files
        if (!foundComponent[0]) {
            KiteImportHelper.forEachImport(file, importedFile ->
                    findComponentAndExtractRequired(importedFile, componentName, required, foundComponent));
        }

        return required;
    }

    private void findComponentAndExtractRequired(PsiElement element, String componentName, Set<String> required, boolean[] foundComponent) {
        if (element == null || element.getNode() == null || foundComponent[0]) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Only check component type definitions, not instances
            if (!KiteSchemaHelper.isComponentInstantiation(element)) {
                var name = extractComponentDefinitionName(element);
                if (componentName.equals(name)) {
                    foundComponent[0] = true;
                    extractRequiredComponentInputs(element, required);
                    return;
                }
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            findComponentAndExtractRequired(child, componentName, required, foundComponent);
            if (foundComponent[0]) return;
            child = child.getNextSibling();
        }
    }

    /**
     * Extract required inputs (those without default values) from a component.
     */
    private void extractRequiredComponentInputs(PsiElement componentDecl, Set<String> required) {
        boolean insideBraces = false;
        boolean inInput = false;
        String currentType = null;
        String currentPropertyName = null;
        boolean hasDefault = false;

        var child = componentDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    // End of block - check last property
                    if (inInput && currentPropertyName != null && !hasDefault) {
                        required.add(currentPropertyName);
                    }
                    break;
                } else if (insideBraces) {
                    // Skip whitespace
                    if (KitePsiUtil.isWhitespace(type)) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // INPUT keyword starts an input declaration
                    if (type == KiteTokenTypes.INPUT) {
                        inInput = true;
                        currentType = null;
                        currentPropertyName = null;
                        hasDefault = false;
                        child = child.getNextSibling();
                        continue;
                    }

                    // OUTPUT or other keywords end input tracking
                    if (type == KiteTokenTypes.OUTPUT || type == KiteTokenTypes.VAR) {
                        inInput = false;
                        currentType = null;
                        currentPropertyName = null;
                        hasDefault = false;
                        child = child.getNextSibling();
                        continue;
                    }

                    if (inInput) {
                        // Handle 'any' keyword as type
                        if (type == KiteTokenTypes.ANY) {
                            currentType = "any";
                            child = child.getNextSibling();
                            continue;
                        }

                        // Skip array literal brackets
                        if (type == KiteElementTypes.ARRAY_LITERAL) {
                            child = child.getNextSibling();
                            continue;
                        }

                        // Track type -> name pattern
                        if (type == KiteTokenTypes.IDENTIFIER) {
                            if (currentType == null) {
                                currentType = child.getText();
                            } else {
                                currentPropertyName = child.getText();
                            }
                        }

                        // = means property has default
                        if (type == KiteTokenTypes.ASSIGN) {
                            hasDefault = true;
                        }

                        // Newline ends property definition
                        if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                            if (currentPropertyName != null && !hasDefault) {
                                required.add(currentPropertyName);
                            }
                            currentType = null;
                            currentPropertyName = null;
                            hasDefault = false;
                            inInput = false;
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Extract properties defined in a resource block.
     */
    private Set<String> extractResourceProperties(PsiElement resourceDecl) {
        var properties = new HashSet<String>();
        boolean insideBraces = false;

        var child = resourceDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBraces) {
                    // Look for identifier followed by =
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        var next = KitePsiUtil.skipWhitespace(child.getNextSibling());
                        if (next != null && next.getNode() != null &&
                            next.getNode().getElementType() == KiteTokenTypes.ASSIGN) {
                            properties.add(child.getText());
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }

        return properties;
    }

    /**
     * Extract properties defined in a component instance block.
     */
    private Set<String> extractComponentInstanceProperties(PsiElement componentDecl) {
        // Same logic as resource properties
        return extractResourceProperties(componentDecl);
    }

    @Nullable
    private PsiElement findResourceNameElement(PsiElement resourceDecl) {
        boolean foundType = false;
        var child = resourceDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.RESOURCE) {
                    child = child.getNextSibling();
                    continue;
                }

                if (type == KiteTokenTypes.IDENTIFIER) {
                    if (foundType) {
                        return child; // Second identifier is the name
                    }
                    foundType = true;
                }

                if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    @Nullable
    private PsiElement findComponentInstanceNameElement(PsiElement componentDecl) {
        boolean foundType = false;
        var child = componentDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.COMPONENT) {
                    child = child.getNextSibling();
                    continue;
                }

                if (type == KiteTokenTypes.IDENTIFIER) {
                    if (foundType) {
                        return child; // Second identifier is the instance name
                    }
                    foundType = true;
                }

                if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    @Nullable
    private String extractComponentDefinitionName(PsiElement componentDecl) {
        boolean foundComponent = false;
        var child = componentDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.COMPONENT) {
                    foundComponent = true;
                } else if (foundComponent && type == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier is the name
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }
}
