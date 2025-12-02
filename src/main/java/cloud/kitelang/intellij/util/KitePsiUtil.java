package cloud.kitelang.intellij.util;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for common PSI navigation operations.
 * <p>
 * This class provides low-level utilities for navigating the PSI (Program Structure Interface)
 * tree in Kite files. These utilities are used by other helpers and the annotator.
 * <p>
 * <b>Whitespace Handling:</b><br>
 * Kite files can contain both IntelliJ platform whitespace tokens ({@code TokenType.WHITE_SPACE})
 * and Kite-specific whitespace tokens ({@code WHITESPACE}, {@code NL}, {@code NEWLINE}).
 * Always use the methods in this class to skip whitespace to handle both types.
 * <p>
 * <b>Key Methods:</b>
 * <ul>
 *   <li>{@link #skipWhitespace} - Skip forward past whitespace tokens</li>
 *   <li>{@link #skipWhitespaceBackward} - Skip backward past whitespace tokens</li>
 *   <li>{@link #isWhitespace} - Check if a token type is whitespace</li>
 *   <li>{@link #isDescendantOf} - Check parent-child relationship</li>
 *   <li>{@link #findFirstChildOfType} - Find child by element type</li>
 *   <li>{@link #getElementType} - Safely get element type (null-safe)</li>
 *   <li>{@link #isInsideBraces} - Check if position is inside declaration braces</li>
 * </ul>
 *
 * @see KiteIdentifierContextHelper for identifier-specific navigation
 * @see KiteImportValidationHelper for import statement navigation
 */
public final class KitePsiUtil {

    private KitePsiUtil() {
        // Utility class
    }

    /**
     * Skip whitespace tokens forward.
     *
     * @param element The starting element
     * @return The first non-whitespace element, or null if none found
     */
    @Nullable
    public static PsiElement skipWhitespace(@Nullable PsiElement element) {
        while (element != null && element.getNode() != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    /**
     * Skip whitespace tokens forward (alias for {@link #skipWhitespace(PsiElement)}).
     *
     * @param element The starting element
     * @return The first non-whitespace element, or null if none found
     * @see #skipWhitespace(PsiElement)
     */
    @Nullable
    public static PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        return skipWhitespace(element);
    }

    /**
     * Skip whitespace tokens backward.
     *
     * @param element The starting element
     * @return The first non-whitespace element going backward, or null if none found
     */
    @Nullable
    public static PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && element.getNode() != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    /**
     * Check if element type is whitespace (including newlines).
     *
     * @param type The element type to check
     * @return true if the type represents whitespace
     */
    public static boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Check if a PSI element is whitespace (including newlines).
     *
     * @param element The element to check
     * @return true if the element represents whitespace
     */
    public static boolean isWhitespaceElement(@Nullable PsiElement element) {
        if (element == null || element.getNode() == null) {
            return false;
        }
        return isWhitespace(element.getNode().getElementType());
    }

    /**
     * Check if a PSI element is whitespace (including newlines).
     * Alias for {@link #isWhitespaceElement(PsiElement)} for API consistency.
     *
     * @param element The element to check
     * @return true if the element represents whitespace
     * @see #isWhitespaceElement(PsiElement)
     */
    public static boolean isWhitespace(@Nullable PsiElement element) {
        return isWhitespaceElement(element);
    }

    /**
     * Check if element is a descendant of ancestor.
     *
     * @param element  The potential descendant
     * @param ancestor The potential ancestor
     * @return true if element is a descendant of ancestor
     */
    public static boolean isDescendantOf(PsiElement element, PsiElement ancestor) {
        PsiElement current = element;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Find the first child element of the given type.
     *
     * @param parent The parent element
     * @param type   The element type to find
     * @return The first child with the given type, or null if not found
     */
    @Nullable
    public static PsiElement findFirstChildOfType(PsiElement parent, IElementType type) {
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == type) {
                return child;
            }
        }
        return null;
    }

    /**
     * Find the next sibling of the given type.
     *
     * @param element The starting element
     * @param type    The element type to find
     * @return The next sibling with the given type, or null if not found
     */
    @Nullable
    public static PsiElement findNextSiblingOfType(PsiElement element, IElementType type) {
        PsiElement sibling = element.getNextSibling();
        while (sibling != null) {
            if (sibling.getNode() != null && sibling.getNode().getElementType() == type) {
                return sibling;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    /**
     * Find the previous sibling of the given type.
     *
     * @param element The starting element
     * @param type    The element type to find
     * @return The previous sibling with the given type, or null if not found
     */
    @Nullable
    public static PsiElement findPrevSiblingOfType(PsiElement element, IElementType type) {
        PsiElement sibling = element.getPrevSibling();
        while (sibling != null) {
            if (sibling.getNode() != null && sibling.getNode().getElementType() == type) {
                return sibling;
            }
            sibling = sibling.getPrevSibling();
        }
        return null;
    }

    /**
     * Get the element type safely.
     *
     * @param element The element
     * @return The element type, or null if the element or its node is null
     */
    @Nullable
    public static IElementType getElementType(@Nullable PsiElement element) {
        if (element == null || element.getNode() == null) {
            return null;
        }
        return element.getNode().getElementType();
    }

    /**
     * Check if element has the specified type.
     *
     * @param element The element to check
     * @param type    The expected type
     * @return true if element has the specified type
     */
    public static boolean hasType(@Nullable PsiElement element, IElementType type) {
        return element != null && element.getNode() != null &&
               element.getNode().getElementType() == type;
    }

    /**
     * Find the name of a declaration element.
     * <p>
     * Handles different declaration patterns:
     * <ul>
     *   <li>For-loops: identifier after FOR keyword and before IN</li>
     *   <li>Functions: name after FUN keyword and before LPAREN</li>
     *   <li>Components: last identifier before LBRACE (handles both definitions and instantiations)</li>
     *   <li>Default (variables, resources, schemas, types): last identifier before = or {</li>
     * </ul>
     *
     * @param declaration The declaration element
     * @param type        The element type of the declaration
     * @return The declaration name, or null if not found
     */
    @Nullable
    public static String findDeclarationName(@NotNull PsiElement declaration, @NotNull IElementType type) {
        // For for-loop statements, the name is after FOR and before IN
        if (type == KiteElementTypes.FOR_STATEMENT) {
            return findForLoopVariable(declaration);
        }

        // For function declarations, the name is after FUN and before LPAREN
        if (type == KiteElementTypes.FUNCTION_DECLARATION) {
            boolean foundFun = false;
            for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNode() == null) continue;
                IElementType childType = child.getNode().getElementType();

                if (childType == KiteTokenTypes.FUN) {
                    foundFun = true;
                } else if (foundFun && childType == KiteTokenTypes.IDENTIFIER) {
                    return child.getText();
                } else if (childType == KiteTokenTypes.LPAREN) {
                    break;
                }
            }
            return null;
        }

        // For component declarations, handle both definitions and instantiations
        if (type == KiteElementTypes.COMPONENT_DECLARATION) {
            List<String> identifiers = new ArrayList<>();
            for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNode() == null) continue;
                IElementType childType = child.getNode().getElementType();

                if (childType == KiteTokenTypes.IDENTIFIER) {
                    identifiers.add(child.getText());
                } else if (childType == KiteTokenTypes.LBRACE) {
                    break;
                }
            }
            // Return the last identifier (instance name for instantiations, type name for definitions)
            return identifiers.isEmpty() ? null : identifiers.get(identifiers.size() - 1);
        }

        // Default: find identifier before = or {
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

    public static @Nullable String findForLoopVariable(@NotNull PsiElement declaration) {
        boolean foundFor = false;
        for (var child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            var childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.FOR) {
                foundFor = true;
            } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                return child.getText();
            } else if (childType == KiteTokenTypes.IN) {
                break;
            }
        }
        return null;
    }

    /**
     * Check if a position element is inside the braces of a declaration.
     * <p>
     * This method determines if the given position is between the opening {@code {}
     * and closing {@code }} brace of a declaration element such as a resource,
     * component, schema, or function body.
     * <p>
     * Handles unclosed braces gracefully: if the opening brace is found but the
     * closing brace is missing (user still typing), the position is still considered
     * "inside" the braces.
     *
     * @param position    The position element to check (e.g., cursor location)
     * @param declaration The declaration element containing the braces
     * @return true if position is inside the braces, false otherwise
     */
    public static boolean isInsideBraces(PsiElement position, PsiElement declaration) {
        var posOffset = position.getTextOffset();
        var lbraceOffset = -1;
        var rbraceOffset = -1;
        var foundLBrace = false;

        for (var child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                var type = child.getNode().getElementType();
                if (type == KiteTokenTypes.LBRACE) {
                    foundLBrace = true;
                    lbraceOffset = child.getTextOffset();
                } else if (type == KiteTokenTypes.RBRACE && foundLBrace) {
                    rbraceOffset = child.getTextOffset();
                    break;
                }
            }
        }

        // Position is inside if:
        // 1. We found an opening brace
        // 2. Position is after the opening brace
        // 3. Either closing brace is missing (unclosed) or position is before closing brace
        return foundLBrace && lbraceOffset >= 0 && posOffset > lbraceOffset &&
               (rbraceOffset < 0 || posOffset < rbraceOffset);
    }
}
