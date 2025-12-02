package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

import static cloud.kitelang.intellij.util.KitePsiUtil.*;
import static cloud.kitelang.intellij.util.KiteTypeInferenceHelper.BUILTIN_TYPES;

/**
 * Helper class for determining the context of identifiers in Kite code.
 * Used to distinguish between declarations, references, property accesses, etc.
 * <p>
 * <b>Why Context Matters:</b><br>
 * The same identifier text can have different meanings depending on context:
 * <ul>
 *   <li>Declaration name: {@code var name = "hello"} - 'name' is being defined</li>
 *   <li>Reference: {@code var x = name} - 'name' refers to an existing declaration</li>
 *   <li>Property access: {@code config.host} - 'host' is a property, not a top-level reference</li>
 *   <li>Decorator: {@code @cloud} - 'cloud' is a built-in decorator name</li>
 *   <li>Type annotation: {@code var string name} - 'string' is a type, not a variable</li>
 *   <li>Property definition: Inside schema body, {@code string host} - 'host' is being defined</li>
 * </ul>
 * <p>
 * <b>Declaration Patterns:</b><br>
 * An identifier is a declaration name if:
 * <ul>
 *   <li>Followed by {@code =} (assignment): {@code name = "value"}</li>
 *   <li>Followed by {@code {}} (block): {@code schema Config {}}</li>
 *   <li>Followed by {@code +=} (append): {@code tags += "new"}</li>
 *   <li>Followed by {@code :} (object property): {@code host: "localhost"}</li>
 *   <li>Preceded by declaration keyword: {@code var}, {@code input}, {@code output}, etc.</li>
 * </ul>
 * <p>
 * <b>Schema Property Definition Pattern:</b><br>
 * Inside schema, resource, or component bodies, the pattern {@code type propertyName} defines
 * a property. This includes array types: {@code string[] tags}.
 * <p>
 * <b>PSI Structure for Array Types:</b>
 * <pre>
 * IDENTIFIER("string") -> ARRAY_LITERAL([]) -> IDENTIFIER("tags")
 * </pre>
 * The {@code []} is wrapped in an ARRAY_LITERAL element, not as separate tokens.
 *
 * @see KitePsiUtil for low-level PSI navigation
 * @see KiteTypeInferenceHelper for type checking
 */
public final class KiteIdentifierContextHelper {

    private KiteIdentifierContextHelper() {
        // Utility class
    }

    /**
     * Check if the identifier is a declaration name (not a reference).
     * Declaration patterns:
     * - identifier = value
     * - identifier { ... }
     * - identifier += value
     * - identifier : type (in object literals)
     *
     * @param identifier The identifier element to check
     * @return true if the identifier is a declaration name
     */
    public static boolean isDeclarationName(PsiElement identifier) {
        // Check what follows this identifier
        PsiElement next = skipWhitespace(identifier.getNextSibling());
        if (next == null || next.getNode() == null) return false;

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
        if (prev != null && prev.getNode() != null) {
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
                if (prevPrev != null && prevPrev.getNode() != null) {
                    IElementType prevPrevType = prevPrev.getNode().getElementType();
                    return prevPrevType == KiteTokenTypes.INPUT ||
                           prevPrevType == KiteTokenTypes.OUTPUT ||
                           prevPrevType == KiteTokenTypes.VAR ||
                           prevPrevType == KiteTokenTypes.RESOURCE ||
                           prevPrevType == KiteTokenTypes.COMPONENT;
                }
            }
        }

        return false;
    }

    /**
     * Check if the identifier is a property access (after a DOT).
     *
     * @param identifier The identifier element to check
     * @return true if this is a property access
     */
    public static boolean isPropertyAccess(PsiElement identifier) {
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        return prev != null && prev.getNode() != null &&
               prev.getNode().getElementType() == KiteTokenTypes.DOT;
    }

    /**
     * Check if the identifier is a decorator name (immediately after @).
     * Decorators are global/built-in and don't need to be declared.
     *
     * @param identifier The identifier element to check
     * @return true if this is a decorator name
     */
    public static boolean isDecoratorName(PsiElement identifier) {
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        return prev != null && prev.getNode() != null &&
               prev.getNode().getElementType() == KiteTokenTypes.AT;
    }

    /**
     * Check if the element is inside an import statement.
     * Identifiers in import statements are being declared/imported, not referenced.
     * Patterns:
     * - import * from "file"
     * - import SymbolName from "file"
     * - import A, B, C from "file"
     *
     * @param element The element to check
     * @return true if the element is inside an import statement
     */
    public static boolean isInsideImportStatement(PsiElement element) {
        // Walk up the PSI tree to check if we're inside an IMPORT_STATEMENT
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent.getNode() != null &&
                parent.getNode().getElementType() == KiteElementTypes.IMPORT_STATEMENT) {
                return true;
            }
            parent = parent.getParent();
        }

        // Also check sibling-level: import might not be wrapped in IMPORT_STATEMENT element
        // Check if preceded by IMPORT keyword at the same level or nearby
        PsiElement current = element;
        while (current != null) {
            PsiElement prev = current.getPrevSibling();
            while (prev != null) {
                if (prev.getNode() != null) {
                    IElementType prevType = prev.getNode().getElementType();
                    // Found IMPORT keyword - we're in an import statement
                    if (prevType == KiteTokenTypes.IMPORT) {
                        return true;
                    }
                    // If we hit a newline or another statement-level element, stop
                    if (prevType == KiteTokenTypes.NL || prevType == KiteTokenTypes.NEWLINE) {
                        break;
                    }
                }
                prev = prev.getPrevSibling();
            }
            // Move to parent and continue checking
            current = current.getParent();
            if (current instanceof PsiFile) {
                break;
            }
        }

        return false;
    }

    /**
     * Check if the identifier is a type annotation in a declaration.
     * Pattern: var string name = value
     * ^^^^^^ type annotation
     *
     * @param identifier The identifier element to check
     * @return true if this is a type annotation
     */
    public static boolean isTypeAnnotation(PsiElement identifier) {
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());
        if (prev == null || prev.getNode() == null) return false;

        IElementType prevType = prev.getNode().getElementType();

        // If preceded by declaration keyword, this could be a type annotation
        if (prevType == KiteTokenTypes.VAR ||
            prevType == KiteTokenTypes.INPUT ||
            prevType == KiteTokenTypes.OUTPUT) {
            // Check if there's another identifier after this (the name)
            PsiElement next = skipWhitespace(identifier.getNextSibling());
            return next != null && next.getNode() != null &&
                   next.getNode().getElementType() == KiteTokenTypes.IDENTIFIER; // This is a type annotation
        }

        return false;
    }

    /**
     * Check if the identifier is a property definition inside a schema, resource, or component body.
     * Pattern: type propertyName (e.g., "string host" inside a schema body)
     * Also handles array types: type[] propertyName (e.g., "string[] tags")
     *
     * @param identifier The identifier element to check
     * @return true if this is a property definition
     */
    public static boolean isPropertyDefinition(PsiElement identifier) {
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

        // Handle array types: type[] propertyName
        // PSI structure: IDENTIFIER(string) -> ARRAY_LITERAL([]) -> IDENTIFIER(tags)
        if (prevType == KiteElementTypes.ARRAY_LITERAL) {
            // Walk back past the ARRAY_LITERAL to find the type identifier
            PsiElement beforeArray = skipWhitespaceBackward(prev.getPrevSibling());
            if (beforeArray != null && beforeArray.getNode() != null) {
                IElementType beforeArrayType = beforeArray.getNode().getElementType();
                // If there's an identifier or 'any' keyword before [], this is a property name
                if (beforeArrayType == KiteTokenTypes.IDENTIFIER || beforeArrayType == KiteTokenTypes.ANY) {
                    return true;
                }
            }
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
            return prevPrevType == KiteTokenTypes.NL || prevPrevType == KiteTokenTypes.LBRACE;
        }

        return false;
    }

    /**
     * Check if the element is inside a schema, resource, or component body (between { and }).
     *
     * @param element The element to check
     * @return true if inside a declaration body
     */
    public static boolean isInsideDeclarationBody(PsiElement element) {
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
}
