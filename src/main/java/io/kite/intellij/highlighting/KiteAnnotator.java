package io.kite.intellij.highlighting;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Semantic annotator for Kite language that provides context-aware syntax highlighting.
 * This annotator identifies types in various contexts and highlights them appropriately.
 * Also handles string interpolation highlighting.
 */
public class KiteAnnotator implements Annotator {

    // Type color - nice blue
    public static final TextAttributesKey TYPE_NAME =
            createTextAttributesKey("KITE_TYPE_NAME",
                    new TextAttributes(JBColor.namedColor("Kite.typeName", new Color(0x498BF6)),
                            null, null, null, Font.PLAIN));

    // Pattern to match string interpolations: ${expression}
    // Supports: ${var}, ${obj.prop}, ${func()}, ${arr[0]}, etc.
    private static final Pattern INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        IElementType elementType = element.getNode().getElementType();

        // Handle string interpolations
        if (elementType == KiteTokenTypes.STRING) {
            annotateStringInterpolations(element, holder);
            return;
        }

        // Only process identifiers for type highlighting
        if (elementType != KiteTokenTypes.IDENTIFIER) {
            return;
        }

        // Get the line of text containing this element
        String line = getLineText(element);
        String beforeElement = getTextBeforeInLine(element, line);
        String afterElement = getTextAfterInLine(element, line);

        // Check if this identifier is a decorator name (comes after @)
        if (beforeElement.matches(".*@\\s*$")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(KiteSyntaxHighlighter.DECORATOR)
                    .create();
            return;
        }

        // Check if this identifier is a type
        boolean isType = false;

        // After input/output/component/resource, the first identifier is always a type
        if (beforeElement.matches(".*(^|\\s)(input|output|component|resource)\\s+$")) {
            isType = true;
        }
        // After colon, it's a type
        else if (beforeElement.matches(".*:\\s*$")) {
            isType = true;
        }
        // After dot, it's part of a type chain (e.g., VM.Instance)
        else if (beforeElement.matches(".*\\.\\s*$")) {
            isType = true;
        }
        // After var, it's only a type if followed by another identifier (not = or +=)
        else if (beforeElement.matches(".*(^|\\s)var\\s+$")) {
            // Check if followed by another identifier (not = or +=)
            if (afterElement.matches("^\\s+[a-zA-Z_][a-zA-Z0-9_]*.*")) {
                isType = true;
            }
        }
        // After opening paren or comma (function parameters), check if followed by identifier
        else if (beforeElement.matches(".*[\\(,]\\s*$")) {
            // It's a type if followed by another identifier (not , or ))
            if (afterElement.matches("^\\s+[a-zA-Z_][a-zA-Z0-9_]*.*") &&
                !afterElement.matches("^\\s*[,\\)].*")) {
                isType = true;
            }
        }
        // After closing paren (return type), check if before opening brace
        else if (beforeElement.matches(".*\\)\\s*$")) {
            // It's a return type if followed by { or another identifier (for dotted types)
            if (afterElement.matches("^\\s*[\\{].*") || afterElement.matches("^\\s*\\..*")) {
                isType = true;
            }
        }
        // Schema property type: line starts with whitespace, identifier is followed by another identifier
        else if (beforeElement.matches("^\\s*$") || beforeElement.matches(".*[\\{\\n]\\s*$")) {
            // It's a type if followed by another identifier (the property name)
            if (afterElement.matches("^\\s+[a-zA-Z_][a-zA-Z0-9_]*.*")) {
                isType = true;
            }
        }

        if (isType) {
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

    private String getTextAfterInLine(PsiElement element, String line) {
        int offset = element.getTextRange().getEndOffset();
        String fileText = element.getContainingFile().getText();

        // Find end of line
        int lineEnd = offset;
        while (lineEnd < fileText.length() && fileText.charAt(lineEnd) != '\n') {
            lineEnd++;
        }

        return fileText.substring(offset, lineEnd);
    }

    /**
     * Annotates string interpolations within a STRING token.
     * Highlights ${...} patterns with special colors for delimiters and variable content.
     */
    private void annotateStringInterpolations(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        String text = element.getText();
        int startOffset = element.getTextRange().getStartOffset();

        Matcher matcher = INTERPOLATION_PATTERN.matcher(text);
        while (matcher.find()) {
            int matchStart = matcher.start();
            int matchEnd = matcher.end();
            String content = matcher.group(1);  // The content inside ${}

            // Highlight ${ - opening delimiter
            TextRange openDelimRange = new TextRange(startOffset + matchStart, startOffset + matchStart + 2);
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(openDelimRange)
                    .textAttributes(KiteSyntaxHighlighter.INTERPOLATION_DELIM)
                    .create();

            // Highlight the content (variable/expression) inside
            TextRange contentRange = new TextRange(startOffset + matchStart + 2, startOffset + matchEnd - 1);
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(contentRange)
                    .textAttributes(KiteSyntaxHighlighter.INTERPOLATION_VAR)
                    .create();

            // Highlight } - closing delimiter
            TextRange closeDelimRange = new TextRange(startOffset + matchEnd - 1, startOffset + matchEnd);
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(closeDelimRange)
                    .textAttributes(KiteSyntaxHighlighter.INTERPOLATION_DELIM)
                    .create();
        }
    }
}
