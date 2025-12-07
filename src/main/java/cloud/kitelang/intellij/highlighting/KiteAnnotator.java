package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.util.KiteIdentifierContextHelper;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Semantic annotator for Kite language that provides context-aware syntax highlighting.
 * This annotator identifies types in various contexts and highlights them appropriately.
 * Also handles string interpolation highlighting and property name/value distinction.
 */
public class KiteAnnotator implements Annotator {

    // Type color - nice blue
    public static final TextAttributesKey TYPE_NAME =
            createTextAttributesKey("KITE_TYPE_NAME",
                    new TextAttributes(JBColor.namedColor("Kite.typeName", new Color(0x498BF6)),
                            null, null, null, Font.PLAIN));

    // Pattern to match string interpolations: ${expression} and $var
    // Supports: ${var}, ${obj.prop}, ${func()}, ${arr[0]}, $var, etc.
    private static final Pattern BRACE_INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern SIMPLE_INTERPOLATION_PATTERN = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        IElementType elementType = element.getNode().getElementType();

        // Handle string interpolations
        if (elementType == KiteTokenTypes.STRING) {
            annotateStringInterpolations(element, holder);
            return;
        }

        // Handle 'any' keyword - color it as TYPE_NAME (blue) like other type keywords
        if (elementType == KiteTokenTypes.ANY) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(TYPE_NAME)
                    .create();
            return;
        }

        // Only process identifiers for type highlighting
        if (elementType != KiteTokenTypes.IDENTIFIER) {
            return;
        }

        // Skip import symbol identifiers - they should use default text color
        if (KiteIdentifierContextHelper.isInsideImportStatement(element)) {
            return;
        }

        // Get the line of text containing this element
        String line = getLineText(element);
        String beforeElement = getTextBeforeInLine(element);
        String afterElement = getTextAfterInLine(element);

        // Check if this identifier is a decorator name (comes after @)
        if (beforeElement.matches(".*@\\s*$")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(KiteSyntaxHighlighter.DECORATOR)
                    .create();
            return;
        }

        // Function names and calls use default text color - no special highlighting needed
        // (Previously highlighted with FUNCTION_NAME but user requested default color)

        // Check if this identifier is a type
        boolean isType = false;

        // After input/output, the first identifier is always a type
        if (beforeElement.matches(".*(^|\\s)(input|output)\\s+$")) {
            isType = true;
        }
        // After resource, the first identifier is always a type (schema name)
        // resource SchemaType instanceName { ... }
        else if (beforeElement.matches(".*(^|\\s)resource\\s+$")) {
            isType = true;
        }
        // After component, it's a type only if followed by ANOTHER identifier (instantiation)
        // component TypeName instanceName { ... } - TypeName is type (blue)
        // component TypeName[] instanceName { ... } - TypeName is type (blue)
        // component TypeName { ... } - TypeName is definition name (default)
        else if (beforeElement.matches(".*(^|\\s)component\\s+$")) {
            // Check if followed by optional [] and another identifier before { (instantiation)
            if (afterElement.matches("^(\\[\\d*\\])*\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*\\{.*") ||
                afterElement.matches("^(\\[\\d*\\])*\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*$")) {
                isType = true;
            }
            // If followed directly by { or end of line, it's a definition name (not a type)
        }
        // After colon (but not after =), it's a type
        else if (beforeElement.matches(".*:\\s*$") && !beforeElement.contains("=")) {
            isType = true;
        }
        // After dot, it's part of a type chain (e.g., VM.Instance) BUT NOT if there's an = before it
        // server.size after "=" is property access, not a type
        else if (beforeElement.matches(".*\\.\\s*$") && !beforeElement.contains("=")) {
            isType = true;
        }
        // After var, it's only a type if followed by another identifier (not = or +=)
        // Also handles array types: var string[] names
        else if (beforeElement.matches(".*(^|\\s)var\\s+$")) {
            // Check if followed by optional [] and then another identifier (not = or +=)
            if (afterElement.matches("^(\\[\\d*\\])*\\s+[a-zA-Z_][a-zA-Z0-9_]*.*")) {
                isType = true;
            }
        }
        // After opening paren or comma (function parameters), check if followed by identifier
        // BUT exclude object literal property names (which are followed by colon)
        // Also handles array types: fun process(string[] items)
        else if (beforeElement.matches(".*[\\(,]\\s*$")) {
            // Skip if this is an object literal property (followed by colon)
            if (afterElement.matches("^\\s*:.*")) {
                // This is an object property name, not a type
                isType = false;
            }
            // It's a type if followed by optional [] and another identifier (not , or ) or :)
            else if (afterElement.matches("^(\\[\\d*\\])*\\s+[a-zA-Z_][a-zA-Z0-9_]*.*") &&
                     !afterElement.matches("^\\s*[,\\):].*")) {
                isType = true;
            }
        }
        // After closing paren (return type), check if before opening brace
        // Also handles array return types: fun process() string[] {
        else if (beforeElement.matches(".*\\)\\s*$")) {
            // It's a return type if followed by optional [] and then { or . (for dotted types)
            if (afterElement.matches("^(\\[\\d*\\])*\\s*[\\{].*") || afterElement.matches("^\\s*\\..*")) {
                isType = true;
            }
        }
        // Schema property type: line starts with whitespace (or after decorator like @cloud),
        // identifier is followed by another identifier
        // Also handles array types in schemas: string[] names
        // Patterns: "  string name" or "@cloud string name"
        else if (beforeElement.matches("^\\s*$") || beforeElement.matches(".*[\\{\\n]\\s*$") ||
                 beforeElement.matches(".*@[a-zA-Z_][a-zA-Z0-9_]*\\s+$")) {
            // It's a type if followed by optional [] and another identifier (the property name)
            if (afterElement.matches("^(\\[\\d*\\])*\\s+[a-zA-Z_][a-zA-Z0-9_]*.*")) {
                isType = true;
            }
        }

        if (isType) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(TYPE_NAME)
                    .create();
        }

        // All other identifiers (property names, values, declaration names) use default text color
        // No special highlighting needed - they inherit from IDENTIFIER
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

    private String getTextBeforeInLine(PsiElement element) {
        int offset = element.getTextRange().getStartOffset();
        String fileText = element.getContainingFile().getText();

        // Find start of line
        int lineStart = offset;
        while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        return fileText.substring(lineStart, offset);
    }

    private String getTextAfterInLine(PsiElement element) {
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
     * Highlights ${...} and $var patterns with special colors.
     */
    private void annotateStringInterpolations(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        String text = element.getText();
        int startOffset = element.getTextRange().getStartOffset();

        // Track which positions are already highlighted (to avoid double-highlighting)
        java.util.Set<Integer> highlightedPositions = new java.util.HashSet<>();

        // First, handle ${expression} patterns
        Matcher braceMatcher = BRACE_INTERPOLATION_PATTERN.matcher(text);
        while (braceMatcher.find()) {
            int matchStart = braceMatcher.start();
            int matchEnd = braceMatcher.end();

            // Mark these positions as highlighted
            for (int i = matchStart; i < matchEnd; i++) {
                highlightedPositions.add(i);
            }

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

        // Then, handle $var patterns (but skip if already part of ${...})
        Matcher simpleMatcher = SIMPLE_INTERPOLATION_PATTERN.matcher(text);
        while (simpleMatcher.find()) {
            int matchStart = simpleMatcher.start();

            // Skip if this position was already highlighted by ${...} pattern
            if (highlightedPositions.contains(matchStart)) {
                continue;
            }

            int matchEnd = simpleMatcher.end();

            // Highlight $ - delimiter
            TextRange delimRange = new TextRange(startOffset + matchStart, startOffset + matchStart + 1);
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(delimRange)
                    .textAttributes(KiteSyntaxHighlighter.INTERPOLATION_DELIM)
                    .create();

            // Highlight the variable name
            TextRange varRange = new TextRange(startOffset + matchStart + 1, startOffset + matchEnd);
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(varRange)
                    .textAttributes(KiteSyntaxHighlighter.INTERPOLATION_VAR)
                    .create();
        }
    }
}
