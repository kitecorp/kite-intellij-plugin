package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.quickfix.AddRequiredPropertyQuickFix;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import cloud.kitelang.intellij.util.KiteSchemaHelper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
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
    protected void checkElement(@NotNull PsiElement element, @NotNull ProblemsHolder holder) {
        // Only run analysis once at the file level
        if (!(element instanceof PsiFile)) {
            return;
        }

        // Traverse all elements in the file
        checkElementRecursively(element, holder);
    }

    /**
     * Recursively check all elements in the file.
     */
    private void checkElementRecursively(@NotNull PsiElement element, @NotNull ProblemsHolder holder) {
        if (element.getNode() == null) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            checkResourceDeclaration(element, holder);
        } else if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Check if this is a component instantiation (not type definition)
            if (KiteSchemaHelper.isComponentInstantiation(element)) {
                checkComponentInstance(element, holder);
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            checkElementRecursively(child, holder);
            child = child.getNextSibling();
        }
    }

    private void checkResourceDeclaration(PsiElement resourceDecl, ProblemsHolder holder) {
        // Get schema type name
        var schemaName = KiteSchemaHelper.extractResourceTypeName(resourceDecl);
        if (schemaName == null) return;

        PsiFile file = resourceDecl.getContainingFile();
        if (file == null) return;

        // Find the schema and get required properties with their types
        var requiredPropertiesWithTypes = findRequiredSchemaPropertiesWithTypes(file, schemaName);
        if (requiredPropertiesWithTypes.isEmpty()) return;

        // Get properties defined in this resource
        var definedProperties = extractResourceProperties(resourceDecl);

        // Find missing required properties
        var resourceNameElement = findResourceNameElement(resourceDecl);
        for (var entry : requiredPropertiesWithTypes.entrySet()) {
            String required = entry.getKey();
            String propertyType = entry.getValue();
            if (!definedProperties.contains(required)) {
                var targetElement = resourceNameElement != null ? resourceNameElement : resourceDecl;
                LocalQuickFix quickFix = new AddRequiredPropertyQuickFix(required, propertyType);
                registerWarning(holder, targetElement,
                        "Missing required property '" + required + "'", quickFix);
            }
        }
    }

    private void checkComponentInstance(PsiElement componentDecl, ProblemsHolder holder) {
        // Get component type name
        var componentTypeName = KiteSchemaHelper.extractComponentTypeName(componentDecl);
        if (componentTypeName == null) return;

        PsiFile file = componentDecl.getContainingFile();
        if (file == null) return;

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
                registerWarning(holder, targetElement,
                        "Missing required property '" + required + "'");
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

    /**
     * Find required properties from a schema with their types.
     * Returns a map of property name to property type.
     */
    private Map<String, String> findRequiredSchemaPropertiesWithTypes(PsiFile file, String schemaName) {
        var requiredWithTypes = new LinkedHashMap<String, String>();
        var foundSchema = new boolean[]{false};

        // Search in current file
        findSchemaAndExtractRequiredWithTypes(file, schemaName, requiredWithTypes, foundSchema);

        // If not found, search in imported files
        if (!foundSchema[0]) {
            KiteImportHelper.forEachImport(file, importedFile ->
                    findSchemaAndExtractRequiredWithTypes(importedFile, schemaName, requiredWithTypes, foundSchema));
        }

        return requiredWithTypes;
    }

    private void findSchemaAndExtractRequiredWithTypes(PsiElement element, String schemaName,
                                                        Map<String, String> requiredWithTypes, boolean[] foundSchema) {
        if (element == null || element.getNode() == null || foundSchema[0]) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.SCHEMA_DECLARATION) {
            var name = KiteSchemaHelper.extractSchemaName(element);
            if (schemaName.equals(name)) {
                foundSchema[0] = true;
                extractRequiredSchemaPropertiesWithTypes(element, requiredWithTypes);
                return;
            }
        }

        // Recurse into children
        var child = element.getFirstChild();
        while (child != null) {
            findSchemaAndExtractRequiredWithTypes(child, schemaName, requiredWithTypes, foundSchema);
            if (foundSchema[0]) return;
            child = child.getNextSibling();
        }
    }

    /**
     * Extract required properties with their types from a schema.
     */
    private void extractRequiredSchemaPropertiesWithTypes(PsiElement schemaDecl, Map<String, String> requiredWithTypes) {
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
                    if (currentPropertyName != null && currentType != null && !hasDefault) {
                        requiredWithTypes.put(currentPropertyName, currentType);
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

                    // Handle 'any' keyword as a type
                    if (type == KiteTokenTypes.ANY) {
                        currentType = "any";
                        child = child.getNextSibling();
                        continue;
                    }

                    // Identifier could be type or property name (including built-in types like string, number, boolean)
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        if (currentType == null) {
                            // This is the type
                            currentType = child.getText();
                        } else {
                            // This is the property name
                            currentPropertyName = child.getText();
                        }
                        child = child.getNextSibling();
                        continue;
                    }

                    // Array type marker []
                    if (type == KiteElementTypes.ARRAY_LITERAL ||
                        type == KiteTokenTypes.LBRACK) {
                        if (currentType != null) {
                            currentType = currentType + "[]";
                        }
                        child = child.getNextSibling();
                        continue;
                    }

                    // Assignment means property has default value
                    if (type == KiteTokenTypes.ASSIGN) {
                        hasDefault = true;
                        child = child.getNextSibling();
                        continue;
                    }

                    // Newline ends property definition
                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                        if (currentPropertyName != null && currentType != null && !hasDefault) {
                            requiredWithTypes.put(currentPropertyName, currentType);
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

    private void findSchemaAndExtractRequired(PsiElement element, String schemaName,
                                               Set<String> required, boolean[] foundSchema) {
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
     * Extract required properties (without default values) from a schema declaration.
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
                    if (KitePsiUtil.isWhitespace(type)) {
                        child = child.getNextSibling();
                        continue;
                    }

                    if (type == KiteTokenTypes.AT) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Handle 'any' keyword as a type
                    if (type == KiteTokenTypes.ANY) {
                        currentType = "any";
                        child = child.getNextSibling();
                        continue;
                    }

                    // Identifier could be type or property name (including built-in types like string, number, boolean)
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        if (currentType == null) {
                            currentType = child.getText();
                        } else {
                            currentPropertyName = child.getText();
                        }
                        child = child.getNextSibling();
                        continue;
                    }

                    if (type == KiteElementTypes.ARRAY_LITERAL ||
                        type == KiteTokenTypes.LBRACK) {
                        child = child.getNextSibling();
                        continue;
                    }

                    if (type == KiteTokenTypes.ASSIGN) {
                        hasDefault = true;
                        child = child.getNextSibling();
                        continue;
                    }

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

    /**
     * Extract property names defined in a resource declaration.
     */
    private Set<String> extractResourceProperties(PsiElement resourceDecl) {
        var properties = new HashSet<String>();
        boolean insideBraces = false;
        String currentPropertyName = null;

        var child = resourceDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBraces) {
                    if (KitePsiUtil.isWhitespace(type)) {
                        child = child.getNextSibling();
                        continue;
                    }

                    if (type == KiteTokenTypes.IDENTIFIER && currentPropertyName == null) {
                        currentPropertyName = child.getText();
                    } else if (type == KiteTokenTypes.ASSIGN && currentPropertyName != null) {
                        properties.add(currentPropertyName);
                        currentPropertyName = null;
                    } else if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                        currentPropertyName = null;
                    }
                }
            }
            child = child.getNextSibling();
        }

        return properties;
    }

    /**
     * Find the name element of a resource declaration (the identifier after the type).
     */
    @Nullable
    private PsiElement findResourceNameElement(PsiElement resourceDecl) {
        boolean foundType = false;
        var child = resourceDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();
                if (type == KiteTokenTypes.IDENTIFIER) {
                    if (foundType) {
                        return child; // This is the name
                    }
                    foundType = true; // First identifier is the type
                } else if (type == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    // ========== Component Instance Helpers ==========

    /**
     * Find required inputs from a component definition.
     */
    private Set<String> findRequiredComponentInputs(PsiFile file, String componentTypeName) {
        var required = new HashSet<String>();
        var foundComponent = new boolean[]{false};

        findComponentAndExtractRequiredInputs(file, componentTypeName, required, foundComponent);

        if (!foundComponent[0]) {
            KiteImportHelper.forEachImport(file, importedFile ->
                    findComponentAndExtractRequiredInputs(importedFile, componentTypeName, required, foundComponent));
        }

        return required;
    }

    private void findComponentAndExtractRequiredInputs(PsiElement element, String componentTypeName,
                                                        Set<String> required, boolean[] foundComponent) {
        if (element == null || element.getNode() == null || foundComponent[0]) return;

        var type = element.getNode().getElementType();

        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            // Only match component definitions (not instances)
            if (!KiteSchemaHelper.isComponentInstantiation(element)) {
                var name = KiteSchemaHelper.extractComponentTypeName(element);
                if (componentTypeName.equals(name)) {
                    foundComponent[0] = true;
                    extractRequiredComponentInputs(element, required);
                    return;
                }
            }
        }

        var child = element.getFirstChild();
        while (child != null) {
            findComponentAndExtractRequiredInputs(child, componentTypeName, required, foundComponent);
            if (foundComponent[0]) return;
            child = child.getNextSibling();
        }
    }

    /**
     * Extract required inputs (without default values) from a component definition.
     */
    private void extractRequiredComponentInputs(PsiElement componentDecl, Set<String> required) {
        boolean insideBraces = false;

        var child = componentDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    break;
                } else if (insideBraces && type == KiteElementTypes.INPUT_DECLARATION) {
                    // Check if input has a default value
                    if (!hasDefaultValue(child)) {
                        var name = extractInputName(child);
                        if (name != null) {
                            required.add(name);
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Check if an input declaration has a default value.
     */
    private boolean hasDefaultValue(PsiElement inputDecl) {
        var child = inputDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null &&
                child.getNode().getElementType() == KiteTokenTypes.ASSIGN) {
                return true;
            }
            child = child.getNextSibling();
        }
        return false;
    }

    /**
     * Extract the name from an input declaration.
     */
    @Nullable
    private String extractInputName(PsiElement inputDecl) {
        boolean foundType = false;
        var child = inputDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();
                // Type can be 'any' keyword or an IDENTIFIER (including built-in types like string, number, boolean)
                if (type == KiteTokenTypes.ANY || type == KiteTokenTypes.IDENTIFIER) {
                    if (foundType) {
                        return child.getText(); // This is the name
                    }
                    foundType = true;
                } else if (type == KiteTokenTypes.ASSIGN) {
                    break;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Extract property names defined in a component instance.
     */
    private Set<String> extractComponentInstanceProperties(PsiElement componentDecl) {
        return extractResourceProperties(componentDecl); // Same logic as resources
    }

    /**
     * Find the instance name element of a component instantiation.
     */
    @Nullable
    private PsiElement findComponentInstanceNameElement(PsiElement componentDecl) {
        return findResourceNameElement(componentDecl); // Same logic as resources
    }
}
