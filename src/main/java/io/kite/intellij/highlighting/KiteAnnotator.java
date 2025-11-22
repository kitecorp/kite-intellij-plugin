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
                    new TextAttributes(JBColor.namedColor("Kite.typeName", new Color(0x3b82f6)),
                            null, null, null, Font.PLAIN));

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        IElementType elementType = element.getNode().getElementType();

        // Only process identifiers
        if (elementType != KiteTokenTypes.IDENTIFIER) {
            return;
        }

        // Get the line of text containing this element
        String line = getLineText(element);
        String beforeElement = getTextBeforeInLine(element, line);

        // Check if this identifier is a decorator name (comes after @)
        if (beforeElement.matches(".*@\\s*$")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(KiteSyntaxHighlighter.DECORATOR)
                    .create();
            return;
        }

        // Check if this identifier is a type (comes after type-expecting keywords)
        // Match patterns like: "input ", "output ", "var ", "component ", "resource ", ": "
        if (beforeElement.matches(".*(^|\\s)(input|output|var|component|resource)\\s+$") ||
            beforeElement.matches(".*:\\s*$")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(TYPE_NAME)
                    .create();
        }
    }

    private String getLineText(PsiElement element) {
        int offset = element.getTextRange().getStartOffset();
        String fileText = element.getContainingFile().getText();

        // Find start of line
        int lineStart = offset;
        while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        // Find end of line
        int lineEnd = offset;
        while (lineEnd < fileText.length() && fileText.charAt(lineEnd) != '\n') {
            lineEnd++;
        }

        return fileText.substring(lineStart, lineEnd);
    }

    private String getTextBeforeInLine(PsiElement element, String line) {
        int offset = element.getTextRange().getStartOffset();
        String fileText = element.getContainingFile().getText();

        // Find start of line
        int lineStart = offset;
        while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        return fileText.substring(lineStart, offset);
    }
}
