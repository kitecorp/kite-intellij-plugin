package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KiteIdentifierContextHelper;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static cloud.kitelang.intellij.util.KitePsiUtil.skipWhitespaceBackward;

/**
 * Annotator that reports errors when reserved words are used as property/input/output names.
 * <p>
 * Example:
 * <pre>
 * schema Config {
 *     string string     // Error: 'string' is a reserved word
 *     number if         // Error: 'if' is a reserved word
 * }
 *
 * component Server {
 *     input string var  // Error: 'var' is a reserved word
 *     output number return  // Error: 'return' is a reserved word
 * }
 * </pre>
 */
public class KiteReservedNamesAnnotator implements Annotator {

    // Reserved type names (lexed as IDENTIFIER in Kite)
    private static final Set<String> TYPE_NAMES = Set.of(
            "string", "number", "boolean", "any", "object", "void", "null"
    );

    // Reserved keywords (lexed as their own token types like IF, RETURN, VAR)
    private static final Set<String> KEYWORDS = Set.of(
            "if", "else", "for", "while", "in", "return", "var", "fun",
            "schema", "component", "resource", "input", "output", "type",
            "import", "from", "init", "this", "true", "false"
    );

    // All reserved words
    private static final Set<String> RESERVED_WORDS;

    static {
        RESERVED_WORDS = new HashSet<>();
        RESERVED_WORDS.addAll(TYPE_NAMES);
        RESERVED_WORDS.addAll(KEYWORDS);
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (element.getContainingFile() == null ||
            element.getContainingFile().getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        String name = element.getText();

        // Check IDENTIFIER tokens (type names like "string", "number" are lexed as IDENTIFIER)
        if (type == KiteTokenTypes.IDENTIFIER) {
            // Skip if not a reserved word
            if (!RESERVED_WORDS.contains(name)) {
                return;
            }

            // Check if this identifier is in a name position (not type position)
            // Use the helper which properly handles line boundaries
            if (KiteIdentifierContextHelper.isPropertyDefinition(element) || isInputOutputName(element)) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                "'" + name + "' is a reserved word and cannot be used as a name")
                        .range(element)
                        .create();
            }
            return;
        }

        // Check keyword tokens (if, return, var, etc.) that appear in name positions
        // These are lexed as their own token types (IF, RETURN, VAR, etc.)
        if (isKeywordToken(type)) {
            // Check if this keyword is in a name position where it shouldn't be
            if (isKeywordInNamePosition(element)) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                "'" + name + "' is a reserved word and cannot be used as a name")
                        .range(element)
                        .create();
            }
        }
    }

    /**
     * Check if the token type is a keyword token.
     */
    private boolean isKeywordToken(IElementType type) {
        return type == KiteTokenTypes.IF ||
               type == KiteTokenTypes.ELSE ||
               type == KiteTokenTypes.FOR ||
               type == KiteTokenTypes.WHILE ||
               type == KiteTokenTypes.IN ||
               type == KiteTokenTypes.RETURN ||
               type == KiteTokenTypes.VAR ||
               type == KiteTokenTypes.FUN ||
               type == KiteTokenTypes.SCHEMA ||
               type == KiteTokenTypes.COMPONENT ||
               type == KiteTokenTypes.RESOURCE ||
               type == KiteTokenTypes.INPUT ||
               type == KiteTokenTypes.OUTPUT ||
               type == KiteTokenTypes.TYPE ||
               type == KiteTokenTypes.IMPORT ||
               type == KiteTokenTypes.FROM ||
               type == KiteTokenTypes.INIT ||
               type == KiteTokenTypes.THIS ||
               type == KiteTokenTypes.TRUE ||
               type == KiteTokenTypes.FALSE ||
               type == KiteTokenTypes.OBJECT ||
               type == KiteTokenTypes.ANY;
    }

    /**
     * Check if a keyword token is in a name position (schema property, input/output name).
     */
    private boolean isKeywordInNamePosition(PsiElement keyword) {
        // Check if inside schema, resource, or component body
        if (KiteIdentifierContextHelper.isInsideDeclarationBody(keyword)) {
            // Check if preceded by a type identifier (indicating this is a property name position)
            PsiElement prev = skipWhitespaceBackward(keyword.getPrevSibling());
            if (prev != null && prev.getNode() != null) {
                IElementType prevType = prev.getNode().getElementType();

                // Handle array types: type[] keyword
                if (prevType == KiteElementTypes.ARRAY_LITERAL) {
                    prev = skipWhitespaceBackward(prev.getPrevSibling());
                    if (prev != null && prev.getNode() != null) {
                        prevType = prev.getNode().getElementType();
                    }
                }

                // If preceded by IDENTIFIER (type) or ANY, this is a name position
                if (prevType == KiteTokenTypes.IDENTIFIER || prevType == KiteTokenTypes.ANY) {
                    return true;
                }
            }
        }

        // Check if inside input/output declaration
        PsiElement parent = keyword.getParent();
        while (parent != null) {
            if (parent.getNode() != null) {
                IElementType parentType = parent.getNode().getElementType();
                if (parentType == KiteElementTypes.INPUT_DECLARATION ||
                    parentType == KiteElementTypes.OUTPUT_DECLARATION) {
                    // Check if preceded by a type identifier
                    PsiElement prev = skipWhitespaceBackward(keyword.getPrevSibling());
                    if (prev != null && prev.getNode() != null) {
                        IElementType prevType = prev.getNode().getElementType();

                        // Handle array types
                        if (prevType == KiteElementTypes.ARRAY_LITERAL) {
                            prev = skipWhitespaceBackward(prev.getPrevSibling());
                            if (prev != null && prev.getNode() != null) {
                                prevType = prev.getNode().getElementType();
                            }
                        }

                        if (prevType == KiteTokenTypes.IDENTIFIER || prevType == KiteTokenTypes.ANY) {
                            return true;
                        }
                    }
                    break;
                }
            }
            parent = parent.getParent();
        }

        return false;
    }

    /**
     * Check if identifier is an input/output name.
     * Pattern: input/output type NAME [= value]
     */
    private boolean isInputOutputName(PsiElement identifier) {
        // Check if inside an input/output declaration
        PsiElement parent = identifier.getParent();

        if (parent == null || parent.getNode() == null) {
            return false;
        }

        IElementType parentType = parent.getNode().getElementType();

        if (parentType != KiteElementTypes.INPUT_DECLARATION &&
            parentType != KiteElementTypes.OUTPUT_DECLARATION) {
            return false;
        }

        // Check if this is the name position (after type, not the type itself)
        // Pattern: INPUT/OUTPUT type NAME
        PsiElement prev = skipWhitespaceBackward(identifier.getPrevSibling());

        if (prev != null && prev.getNode() != null) {
            IElementType prevType = prev.getNode().getElementType();

            // Check for ARRAY_LITERAL
            if (prevType == KiteElementTypes.ARRAY_LITERAL) {
                prev = skipWhitespaceBackward(prev.getPrevSibling());
                if (prev != null && prev.getNode() != null) {
                    prevType = prev.getNode().getElementType();
                }
            }

            // Previous should be a type (IDENTIFIER or ANY) not INPUT/OUTPUT keyword
            if (prevType == KiteTokenTypes.IDENTIFIER || prevType == KiteTokenTypes.ANY) {
                return true;
            }
        }

        return false;
    }
}
