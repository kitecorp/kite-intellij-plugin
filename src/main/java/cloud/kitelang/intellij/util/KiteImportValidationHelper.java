package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import static cloud.kitelang.intellij.util.KitePsiUtil.isWhitespace;

/**
 * Helper class for import validation operations.
 * <p>
 * This class provides utilities for parsing and validating Kite import statements.
 * Kite supports two import syntaxes:
 * <ul>
 *   <li>Wildcard: {@code import * from "file.kite"}</li>
 *   <li>Named: {@code import Symbol from "file.kite"} or {@code import A, B, C from "file.kite"}</li>
 * </ul>
 * <p>
 * <b>Import Path Resolution:</b><br>
 * Import paths are resolved in the following order (see {@code KiteImportHelper.resolveFilePath()}):
 * <ol>
 *   <li>Relative to containing file: {@code "common.kite"}</li>
 *   <li>Project base path</li>
 *   <li>Project-local providers: {@code .kite/providers/}</li>
 *   <li>User-global providers: {@code ~/.kite/providers/}</li>
 *   <li>Package-style paths: {@code "aws.DatabaseConfig"} -> {@code aws/DatabaseConfig.kite}</li>
 * </ol>
 * <p>
 * <b>PSI Structure:</b><br>
 * Import statements may be wrapped in an {@code IMPORT_STATEMENT} element, or the tokens
 * may appear directly as children of the file. This class handles both cases:
 * <ul>
 *   <li>{@link #isWildcardImport} - Check IMPORT_STATEMENT element</li>
 *   <li>{@link #isWildcardImportFromToken} - Check starting from raw IMPORT token</li>
 * </ul>
 * <p>
 * <b>String Parsing:</b><br>
 * Import paths can be double-quoted or single-quoted. For interpolated strings,
 * the structure is: {@code DQUOTE + STRING_TEXT + STRING_DQUOTE}.
 * Use {@link #collectInterpolatedStringText} to extract content from interpolated strings.
 * <p>
 * <b>Validation:</b><br>
 * The {@link #isNonImportStatement} method identifies declaration types that should
 * come after imports. This is used by annotators to warn about misplaced imports.
 *
 * @see cloud.kitelang.intellij.reference.KiteImportHelper for import resolution
 * @see KiteIdentifierContextHelper#isInsideImportStatement for context detection
 */
public final class KiteImportValidationHelper {

    private KiteImportValidationHelper() {
        // Utility class
    }

    /**
     * Check if an import statement is a wildcard import (import * from "file").
     *
     * @param importStatement The import statement element
     * @return true if this is a wildcard import
     */
    public static boolean isWildcardImport(PsiElement importStatement) {
        boolean foundImport = false;
        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IMPORT) {
                foundImport = true;
            } else if (foundImport && childType == KiteTokenTypes.MULTIPLY) {
                return true; // Found wildcard
            } else if (childType == KiteTokenTypes.FROM) {
                break; // Reached FROM without finding *, not wildcard
            }
        }
        return false;
    }

    /**
     * Check if an import starting from a raw IMPORT token is a wildcard import.
     *
     * @param importToken The IMPORT token element
     * @return true if this is a wildcard import
     */
    public static boolean isWildcardImportFromToken(PsiElement importToken) {
        PsiElement sibling = importToken.getNextSibling();
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

            // Found wildcard
            if (siblingType == KiteTokenTypes.MULTIPLY) {
                return true;
            }

            // Found identifier before * means it's a named import
            if (siblingType == KiteTokenTypes.IDENTIFIER) {
                return false;
            }

            // Stop at FROM
            if (siblingType == KiteTokenTypes.FROM) {
                break;
            }

            sibling = sibling.getNextSibling();
        }
        return false;
    }

    /**
     * Extract import path from a raw IMPORT token by scanning forward to find the string.
     *
     * @param importToken The IMPORT token element
     * @return The import path string, or null if not found
     */
    @Nullable
    public static String extractImportPathFromToken(PsiElement importToken) {
        boolean foundFrom = false;
        PsiElement sibling = importToken.getNextSibling();

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

            // Look for "from" keyword
            if (siblingType == KiteTokenTypes.FROM) {
                foundFrom = true;
                sibling = sibling.getNextSibling();
                continue;
            }

            // After FROM, look for the string literal
            if (foundFrom) {
                String text = sibling.getText();

                // Check for quoted string
                if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
                    return text.substring(1, text.length() - 1);
                }
                if (text.startsWith("'") && text.endsWith("'") && text.length() >= 2) {
                    return text.substring(1, text.length() - 1);
                }

                // Check for string token types
                if (siblingType == KiteTokenTypes.STRING ||
                    siblingType == KiteTokenTypes.SINGLE_STRING) {
                    if (text.length() >= 2) {
                        return text.substring(1, text.length() - 1);
                    }
                }

                // For DQUOTE, look for STRING_TEXT in siblings
                if (siblingType == KiteTokenTypes.DQUOTE) {
                    PsiElement next = sibling.getNextSibling();
                    while (next != null) {
                        if (next.getNode() != null) {
                            IElementType nextType = next.getNode().getElementType();
                            if (nextType == KiteTokenTypes.STRING_TEXT) {
                                return next.getText();
                            }
                            if (nextType == KiteTokenTypes.STRING_DQUOTE) {
                                break; // End of string
                            }
                        }
                        next = next.getNextSibling();
                    }
                }
            }

            // Stop at newline
            if (siblingType == KiteTokenTypes.NL || siblingType == KiteTokenTypes.NEWLINE) {
                break;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    /**
     * Find the string element containing the import path in an import statement.
     * Pattern: import ... from "path"
     *
     * @param importStatement The import statement element
     * @return The element containing the path string, or null if not found
     */
    @Nullable
    public static PsiElement findImportPathString(PsiElement importStatement) {
        boolean foundFrom = false;
        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.FROM) {
                foundFrom = true;
                continue;
            }

            if (foundFrom) {
                // Look for any string token type
                if (childType == KiteTokenTypes.STRING ||
                    childType == KiteTokenTypes.SINGLE_STRING) {
                    return child;
                }
                // For DQUOTE, we need to find the full interpolated string
                // First check if parent is a stringLiteral/interpolatedString element
                if (childType == KiteTokenTypes.DQUOTE) {
                    PsiElement parent = child.getParent();
                    // If parent is NOT the import statement, it might be the string element we want
                    if (parent != null && parent != importStatement) {
                        String parentText = parent.getText();
                        if (parentText.startsWith("\"") && parentText.endsWith("\"")) {
                            return parent;
                        }
                    }
                    // Otherwise, return DQUOTE itself - we'll handle text collection specially
                    return child;
                }
                // If we find a composite element after FROM, check if it's a string element
                String text = child.getText();
                if (text.startsWith("\"") || text.startsWith("'")) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * Find the import path string starting from a raw IMPORT token.
     *
     * @param importToken The IMPORT token element
     * @return The element containing the path string, or null if not found
     */
    @Nullable
    public static PsiElement findImportPathStringFromToken(PsiElement importToken) {
        boolean foundFrom = false;
        PsiElement sibling = importToken.getNextSibling();
        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }
            IElementType siblingType = sibling.getNode().getElementType();

            if (siblingType == KiteTokenTypes.FROM) {
                foundFrom = true;
                sibling = sibling.getNextSibling();
                continue;
            }

            if (foundFrom) {
                if (siblingType == KiteTokenTypes.STRING ||
                    siblingType == KiteTokenTypes.SINGLE_STRING) {
                    return sibling;
                }
                // For DQUOTE, get the parent element to get the full string
                if (siblingType == KiteTokenTypes.DQUOTE) {
                    PsiElement parent = sibling.getParent();
                    if (parent != null) {
                        return parent;
                    }
                    return sibling;
                }
                // Check if element text looks like a string
                String text = sibling.getText();
                if (text.startsWith("\"") || text.startsWith("'")) {
                    return sibling;
                }
            }

            sibling = sibling.getNextSibling();
        }
        return null;
    }

    /**
     * Extract the import path from a string element.
     * Handles both regular string elements and DQUOTE tokens that need sibling collection.
     *
     * @param stringElement The string element
     * @return The extracted path, or null if extraction failed
     */
    @Nullable
    public static String extractImportPathFromElement(PsiElement stringElement) {
        if (stringElement == null) return null;

        // Check if this is a DQUOTE token - need to collect text from DQUOTE to STRING_DQUOTE
        if (stringElement.getNode() != null &&
            stringElement.getNode().getElementType() == KiteTokenTypes.DQUOTE) {
            return collectInterpolatedStringText(stringElement);
        }

        // Otherwise, just extract from the element's text
        return extractStringContent(stringElement.getText());
    }

    /**
     * Collect the content of an interpolated string starting from DQUOTE.
     * Walks siblings to find STRING_TEXT (content) and STRING_DQUOTE (closing).
     *
     * @param dquote The DQUOTE element
     * @return The collected string content
     */
    @Nullable
    public static String collectInterpolatedStringText(PsiElement dquote) {
        StringBuilder content = new StringBuilder();
        PsiElement sibling = dquote.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType type = sibling.getNode().getElementType();

            // End of string found
            if (type == KiteTokenTypes.STRING_DQUOTE) {
                return content.toString();
            }

            // Collect content
            if (type == KiteTokenTypes.STRING_TEXT ||
                type == KiteTokenTypes.STRING_ESCAPE ||
                type == KiteTokenTypes.STRING_DOLLAR) {
                content.append(sibling.getText());
            }

            sibling = sibling.getNextSibling();
        }

        // If we didn't find STRING_DQUOTE, return empty string (empty string case: "")
        return content.toString();
    }

    /**
     * Extract the content from a string token, removing quotes.
     * Handles both regular strings and empty strings.
     *
     * @param stringToken The string token text
     * @return The extracted content without quotes, or null if invalid
     */
    @Nullable
    public static String extractStringContent(String stringToken) {
        if (stringToken == null) {
            return null;
        }
        // Handle empty strings (just "") - length is 2
        if (stringToken.equals("\"\"") || stringToken.equals("''")) {
            return "";
        }
        // Need at least 2 chars for quotes
        if (stringToken.length() < 2) {
            return null;
        }
        // Remove surrounding quotes
        if ((stringToken.startsWith("\"") && stringToken.endsWith("\"")) ||
            (stringToken.startsWith("'") && stringToken.endsWith("'"))) {
            return stringToken.substring(1, stringToken.length() - 1);
        }
        return stringToken;
    }

    /**
     * Find the end of an import statement starting from a raw IMPORT token.
     * Returns the last element before the newline or the string element.
     *
     * @param importToken The IMPORT token element
     * @return The last element of the import statement
     */
    @Nullable
    public static PsiElement findImportStatementEnd(PsiElement importToken) {
        PsiElement lastElement = importToken;
        PsiElement sibling = importToken.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType siblingType = sibling.getNode().getElementType();

            // Stop at newline
            if (siblingType == KiteTokenTypes.NL || siblingType == KiteTokenTypes.NEWLINE) {
                break;
            }

            lastElement = sibling;
            sibling = sibling.getNextSibling();
        }

        return lastElement;
    }

    /**
     * Check if the element type represents a non-import statement.
     * These are declarations and statements that should come after imports.
     *
     * @param type The element type to check
     * @return true if this is a non-import statement type
     */
    public static boolean isNonImportStatement(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT ||
               // Raw keyword tokens at file level (when not wrapped in elements)
               type == KiteTokenTypes.VAR ||
               type == KiteTokenTypes.INPUT ||
               type == KiteTokenTypes.OUTPUT ||
               type == KiteTokenTypes.RESOURCE ||
               type == KiteTokenTypes.COMPONENT ||
               type == KiteTokenTypes.SCHEMA ||
               type == KiteTokenTypes.FUN ||
               type == KiteTokenTypes.TYPE ||
               type == KiteTokenTypes.FOR ||
               type == KiteTokenTypes.IF;
    }
}
