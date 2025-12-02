package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Helper class for type inference and type compatibility checking.
 * <p>
 * This class provides utilities for:
 * <ul>
 *   <li>Inferring the type of value expressions (strings, numbers, booleans, etc.)</li>
 *   <li>Checking type compatibility between declared and actual types</li>
 *   <li>Identifying built-in type names</li>
 * </ul>
 * <p>
 * <b>Type Inference Strategy:</b>
 * <ol>
 *   <li>First checks the PSI element type (STRING, NUMBER, TRUE/FALSE, etc.)</li>
 *   <li>Falls back to text-based detection for composite elements</li>
 *   <li>Recursively checks children for composite elements</li>
 * </ol>
 * <p>
 * <b>Type Compatibility Rules:</b>
 * <ul>
 *   <li>{@code any} accepts all types</li>
 *   <li>Array types (e.g., {@code string[]}) accept array values</li>
 *   <li>{@code null} is compatible with any type (lenient nullability)</li>
 *   <li>Custom type aliases accept primitive values (can't resolve alias definitions)</li>
 * </ul>
 * <p>
 * <b>String Handling:</b><br>
 * Kite supports multiple string syntaxes:
 * <ul>
 *   <li>Double-quoted: {@code "hello"} - tokenized as DQUOTE + STRING_TEXT + STRING_DQUOTE</li>
 *   <li>Single-quoted: {@code 'hello'} - tokenized as SINGLE_STRING</li>
 *   <li>Legacy STRING token for simple strings</li>
 * </ul>
 *
 * @see KiteIdentifierContextHelper for identifier context detection
 * @see KiteImportValidationHelper for import-related utilities
 */
public final class KiteTypeInferenceHelper {

    /**
     * Set of built-in type names that are recognized by the Kite language.
     * Includes both lowercase and PascalCase variants for flexibility.
     * <p>
     * Note: The lowercase versions are the canonical form used in Kite code.
     * PascalCase versions are included for compatibility with type annotations
     * that might use capitalized forms.
     */
    public static final Set<String> BUILTIN_TYPES = Set.of(
            "string", "number", "boolean", "any", "object", "void",
            "String", "Number", "Boolean", "Any", "Object", "Void"
    );

    private KiteTypeInferenceHelper() {
        // Utility class
    }

    /**
     * Infer the type of a value expression.
     *
     * @param value The PSI element representing the value
     * @return The inferred type string, or null if type cannot be inferred
     */
    @Nullable
    public static String inferType(PsiElement value) {
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
            if (KitePsiUtil.isWhitespace(child.getNode().getElementType())) continue;

            String childType = inferType(child);
            if (childType != null) {
                return childType;
            }
        }

        return null;
    }

    /**
     * Check if the value type is compatible with the declared type.
     *
     * @param declaredType The declared type
     * @param valueType    The actual value type
     * @return true if types are compatible
     */
    public static boolean isTypeCompatible(String declaredType, String valueType) {
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

        // Array types (e.g., string[], number[]) accept array values
        if (normalizedDeclared.endsWith("[]") && "array".equals(normalizedValue)) {
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
            return "number".equals(normalizedValue) || "boolean".equals(normalizedValue);
        }

        return false;
    }

    /**
     * Check if a type name is a built-in type.
     *
     * @param typeName The type name to check
     * @return true if it's a built-in type
     */
    public static boolean isBuiltinType(String typeName) {
        return "string".equals(typeName) ||
               "number".equals(typeName) ||
               "boolean".equals(typeName) ||
               "any".equals(typeName) ||
               "object".equals(typeName) ||
               "void".equals(typeName) ||
               "null".equals(typeName) ||
               "array".equals(typeName);
    }
}
