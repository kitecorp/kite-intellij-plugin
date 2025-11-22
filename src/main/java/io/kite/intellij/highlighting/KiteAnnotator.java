package io.kite.intellij.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Semantic annotator for Kite language that provides context-aware syntax highlighting.
 * This annotator identifies types in various contexts and highlights them appropriately.
 */
public class KiteAnnotator implements Annotator {

    // Type color - nice blue
    public static final TextAttributesKey TYPE_NAME =
            createTextAttributesKey("KITE_TYPE_NAME",
                    new TextAttributes(JBColor.namedColor("Kite.typeName", new Color(0x4EC9B0)),
                            null, null, null, Font.PLAIN));

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        IElementType elementType = element.getNode().getElementType();

        // Only process identifiers
        if (elementType != KiteTokenTypes.IDENTIFIER) {
            return;
        }

        // Find the previous meaningful (non-whitespace) element
        PsiElement prev = findPreviousNonWhitespace(element);
        if (prev == null) {
            return;
        }

        IElementType prevType = prev.getNode().getElementType();

        // Check if this identifier is a decorator name (comes after @)
        if (prevType == KiteTokenTypes.AT) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(KiteSyntaxHighlighter.DECORATOR)
                    .create();
            return;
        }

        // Check if this identifier is a type (comes after type-expecting keywords)
        if (prevType == KiteTokenTypes.INPUT ||
            prevType == KiteTokenTypes.OUTPUT ||
            prevType == KiteTokenTypes.VAR ||
            prevType == KiteTokenTypes.COMPONENT ||
            prevType == KiteTokenTypes.RESOURCE ||
            prevType == KiteTokenTypes.COLON) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(TYPE_NAME)
                    .create();
        }
    }

    private PsiElement findPreviousNonWhitespace(PsiElement element) {
        PsiElement current = element.getPrevSibling();

        while (current != null) {
            IElementType type = current.getNode().getElementType();

            // Skip whitespace, newlines, and comments
            if (type != KiteTokenTypes.WHITESPACE &&
                type != KiteTokenTypes.NL &&
                type != KiteTokenTypes.NEWLINE &&
                type != KiteTokenTypes.LINE_COMMENT &&
                type != KiteTokenTypes.BLOCK_COMMENT) {
                return current;
            }

            current = current.getPrevSibling();
        }

        return null;
    }
}
