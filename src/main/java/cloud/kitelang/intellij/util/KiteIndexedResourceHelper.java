package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for indexed resource operations.
 * Handles detection of @count decorator, for-loop context,
 * and validation of indexed access patterns like server[0] or server["dev"].
 */
public final class KiteIndexedResourceHelper {

    private KiteIndexedResourceHelper() {
        // Utility class - no instances
    }

    /**
     * Index type for indexed resources.
     */
    public enum IndexType {
        NUMERIC,  // @count(n) or for i in 0..n
        STRING    // for x in ["a", "b"]
    }

    /**
     * Information about an indexed resource or component.
     */
    public record IndexedResourceInfo(
            IndexType indexType,
            @Nullable Integer countValue,
            @Nullable Integer rangeStart,
            @Nullable Integer rangeEnd,
            @Nullable List<String> stringKeys,
            PsiElement declaration
    ) {
        /**
         * Check if a numeric index is valid for this indexed resource.
         */
        public boolean isValidNumericIndex(int index) {
            if (indexType != IndexType.NUMERIC) {
                return false;
            }
            if (countValue != null) {
                return index >= 0 && index < countValue;
            }
            if (rangeStart != null && rangeEnd != null) {
                return index >= rangeStart && index < rangeEnd;
            }
            return false;
        }

        /**
         * Check if a string key is valid for this indexed resource.
         */
        public boolean isValidStringKey(String key) {
            if (indexType != IndexType.STRING) {
                return false;
            }
            return stringKeys != null && stringKeys.contains(key);
        }

        /**
         * Get all valid indices as strings for completion.
         * Returns numeric indices as strings (0, 1, 2) or quoted strings ("dev", "prod").
         */
        public List<String> getValidIndices() {
            var result = new ArrayList<String>();
            if (indexType == IndexType.NUMERIC) {
                int start = rangeStart != null ? rangeStart : 0;
                int end = countValue != null ? countValue : (rangeEnd != null ? rangeEnd : start);
                for (int i = start; i < end; i++) {
                    result.add(String.valueOf(i));
                }
            } else if (indexType == IndexType.STRING && stringKeys != null) {
                for (var key : stringKeys) {
                    result.add("\"" + key + "\"");
                }
            }
            return result;
        }

        /**
         * Validate a numeric index and return a result.
         */
        public ValidationResult validateNumericIndex(int index) {
            if (indexType != IndexType.NUMERIC) {
                return ValidationResult.error(
                        "'" + getDeclarationName() + "' uses string keys (e.g., " +
                                getDeclarationName() + "[\"" + (stringKeys != null && !stringKeys.isEmpty() ? stringKeys.get(0) : "key") + "\"]), not numeric indices.");
            }
            if (!isValidNumericIndex(index)) {
                int maxIndex = countValue != null ? countValue - 1 : (rangeEnd != null ? rangeEnd - 1 : 0);
                int minIndex = rangeStart != null ? rangeStart : 0;
                return ValidationResult.error(
                        "Index " + index + " is out of bounds. '" + getDeclarationName() +
                                "' has valid indices " + minIndex + "-" + maxIndex + ".");
            }
            return ValidationResult.valid();
        }

        /**
         * Validate a string key and return a result.
         */
        public ValidationResult validateStringKey(String key) {
            if (indexType != IndexType.STRING) {
                return ValidationResult.error(
                        "'" + getDeclarationName() + "' uses numeric indices (e.g., " +
                                getDeclarationName() + "[0]), not string keys.");
            }
            if (!isValidStringKey(key)) {
                var keysStr = stringKeys != null ? String.join(", ", stringKeys.stream().map(k -> "\"" + k + "\"").toList()) : "";
                return ValidationResult.error(
                        "Key \"" + key + "\" is not valid. '" + getDeclarationName() +
                                "' accepts: " + keysStr + ".");
            }
            return ValidationResult.valid();
        }

        private String getDeclarationName() {
            if (declaration.getNode() == null) {
                return "resource";
            }
            var type = declaration.getNode().getElementType();
            var name = KiteDeclarationHelper.findNameInDeclaration(declaration, type);
            return name != null ? name : "resource";
        }
    }

    /**
     * Result of validating an indexed access.
     */
    public record ValidationResult(boolean isValid, @Nullable String errorMessage) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * Information about an indexed access expression like server[0] or server["dev"].
     */
    public record IndexedAccessInfo(
            String baseName,
            PsiElement baseElement,
            PsiElement indexExpression,
            @Nullable Integer numericValue,
            @Nullable String stringValue
    ) {
        /**
         * Check if this is a variable index (not a literal).
         */
        public boolean isVariableIndex() {
            return numericValue == null && stringValue == null;
        }
    }

    /**
     * Get indexing info for a declaration.
     * Checks for @count decorator or enclosing for-loop.
     *
     * @param declaration The resource or component declaration
     * @return IndexedResourceInfo if the declaration is indexed, null otherwise
     */
    @Nullable
    public static IndexedResourceInfo getIndexedInfo(PsiElement declaration) {
        if (declaration == null || declaration.getNode() == null) {
            return null;
        }

        var elementType = declaration.getNode().getElementType();
        if (elementType != KiteElementTypes.RESOURCE_DECLARATION &&
                elementType != KiteElementTypes.COMPONENT_DECLARATION) {
            return null;
        }

        // Check for @count decorator first (takes precedence)
        var countValue = extractCountValue(declaration);
        if (countValue != null) {
            return new IndexedResourceInfo(IndexType.NUMERIC, countValue, null, null, null, declaration);
        }

        // Check for enclosing for-loop
        var forLoopInfo = findEnclosingForLoop(declaration);
        if (forLoopInfo != null) {
            return forLoopInfo;
        }

        return null;
    }

    /**
     * Extract the @count value from a declaration's decorators.
     *
     * @param declaration The declaration to check
     * @return The count value, or null if no @count decorator
     */
    @Nullable
    public static Integer extractCountValue(PsiElement declaration) {
        // Look backwards from the declaration for decorators
        var prev = skipWhitespaceAndNewlinesBackward(declaration.getPrevSibling());

        while (prev != null) {
            if (prev.getNode() != null) {
                var type = prev.getNode().getElementType();

                // Found a decorator: @ followed by identifier
                if (type == KiteTokenTypes.AT) {
                    var decoratorName = skipWhitespace(prev.getNextSibling());
                    if (decoratorName != null && "count".equals(decoratorName.getText())) {
                        // Look for ( number )
                        var lparen = skipWhitespace(decoratorName.getNextSibling());
                        if (lparen != null && lparen.getNode() != null &&
                                lparen.getNode().getElementType() == KiteTokenTypes.LPAREN) {
                            var numElement = skipWhitespace(lparen.getNextSibling());
                            if (numElement != null && numElement.getNode() != null &&
                                    numElement.getNode().getElementType() == KiteTokenTypes.NUMBER) {
                                try {
                                    return Integer.parseInt(numElement.getText());
                                } catch (NumberFormatException e) {
                                    return null;
                                }
                            }
                        }
                    }
                }

                // Stop if we hit a non-decorator element (like another declaration)
                if (type == KiteTokenTypes.RBRACE || type == KiteTokenTypes.SEMICOLON) {
                    break;
                }
            }

            prev = skipWhitespaceAndNewlinesBackward(prev.getPrevSibling());
        }

        return null;
    }

    /**
     * Find the enclosing for-loop for a declaration and extract its iteration info.
     *
     * @param declaration The declaration to check
     * @return IndexedResourceInfo if inside a for-loop, null otherwise
     */
    @Nullable
    public static IndexedResourceInfo findEnclosingForLoop(PsiElement declaration) {
        var parent = declaration.getParent();
        while (parent != null) {
            if (parent.getNode() != null &&
                    parent.getNode().getElementType() == KiteElementTypes.FOR_STATEMENT) {
                return parseForLoop(parent, declaration);
            }
            parent = parent.getParent();
        }
        return null;
    }

    /**
     * Parse a for-loop statement to extract iteration info.
     */
    @Nullable
    private static IndexedResourceInfo parseForLoop(PsiElement forStatement, PsiElement declaration) {
        // Pattern: for identifier in (rangeExpression | arrayExpression | identifier)
        var child = forStatement.getFirstChild();
        boolean foundFor = false;
        boolean foundIn = false;

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                // Skip whitespace
                if (type == KiteTokenTypes.WHITESPACE || type == TokenType.WHITE_SPACE ||
                        type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    child = child.getNextSibling();
                    continue;
                }

                if (type == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && type == KiteTokenTypes.IN) {
                    foundIn = true;
                } else if (foundIn) {
                    // Look at the iteration source
                    if (type == KiteTokenTypes.NUMBER) {
                        // Start of range expression: NUMBER .. NUMBER
                        return parseRangeExpression(child, declaration);
                    } else if (type == KiteElementTypes.ARRAY_LITERAL) {
                        // Array literal element
                        return parseArrayLiteral(child, declaration);
                    } else if (type == KiteTokenTypes.LBRACK) {
                        // Array literal starting with [
                        return parseArrayLiteralFromBracket(child, declaration);
                    }
                }
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Parse an array literal starting from [ token.
     */
    @Nullable
    private static IndexedResourceInfo parseArrayLiteralFromBracket(PsiElement lbrack, PsiElement declaration) {
        var keys = new ArrayList<String>();

        var child = lbrack.getNextSibling();
        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.RBRACK) {
                    break;
                }

                // Look for string literals
                if (type == KiteTokenTypes.SINGLE_STRING) {
                    var text = child.getText();
                    // Remove quotes
                    if (text.length() >= 2) {
                        keys.add(text.substring(1, text.length() - 1));
                    }
                } else if (type == KiteTokenTypes.DQUOTE) {
                    // Interpolated string - find the text content
                    var next = child.getNextSibling();
                    if (next != null && next.getNode() != null &&
                            next.getNode().getElementType() == KiteTokenTypes.STRING_TEXT) {
                        keys.add(next.getText());
                    }
                }
            }
            child = child.getNextSibling();
        }

        if (!keys.isEmpty()) {
            return new IndexedResourceInfo(IndexType.STRING, null, null, null, keys, declaration);
        }
        return null;
    }

    /**
     * Parse a range expression (NUMBER .. NUMBER).
     */
    @Nullable
    private static IndexedResourceInfo parseRangeExpression(PsiElement startNumber, PsiElement declaration) {
        try {
            int start = Integer.parseInt(startNumber.getText());

            // Look for .. and end number (skip whitespace and newlines)
            var next = skipWhitespaceAndNewlines(startNumber.getNextSibling());
            if (next != null && next.getNode() != null &&
                    next.getNode().getElementType() == KiteTokenTypes.RANGE) {
                var endElement = skipWhitespaceAndNewlines(next.getNextSibling());
                if (endElement != null && endElement.getNode() != null &&
                        endElement.getNode().getElementType() == KiteTokenTypes.NUMBER) {
                    int end = Integer.parseInt(endElement.getText());
                    return new IndexedResourceInfo(IndexType.NUMERIC, null, start, end, null, declaration);
                }
            }
        } catch (NumberFormatException e) {
            // Ignore parse errors
        }
        return null;
    }

    /**
     * Skip whitespace and newlines forward.
     */
    @Nullable
    private static PsiElement skipWhitespaceAndNewlines(@Nullable PsiElement element) {
        while (element != null) {
            if (element.getNode() != null) {
                var type = element.getNode().getElementType();
                if (type != KiteTokenTypes.WHITESPACE &&
                        type != TokenType.WHITE_SPACE &&
                        type != KiteTokenTypes.NL &&
                        type != KiteTokenTypes.NEWLINE) {
                    return element;
                }
            }
            element = element.getNextSibling();
        }
        return null;
    }

    /**
     * Parse an array literal to extract string keys.
     */
    @Nullable
    private static IndexedResourceInfo parseArrayLiteral(PsiElement arrayElement, PsiElement declaration) {
        var keys = new ArrayList<String>();

        // Find the contents between [ and ]
        var child = arrayElement.getFirstChild();
        if (child == null) {
            child = arrayElement.getNextSibling();
        }

        while (child != null) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();

                if (type == KiteTokenTypes.RBRACK) {
                    break;
                }

                // Look for string literals
                if (type == KiteTokenTypes.SINGLE_STRING) {
                    var text = child.getText();
                    // Remove quotes
                    if (text.length() >= 2) {
                        keys.add(text.substring(1, text.length() - 1));
                    }
                } else if (type == KiteTokenTypes.DQUOTE) {
                    // Interpolated string - find the text content
                    var next = child.getNextSibling();
                    if (next != null && next.getNode() != null &&
                            next.getNode().getElementType() == KiteTokenTypes.STRING_TEXT) {
                        keys.add(next.getText());
                    }
                }
            }
            child = child.getNextSibling();
        }

        if (!keys.isEmpty()) {
            return new IndexedResourceInfo(IndexType.STRING, null, null, null, keys, declaration);
        }
        return null;
    }

    /**
     * Parse an indexed access expression starting from the LBRACK token.
     *
     * @param lbrack The [ token
     * @return IndexedAccessInfo if this is an indexed access, null otherwise
     */
    @Nullable
    public static IndexedAccessInfo parseIndexedAccess(PsiElement lbrack) {
        if (lbrack == null || lbrack.getNode() == null ||
                lbrack.getNode().getElementType() != KiteTokenTypes.LBRACK) {
            return null;
        }

        // Find the base identifier before [
        var prev = skipWhitespace(lbrack.getPrevSibling());
        if (prev == null || prev.getNode() == null) {
            return null;
        }

        // Check if this is identifier[ pattern (not array type like string[])
        if (prev.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        var baseName = prev.getText();
        var baseElement = prev;

        // Find the index expression between [ and ]
        var indexElement = skipWhitespace(lbrack.getNextSibling());
        if (indexElement == null) {
            return null;
        }

        Integer numericValue = null;
        String stringValue = null;

        if (indexElement.getNode() != null) {
            var indexType = indexElement.getNode().getElementType();

            if (indexType == KiteTokenTypes.NUMBER) {
                try {
                    numericValue = Integer.parseInt(indexElement.getText());
                } catch (NumberFormatException e) {
                    // Variable or expression
                }
            } else if (indexType == KiteTokenTypes.SINGLE_STRING) {
                var text = indexElement.getText();
                if (text.length() >= 2) {
                    stringValue = text.substring(1, text.length() - 1);
                }
            } else if (indexType == KiteTokenTypes.DQUOTE) {
                // Interpolated string
                var next = indexElement.getNextSibling();
                if (next != null && next.getNode() != null &&
                        next.getNode().getElementType() == KiteTokenTypes.STRING_TEXT) {
                    stringValue = next.getText();
                }
            }
            // Otherwise it's a variable or expression
        }

        return new IndexedAccessInfo(baseName, baseElement, indexElement, numericValue, stringValue);
    }

    /**
     * Skip whitespace forward.
     */
    @Nullable
    private static PsiElement skipWhitespace(@Nullable PsiElement element) {
        while (element != null) {
            if (element.getNode() != null) {
                var type = element.getNode().getElementType();
                if (type != KiteTokenTypes.WHITESPACE &&
                        type != TokenType.WHITE_SPACE) {
                    return element;
                }
            }
            element = element.getNextSibling();
        }
        return null;
    }

    /**
     * Skip whitespace and newlines backward.
     */
    @Nullable
    private static PsiElement skipWhitespaceAndNewlinesBackward(@Nullable PsiElement element) {
        while (element != null) {
            if (element.getNode() != null) {
                var type = element.getNode().getElementType();
                if (type != KiteTokenTypes.WHITESPACE &&
                        type != TokenType.WHITE_SPACE &&
                        type != KiteTokenTypes.NL &&
                        type != KiteTokenTypes.NEWLINE) {
                    return element;
                }
            }
            element = element.getPrevSibling();
        }
        return null;
    }
}
