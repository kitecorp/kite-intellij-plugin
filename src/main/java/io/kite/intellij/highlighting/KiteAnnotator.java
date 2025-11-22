package io.kite.intellij.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import io.kite.intellij.psi.KiteTokenTypes;
import org.antlr.v4.runtime.tree.ParseTree;
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

        // Get text before this element to determine context
        String textBefore = getTextBefore(element, 30);

        // Check if this identifier is a decorator name (comes after @)
        // Match @ with optional whitespace before the identifier
        if (textBefore.trim().endsWith("@") || textBefore.matches(".*@\\s+$")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(KiteSyntaxHighlighter.DECORATOR)
                    .create();
            return;
        }

        // Check if this identifier is a type (comes after type-expecting keywords)
        // Match: input/output/var/component/resource followed by whitespace
        if (textBefore.matches(".*(input|output|var|component|resource|:)\\s+$")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(TYPE_NAME)
                    .create();
        }
    }

    private String getTextBefore(PsiElement element, int maxLength) {
        int startOffset = Math.max(0, element.getTextRange().getStartOffset() - maxLength);
        int endOffset = element.getTextRange().getStartOffset();
        return element.getContainingFile().getText().substring(startOffset, endOffset);
    }
}
