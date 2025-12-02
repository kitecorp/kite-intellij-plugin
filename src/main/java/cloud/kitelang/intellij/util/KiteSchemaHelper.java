package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for schema-related operations.
 * Provides methods to find schemas, extract properties, and look up schema info.
 */
public final class KiteSchemaHelper {

    private KiteSchemaHelper() {
        // Utility class - no instances
    }

    /**
     * Find schema properties by name. Returns a map of property name to SchemaPropertyInfo.
     * Searches in current file and imported files.
     */
    public static Map<String, SchemaPropertyInfo> findSchemaProperties(PsiFile file, String schemaName) {
        var properties = new HashMap<String, SchemaPropertyInfo>();

        // Search in current file
        findSchemaPropertiesRecursive(file, schemaName, properties);

        // If not found, search in imported files
        if (properties.isEmpty()) {
            KiteImportHelper.forEachImport(file, importedFile ->
                    findSchemaPropertiesRecursive(importedFile, schemaName, properties));
        }

        return properties;
    }

    /**
     * Recursively search for a schema and extract its properties.
     */
    private static void findSchemaPropertiesRecursive(PsiElement element, String schemaName, Map<String, SchemaPropertyInfo> properties) {
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
     * Get the schema name from a schema declaration.
     */
    @Nullable
    public static String extractSchemaName(PsiElement schemaDecl) {
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
     * Extract property definitions from a schema, returning just the type names.
     * Convenience method for callers that only need property name to type mapping.
     *
     * @param schemaDecl The schema declaration element
     * @return Map of property name to type name
     */
    public static Map<String, String> extractSchemaPropertyTypes(PsiElement schemaDecl) {
        Map<String, SchemaPropertyInfo> fullProperties = new HashMap<>();
        extractSchemaProperties(schemaDecl, fullProperties);

        Map<String, String> typeMap = new HashMap<>();
        for (Map.Entry<String, SchemaPropertyInfo> entry : fullProperties.entrySet()) {
            typeMap.put(entry.getKey(), entry.getValue().type);
        }
        return typeMap;
    }

    /**
     * Extract property definitions from a schema.
     * Pattern inside schema: type propertyName [= defaultValue]
     * Properties with = defaultValue have hasDefaultValue = true.
     */
    public static void extractSchemaProperties(PsiElement schemaDecl, Map<String, SchemaPropertyInfo> properties) {
        boolean insideBraces = false;
        String currentType = null;
        String currentPropertyName = null;

        PsiElement child = schemaDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (type == KiteTokenTypes.RBRACE) {
                    // Save last property if pending (no default value)
                    if (currentPropertyName != null && currentType != null) {
                        properties.put(currentPropertyName, new SchemaPropertyInfo(currentType, false));
                    }
                    break;
                } else if (insideBraces) {
                    // Skip whitespace (but not newlines - those end property definitions)
                    if (type == KiteTokenTypes.WHITESPACE || type == TokenType.WHITE_SPACE) {
                        child = child.getNextSibling();
                        continue;
                    }

                    // Skip decorators
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

                    // Track type -> name pattern
                    if (type == KiteTokenTypes.IDENTIFIER) {
                        String text = child.getText();
                        if (currentType == null) {
                            // First identifier is the type
                            currentType = text;
                        } else {
                            // Second identifier is the property name
                            currentPropertyName = text;
                        }
                    }

                    // Assignment means property has a default value
                    if (type == KiteTokenTypes.ASSIGN) {
                        if (currentPropertyName != null && currentType != null) {
                            properties.put(currentPropertyName, new SchemaPropertyInfo(currentType, true));
                            currentType = null;
                            currentPropertyName = null;
                        }
                    }

                    // Reset on newline (end of property definition without default value)
                    if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                        if (currentPropertyName != null && currentType != null) {
                            // Property without default value
                            properties.put(currentPropertyName, new SchemaPropertyInfo(currentType, false));
                        }
                        currentType = null;
                        currentPropertyName = null;
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Extract the resource type name from a resource declaration.
     * Pattern: resource TypeName instanceName { ... }
     */
    @Nullable
    public static String extractResourceTypeName(PsiElement resourceDecl) {
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
     * Extract the component type name from a component declaration (instantiation only).
     * Pattern: component TypeName instanceName { ... }
     * Returns the type name only if this is an instantiation (2 identifiers before {).
     * Returns null for type definitions (only 1 identifier).
     */
    @Nullable
    public static String extractComponentTypeName(PsiElement componentDecl) {
        if (componentDecl.getNode() == null ||
            componentDecl.getNode().getElementType() != KiteElementTypes.COMPONENT_DECLARATION) {
            return null;
        }

        var child = componentDecl.getFirstChild();
        String firstIdentifier = null;
        String secondIdentifier = null;
        var foundComponent = false;

        while (child != null) {
            if (child.getNode() != null) {
                var childType = child.getNode().getElementType();

                if (childType == KiteTokenTypes.COMPONENT) {
                    foundComponent = true;
                } else if (foundComponent && childType == KiteTokenTypes.IDENTIFIER) {
                    if (firstIdentifier == null) {
                        firstIdentifier = child.getText();
                    } else if (secondIdentifier == null) {
                        secondIdentifier = child.getText();
                    }
                } else if (childType == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            child = child.getNextSibling();
        }

        // If we found two identifiers before {, this is an instantiation
        // Return the first identifier (the type name)
        if (firstIdentifier != null && secondIdentifier != null) {
            return firstIdentifier;
        }

        // Only one identifier - this is a type definition, not an instance
        return null;
    }

    /**
     * Check if a component declaration is an instantiation (not a type definition).
     * An instantiation has pattern: component TypeName instanceName { ... } (2 identifiers)
     * A type definition has pattern: component TypeName { ... } (1 identifier)
     *
     * @param componentDecl The component declaration element
     * @return true if this is an instantiation, false if it's a type definition
     */
    public static boolean isComponentInstantiation(PsiElement componentDecl) {
        return extractComponentTypeName(componentDecl) != null;
    }

    /**
     * Information about a schema property.
     */
    public record SchemaPropertyInfo(String type, boolean hasDefaultValue) {

        /**
         * A property is required if it has no default value.
         */
        public boolean isRequired() {
            return !hasDefaultValue;
        }
    }

}
