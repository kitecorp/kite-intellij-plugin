package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.quickfix.AddRequiredPropertyQuickFix;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import cloud.kitelang.intellij.util.KiteSchemaHelper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

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
    protected boolean isFileLevelInspection() {
        return true;
    }

    @Override
    protected void checkFile(@NotNull KiteFile file, @NotNull ProblemsHolder holder) {
        // Traverse all elements in the file
        checkElementRecursively(file, holder);
    }

    @Override
    protected void checkElement(@NotNull PsiElement element, @NotNull ProblemsHolder holder) {
        // Not used - this is a file-level inspection
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

        // Use KiteSchemaHelper to find schema properties (reuse tested utility)
        var schemaProperties = KiteSchemaHelper.findSchemaProperties(file, schemaName);
        if (schemaProperties.isEmpty()) return;

        // Get properties defined in this resource
        var definedProperties = extractResourceProperties(resourceDecl);

        // Find missing required properties
        var resourceNameElement = findResourceNameElement(resourceDecl);
        for (var entry : schemaProperties.entrySet()) {
            String propName = entry.getKey();
            var propInfo = entry.getValue();

            // Only check required properties (those without default values)
            if (propInfo.isRequired() && !definedProperties.contains(propName)) {
                var targetElement = resourceNameElement != null ? resourceNameElement : resourceDecl;
                LocalQuickFix quickFix = new AddRequiredPropertyQuickFix(propName, propInfo.type());
                registerWarning(holder, targetElement,
                        "Missing required property '" + propName + "'", quickFix);
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
