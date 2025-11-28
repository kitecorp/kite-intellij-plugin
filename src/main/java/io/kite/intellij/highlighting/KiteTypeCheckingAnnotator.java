package io.kite.intellij.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import io.kite.intellij.reference.KiteImportHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Annotator that performs type checking and reference validation.
 * <p>
 * Features:
 * - Undefined reference detection: Warns when identifiers don't resolve to any declaration
 * - Type mismatch detection: Warns when assigned values don't match declared types
 */
public class KiteTypeCheckingAnnotator implements Annotator {

    // Set of built-in type names that should not be flagged as undefined
    private static final Set<String> BUILTIN_TYPES = Set.of(
            "string", "number", "boolean", "any", "object", "void",
            "String", "Number", "Boolean", "Any", "Object", "Void"
    );

    // Set of keywords that should not be treated as identifiers
    private static final Set<String> KEYWORDS = Set.of(
            "if", "else", "while", "for", "in", "return", "import", "from",
            "fun", "var", "type", "init", "this", "true", "false", "null",
            "input", "output", "resource", "component", "schema"
    );

    // Set of built-in global functions that don't need to be declared
    private static final Set<String> BUILTIN_FUNCTIONS = Set.of(
            "print", "println"
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process at file level to avoid redundant checks
        if (!(element instanceof PsiFile)) {
            return;
        }

        PsiFile file = (PsiFile) element;
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Collect all declared names in the file first
        Set<String> declaredNames = new HashSet<>();
        collectAllDeclaredNames(file, declaredNames);

        // Also collect declared names from imported files
        collectDeclaredNamesFromImports(file, declaredNames, new HashSet<>());

        // Check all identifiers for undefined references
        checkUndefinedReferences(file, declaredNames, holder);

        // Check for type mismatches in variable declarations
        checkTypeMismatches(file, holder);
    }

    /**
     * Collect all declared names in the file (including nested scopes).
     */
    private void collectAllDeclaredNames(PsiElement element, Set<String> names) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check if this is a declaration
        if (isDeclarationType(type)) {
            String name = findDeclarationName(element, type);
            if (name != null) {
                names.add(name);
            }

            // For function declarations, also collect parameter names
            if (type == KiteElementTypes.FUNCTION_DECLARATION) {
                collectFunctionParameters(element, names);
            }
        }

        // Check for loop variable declarations (for x in ...)
        if (type == KiteElementTypes.FOR_STATEMENT) {
            String loopVar = findForLoopVariable(element);
            if (loopVar != null) {
                names.add(loopVar);
            }
        }

        // Handle raw VAR tokens that might not be wrapped in VARIABLE_DECLARATION
        // This can happen inside function bodies
        if (type == KiteTokenTypes.VAR) {
            String varName = findVarNameFromToken(element);
            if (varName != null) {
                names.add(varName);
            }
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectAllDeclaredNames(child, names);
        }
    }

    /**
     * Find variable name from a raw VAR token.
     * Pattern: var [type] name = value
     * The variable name is the last identifier before =
     */
    @Nullable
    private String findVarNameFromToken(PsiElement varToken) {
        PsiElement lastIdentifier = null;
        PsiElement sibling = varToken.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType siblingType = sibling.getNode().getElementType();

            // Skip whitespace
            if (isWhitespace(siblingType)) {
                sibling = sibling.getNextSibling();
                continue;
            }

            // Collect identifiers
            if (siblingType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = sibling;
            }

            // Stop at = and return the last identifier found
            if (siblingType == KiteTokenTypes.ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier.getText();
                }
                break;
            }

            // Stop at newline (incomplete declaration)
            if (siblingType == KiteTokenTypes.NL) {
                break;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    /**
     * Collect function parameter names from a function declaration.
     * Function signature: fun name(type param1, type param2) returnType { ... }
     * Parameters are identifiers inside parentheses, alternating with types.
     */
    private void collectFunctionParameters(PsiElement functionDecl, Set<String> names) {
        boolean insideParens = false;
        List<String> identifiersInParens = new ArrayList<>();

        for (PsiElement child = functionDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LPAREN) {
                insideParens = true;
                identifiersInParens.clear();
                continue;
            }

            if (childType == KiteTokenTypes.RPAREN) {
                // Process collected identifiers - every second one is a parameter name
                // Pattern: type1 param1, type2 param2 => [type1, param1, type2, param2]
                for (int i = 1; i < identifiersInParens.size(); i += 2) {
                    names.add(identifiersInParens.get(i));
                }
                insideParens = false;
                break;
            }

            if (insideParens && childType == KiteTokenTypes.IDENTIFIER) {
                identifiersInParens.add(child.getText());
            }
        }
    }

    /**
     * Collect declared names from imported files (for cross-file reference validation).
     */
    private void collectDeclaredNamesFromImports(PsiFile file, Set<String> names, Set<String> visitedPaths) {
        // Get imported files
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

            // Collect declared names from this imported file
            collectAllDeclaredNames(importedFile, names);

            // Recursively check imports in the imported file
            collectDeclaredNamesFromImports(importedFile, names, visitedPaths);
        }
    }

    /**
     * Check all identifiers for undefined references.
     */
    private void checkUndefinedReferences(PsiElement element, Set<String> declaredNames, AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check identifier tokens
        if (type == KiteTokenTypes.IDENTIFIER) {
            String name = element.getText();

            // Skip if it's a keyword, builtin type, or declared name
            if (KEYWORDS.contains(name) || BUILTIN_TYPES.contains(name)) {
                return;
            }

            // Skip built-in global functions (print, println, etc.)
            if (BUILTIN_FUNCTIONS.contains(name)) {
                return;
            }

            // Skip if this identifier is a declaration name (not a reference)
            if (isDeclarationName(element)) {
                return;
            }

            // Skip if this is a property access (identifier after DOT)
            if (isPropertyAccess(element)) {
                return;
            }

            // Skip if this is a type annotation (identifier after another identifier in declaration)
            if (isTypeAnnotation(element)) {
                return;
            }

            // Skip if this is a schema/resource/component property definition
            // Pattern: type propertyName (e.g., "string host" in schema)
            if (isPropertyDefinition(element)) {
                return;
            }

            // Skip decorator names (identifiers after @)
            // Decorators are global/built-in and don't need to be imported
            if (isDecoratorName(element)) {
                return;
            }

            // Check if the name is declared
            if (!declaredNames.contains(name)) {
                holder.newAnnotation(HighlightSeverity.WARNING,
                                "Cannot resolve symbol '" + name + "'")
                        .range(element)
                        .create();
            }
        }

        // Recurse into children (but not into declaration types - they're handled specially)
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            checkUndefinedReferences(child, declaredNames, holder);
        }
    }

    /**
     * Check for type mismatches in variable declarations.
     */
    private void checkTypeMismatches(PsiElement element, AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check variable declarations with explicit types
        if (type == KiteElementTypes.VARIABLE_DECLARATION ||
            type == KiteElementTypes.INPUT_DECLARATION ||
            type == KiteElementTypes.OUTPUT_DECLARATION) {
            checkDeclarationTypeMismatch(element, holder);
        }

        // Check resource property type mismatches against schema
        if (type == KiteElementTypes.RESOURCE_DECLARATION) {
            checkResourcePropertyTypeMismatches(element, holder);
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            checkTypeMismatches(child, holder);
        }
    }

    /**
     * Check if a declaration's value type matches its declared type.
     */
    private void checkDeclarationTypeMismatch(PsiElement declaration, AnnotationHolder holder) {
        // Parse declaration: keyword [type] name = value
        String declaredType = null;
        PsiElement valueElement = null;
        PsiElement nameElement = null;

        boolean foundKeyword = false;
        PsiElement firstIdentifier = null;
        PsiElement secondIdentifier = null;

        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            // Skip whitespace
            if (isWhitespace(childType)) continue;

            // Track keyword
            if (childType == KiteTokenTypes.VAR ||
                childType == KiteTokenTypes.INPUT ||
                childType == KiteTokenTypes.OUTPUT) {
                foundKeyword = true;
                continue;
            }

            if (foundKeyword && childType == KiteTokenTypes.IDENTIFIER) {
                if (firstIdentifier == null) {
                    firstIdentifier = child;
                } else if (secondIdentifier == null) {
                    secondIdentifier = child;
                }
            }

            if (childType == KiteTokenTypes.ASSIGN) {
                // Everything after = is the value
                valueElement = skipWhitespace(child.getNextSibling());
                break;
            }
        }

        // Determine if there's an explicit type
        // Pattern: var type name = value (two identifiers before =)
        // Pattern: var name = value (one identifier before =)
        if (secondIdentifier != null) {
            // First identifier is type, second is name
            declaredType = firstIdentifier.getText();
            nameElement = secondIdentifier;
        } else if (firstIdentifier != null) {
            // Only name, no explicit type
            nameElement = firstIdentifier;
        }

        // If no explicit type or no value, skip type checking
        if (declaredType == null || valueElement == null) {
            return;
        }

        // Infer the value's type
        String valueType = inferType(valueElement);
        if (valueType == null) {
            return; // Can't infer type
        }

        // Check for mismatch
        if (!isTypeCompatible(declaredType, valueType)) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "Type mismatch: expected '" + declaredType + "' but got '" + valueType + "'")
                    .range(valueElement)
                    .create();
        }
    }

    /**
     * Check resource property type mismatches against schema definitions.
     * For example: if schema defines "string name" but resource assigns "name = 123",
     * this should show an error.
     */
    private void checkResourcePropertyTypeMismatches(PsiElement resourceDeclaration, AnnotationHolder holder) {
        // Get the resource type name (first identifier after RESOURCE keyword)
        String schemaName = getResourceTypeName(resourceDeclaration);
        if (schemaName == null) return;

        // Find the matching schema and get property types
        PsiFile file = resourceDeclaration.getContainingFile();
        if (file == null) return;

        java.util.Map<String, String> schemaProperties = findSchemaProperties(file, schemaName);
        if (schemaProperties.isEmpty()) return;

        // Find property assignments inside the resource body
        boolean insideBraces = false;
        for (PsiElement child = resourceDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

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
                PsiElement next = skipWhitespace(child.getNextSibling());
                if (next != null && next.getNode() != null &&
                    next.getNode().getElementType() == KiteTokenTypes.ASSIGN) {

                    // Get the value element
                    PsiElement valueElement = skipWhitespace(next.getNextSibling());
                    if (valueElement == null) continue;

                    // Get expected type from schema
                    String expectedType = schemaProperties.get(propertyName);
                    if (expectedType == null) continue;

                    // Infer actual value type
                    String actualType = inferType(valueElement);
                    if (actualType == null) continue;

                    // Check for mismatch
                    if (!isTypeCompatible(expectedType, actualType)) {
                        holder.newAnnotation(HighlightSeverity.ERROR,
                                        "Type mismatch: property '" + propertyName + "' expects '" + expectedType +
                                        "' but got '" + actualType + "'")
                                .range(valueElement)
                                .create();
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
    private String getResourceTypeName(PsiElement resourceDeclaration) {
        boolean foundResource = false;
        for (PsiElement child = resourceDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

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
    private void findSchemaPropertiesRecursive(PsiElement element, String schemaName, java.util.Map<String, String> properties) {
        if (element == null || element.getNode() == null) return;

        if (element.getNode().getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            // Check if this is the schema we're looking for
            String name = getSchemaNameFromDeclaration(element);
            if (schemaName.equals(name)) {
                extractSchemaProperties(element, properties);
                return;
            }
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
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
    private String getSchemaNameFromDeclaration(PsiElement schemaDeclaration) {
        boolean foundSchema = false;
        for (PsiElement child = schemaDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

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
    private void extractSchemaProperties(PsiElement schemaDeclaration, java.util.Map<String, String> properties) {
        boolean insideBraces = false;
        String currentType = null;

        for (PsiElement child = schemaDeclaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.LBRACE) {
                insideBraces = true;
                continue;
            }
            if (childType == KiteTokenTypes.RBRACE) {
                break;
            }

            if (!insideBraces) continue;

            // Skip whitespace and newlines
            if (isWhitespace(childType)) {
                continue;
            }

            // Handle 'any' keyword as a type
            if (childType == KiteTokenTypes.ANY) {
                currentType = "any";
                continue;
            }

            // Pattern: type propertyName [= defaultValue]
            if (childType == KiteTokenTypes.IDENTIFIER) {
                if (currentType == null) {
                    // This identifier is the type
                    currentType = child.getText();
                } else {
                    // This identifier is the property name
                    properties.put(child.getText(), currentType);
                    currentType = null;
                }
            }

            // Reset on = (end of property definition)
            if (childType == KiteTokenTypes.ASSIGN) {
                currentType = null;
            }

            // Reset on newline (end of property definition)
            if (childType == KiteTokenTypes.NL || childType == KiteTokenTypes.NEWLINE) {
                currentType = null;
            }
        }
    }

    /**
     * Infer the type of a value expression.
     */
    @Nullable
    private String inferType(PsiElement value) {
        if (value == null || value.getNode() == null) return null;

        IElementType type = value.getNode().getElementType();

        // String literal (double-quoted with DQUOTE, single-quoted with SINGLE_STRING, or legacy STRING)
        if (type == KiteTokenTypes.STRING ||
            type == KiteTokenTypes.SINGLE_STRING ||
            type == KiteTokenTypes.DQUOTE ||
            type == KiteTokenTypes.STRING_TEXT) {
            return "string";
        }

        // Number literal
        if (type == KiteTokenTypes.NUMBER) {
            return "number";
        }

        // Boolean literal
        if (type == KiteTokenTypes.TRUE || type == KiteTokenTypes.FALSE) {
            return "boolean";
        }

        // Null literal
        if (type == KiteTokenTypes.NULL) {
            return "null";
        }

        // Object literal
        if (type == KiteElementTypes.OBJECT_LITERAL) {
            return "object";
        }

        // Array literal
        if (type == KiteElementTypes.ARRAY_LITERAL || type == KiteTokenTypes.LBRACK) {
            return "array";
        }

        // Check for string content (the actual text of a string)
        String text = value.getText();
        if (text.startsWith("\"") || text.startsWith("'")) {
            return "string";
        }

        // Check for number patterns
        if (text.matches("-?\\d+(\\.\\d+)?")) {
            return "number";
        }

        // Check text content
        if ("true".equals(text) || "false".equals(text)) {
            return "boolean";
        }
        if ("null".equals(text)) {
            return "null";
        }

        // For composite elements, check first significant child
        for (PsiElement child = value.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            if (isWhitespace(child.getNode().getElementType())) continue;

            String childType = inferType(child);
            if (childType != null) {
                return childType;
            }
        }

        return null;
    }

    /**
     * Check if the value type is compatible with the declared type.
     */
    private boolean isTypeCompatible(String declaredType, String valueType) {
        // Normalize types
        String normalizedDeclared = declaredType.toLowerCase();
        String normalizedValue = valueType.toLowerCase();

        // Exact match
        if (normalizedDeclared.equals(normalizedValue)) {
            return true;
        }

        // 'any' accepts everything
        if ("any".equals(normalizedDeclared)) {
            return true;
        }

        // 'object' accepts object literals
        if ("object".equals(normalizedDeclared) && "object".equals(normalizedValue)) {
            return true;
        }

        // null is compatible with any nullable type (we're lenient here)
        if ("null".equals(normalizedValue)) {
            return true;
        }

        // Custom type aliases (non-built-in types) - be lenient
        // If the declared type is not a built-in type, it's likely a type alias
        // (e.g., type Environment = "dev" | "staging" | "prod")
        // We can't fully resolve type aliases, so allow compatible primitive values
        if (!isBuiltinType(normalizedDeclared)) {
            // Custom types that look like they could be string unions (PascalCase names)
            // should accept string values
            if ("string".equals(normalizedValue)) {
                return true;
            }
            // Also accept numbers and booleans for custom types
            // (the user knows what they're doing with their type aliases)
            if ("number".equals(normalizedValue) || "boolean".equals(normalizedValue)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a type name is a built-in type.
     */
    private boolean isBuiltinType(String typeName) {
        return "string".equals(typeName) ||
               "number".equals(typeName) ||
               "boolean".equals(typeName) ||
               "any".equals(typeName) ||
               "object".equals(typeName) ||
               "void".equals(typeName) ||
               "null".equals(typeName) ||
               "array".equals(typeName);
    }

    /**
     * Check if the identifier is a declaration name (not a reference).
     */
    private boolean isDeclarationName(PsiElement identifier) {
        // Check what follows this identifier
        PsiElement next = skipWhitespace(identifier.getNextSibling());
        if (next == null) return false;

        IElementType nextType = next.getNode().getElementType();

        // Declaration patterns:
        // - identifier = value
        // - identifier { ... }
        // - identifier += value
        // - identifier : type (in object literals)
        if (nextType == KiteTokenTypes.ASSIGN ||
            nextType == KiteTokenTypes.LBRACE ||
            nextType == KiteTokenTypes.PLUS_ASSIGN ||
            nextType == KiteTokenTypes.COLON) {
            return true;
        }

        // Check if preceded by a keyword (input, output, var, resource, component, etc.)
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev != null) {
            IElementType prevType = prev.getNode().getElementType();
            if (prevType == KiteTokenTypes.INPUT ||
                prevType == KiteTokenTypes.OUTPUT ||
                prevType == KiteTokenTypes.VAR ||
                prevType == KiteTokenTypes.FUN ||
                prevType == KiteTokenTypes.TYPE ||
                prevType == KiteTokenTypes.FOR ||
                prevType == KiteTokenTypes.RESOURCE ||
                prevType == KiteTokenTypes.COMPONENT ||
                prevType == KiteTokenTypes.SCHEMA) {
                return true;
            }

            // Also check if prev is an identifier (could be type annotation before name)
            if (prevType == KiteTokenTypes.IDENTIFIER) {
                // Check if there's a keyword before the type
                PsiElement prevPrev = skipWhitespaceBackward(prev.getPrevSibling());
                if (prevPrev != null) {
                    IElementType prevPrevType = prevPrev.getNode().getElementType();
                    if (prevPrevType == KiteTokenTypes.INPUT ||
                        prevPrevType == KiteTokenTypes.OUTPUT ||
                        prevPrevType == KiteTokenTypes.VAR ||
                        prevPrevType == KiteTokenTypes.RESOURCE ||
                        prevPrevType == KiteTokenTypes.COMPONENT) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if the identifier is a property access (after a DOT).
     */
    private boolean isPropertyAccess(PsiElement identifier) {
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        return prev != null && prev.getNode().getElementType() == KiteTokenTypes.DOT;
    }

    /**
     * Check if the identifier is a decorator name (immediately after @).
     * Decorators are global/built-in and don't need to be declared.
     */
    private boolean isDecoratorName(PsiElement identifier) {
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        return prev != null && prev.getNode() != null &&
               prev.getNode().getElementType() == KiteTokenTypes.AT;
    }

    /**
     * Check if the identifier is a type annotation in a declaration.
     */
    private boolean isTypeAnnotation(PsiElement identifier) {
        // Type annotations appear after declaration keywords and before the name
        // Pattern: var string name = value
        //              ^^^^^^ type annotation

        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev == null) return false;

        IElementType prevType = prev.getNode().getElementType();

        // If preceded by declaration keyword, this could be a type annotation
        if (prevType == KiteTokenTypes.VAR ||
            prevType == KiteTokenTypes.INPUT ||
            prevType == KiteTokenTypes.OUTPUT) {
            // Check if there's another identifier after this (the name)
            PsiElement next = skipWhitespace(identifier.getNextSibling());
            if (next != null && next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                return true; // This is a type annotation
            }
        }

        return false;
    }

    /**
     * Find the for loop variable name.
     */
    @Nullable
    private String findForLoopVariable(PsiElement forStatement) {
        boolean foundFor = false;
        for (PsiElement child = forStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.FOR) {
                foundFor = true;
            } else if (foundFor && type == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            } else if (type == KiteTokenTypes.IN) {
                break;
            }
        }
        return null;
    }

    /**
     * Check if element type is a declaration.
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
    @Nullable
    private String findDeclarationName(PsiElement declaration, IElementType type) {
        // For component declarations, handle both definitions and instances
        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            return findComponentName(declaration);
        }

        // Special handling for function declarations
        // Pattern: fun name(params) returnType { ... }
        // The function name is the identifier after FUN and before LPAREN
        if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            boolean foundFun = false;
            for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNode() == null) continue;
                IElementType childType = child.getNode().getElementType();

                if (childType == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText(); // First identifier after FUN is the function name
                } else if (childType == KiteTokenTypes.LPAREN) {
                    break; // Stop at opening paren
                }
            }
            return null;
        }

        // Find the identifier before = or {
        PsiElement lastIdentifier = null;
        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier.getText();
                }
            }
        }
        return lastIdentifier != null ? lastIdentifier.getText() : null;
    }

    /**
     * Find the name of a component (handles both declarations and instantiations).
     */
    @Nullable
    private String findComponentName(PsiElement componentDecl) {
        List<String> identifiers = new ArrayList<>();

        for (PsiElement child = componentDecl.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.IDENTIFIER) {
                identifiers.add(child.getText());
            } else if (type == KiteTokenTypes.LBRACE) {
                break;
            }
        }

        if (identifiers.isEmpty()) {
            return null;
        }

        // Return the last identifier (instance name for instantiations, type name for definitions)
        return identifiers.get(identifiers.size() - 1);
    }

    /**
     * Skip whitespace tokens forward.
     */
    @Nullable
    private PsiElement skipWhitespace(@Nullable PsiElement element) {
        while (element != null && element.getNode() != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    /**
     * Skip whitespace tokens backward.
     */
    @Nullable
    private PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && element.getNode() != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    /**
     * Check if element type is whitespace.
     */
    private boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Check if the identifier is a property definition inside a schema, resource, or component body.
     * Pattern: type propertyName (e.g., "string host" inside a schema body)
     */
    private boolean isPropertyDefinition(PsiElement identifier) {
        // Check if inside a schema, resource, or component body
        if (!isInsideDeclarationBody(identifier)) {
            return false;
        }

        // Property definition pattern: type name
        // The identifier is a property name if it's preceded by a type identifier
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev == null || prev.getNode() == null) {
            return false;
        }

        IElementType prevType = prev.getNode().getElementType();

        // If preceded by 'any' keyword, this is a property name (any is a type)
        if (prevType == KiteTokenTypes.ANY) {
            return true;
        }

        // If preceded by a builtin type or another identifier (custom type), this is a property name
        if (prevType == KiteTokenTypes.IDENTIFIER) {
            String prevText = prev.getText();
            // Check if the previous identifier is a type (builtin types or custom types)
            // For now, assume any identifier before this one in a schema body is a type
            if (BUILTIN_TYPES.contains(prevText) || Character.isUpperCase(prevText.charAt(0))) {
                return true;
            }
            // Additional check: if the previous identifier is followed by this one (type name pattern)
            // and they're both at the start of a line or after a newline, it's a property definition
            PsiElement prevPrev = skipWhitespaceBackward(prev.getPrevSibling());
            if (prevPrev == null) {
                return true; // At the start of the body
            }
            IElementType prevPrevType = prevPrev.getNode().getElementType();
            if (prevPrevType == KiteTokenTypes.NL || prevPrevType == KiteTokenTypes.LBRACE) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the element is inside a schema, resource, or component body (between { and }).
     */
    private boolean isInsideDeclarationBody(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent.getNode() != null) {
                IElementType parentType = parent.getNode().getElementType();
                if (parentType == KiteElementTypes.SCHEMA_DECLARATION ||
                    parentType == KiteElementTypes.RESOURCE_DECLARATION ||
                    parentType == KiteElementTypes.COMPONENT_DECLARATION) {
                    // Check if element is after LBRACE (inside the body)
                    boolean afterLbrace = false;
                    for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.LBRACE) {
                            afterLbrace = true;
                        }
                        if (afterLbrace && isDescendantOf(element, child)) {
                            return true;
                        }
                        if (child == element) {
                            return afterLbrace;
                        }
                    }
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Check if element is a descendant of ancestor.
     */
    private boolean isDescendantOf(PsiElement element, PsiElement ancestor) {
        PsiElement current = element;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
