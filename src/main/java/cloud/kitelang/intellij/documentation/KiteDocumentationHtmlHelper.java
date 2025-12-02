package cloud.kitelang.intellij.documentation;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * HTML rendering utilities for Kite documentation popups.
 * <p>
 * This class provides:
 * <ul>
 *   <li>HTML escaping methods for safe rendering</li>
 *   <li>Syntax highlighting colorization for code snippets</li>
 *   <li>Theme-aware background colors for sections</li>
 *   <li>Aligned member formatting for inputs/outputs</li>
 * </ul>
 * <p>
 * <b>Color Scheme:</b>
 * <ul>
 *   <li>Keywords (resource, var, etc.): Purple (#AB5FDB)</li>
 *   <li>Types (string, number, etc.): Blue (#498BF6)</li>
 *   <li>Strings: Green (#6A9955)</li>
 *   <li>Numbers: Blue (#6897BB)</li>
 *   <li>Decorators: Orange (#CC7832)</li>
 * </ul>
 *
 * @see KiteDocumentationProvider for the main documentation provider
 * @see KiteDecoratorDocumentation for decorator documentation
 */
public final class KiteDocumentationHtmlHelper {

    // CSS colors for syntax highlighting in documentation (matching editor colors)
    public static final String COLOR_KEYWORD = "#AB5FDB";   // Purple - keywords (matches KiteSyntaxHighlighter.KEYWORD)
    public static final String COLOR_TYPE = "#498BF6";      // Blue - type names (matches KiteAnnotator.TYPE_NAME)
    public static final String COLOR_STRING = "#6A9955";    // Green - string literals (matches KiteSyntaxHighlighter.STRING)
    public static final String COLOR_NUMBER = "#6897BB";    // Blue - number literals (matches IntelliJ Darcula)
    public static final String COLOR_DECORATOR = "#CC7832"; // Orange - decorators
    // Keywords that should be colored
    public static final Set<String> KEYWORDS = Set.of(
            "resource", "component", "schema", "fun", "var", "input", "output", "type",
            "if", "else", "for", "while", "in", "return", "import", "from", "init", "this",
            "true", "false", "null"
    );
    // Built-in type names
    public static final Set<String> TYPE_NAMES = Set.of(
            "string", "number", "boolean", "object", "any", "void", "list", "map"
    );

    private KiteDocumentationHtmlHelper() {
        // Utility class
    }

    /**
     * Escape HTML special characters.
     */
    @NotNull
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br/>");
    }

    /**
     * Escape HTML special characters without converting newlines.
     * For use inside {@code <pre>} tags where newlines should be preserved as-is.
     */
    @NotNull
    public static String escapeHtmlNoBreaks(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Gets a theme-aware section background color.
     * In light theme: slightly darker than background (like a subtle card)
     * In dark theme: slightly lighter than background (like a subtle card)
     *
     * @return hex color string like "#e8e8e8" for light theme or "#3c3f41" for dark theme
     */
    @NotNull
    public static String getSectionBackgroundColor() {
        Color bgColor = new JBColor(
                new Color(0xe8, 0xe8, 0xe8),  // Light theme: light gray
                new Color(0x3c, 0x3f, 0x41)   // Dark theme: slightly lighter than default dark bg
        );
        return String.format("#%02x%02x%02x", bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
    }

    /**
     * Gets a theme-aware code background color.
     * Slightly different from section background to distinguish code examples.
     *
     * @return hex color string
     */
    @NotNull
    public static String getCodeBackgroundColor() {
        return JBColor.isBright() ? "#EBEBEB" : "#383838";
    }

    /**
     * Colorize code text with syntax highlighting.
     * This applies coloring to keywords, types, strings, and numbers.
     */
    @NotNull
    public static String colorizeCode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Handle string literals (quoted strings)
            if (c == '"') {
                int start = i;
                i++; // skip opening quote
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) {
                        i++; // skip escaped char
                    }
                    i++;
                }
                if (i < len) i++; // skip closing quote
                String str = escapeHtml(text.substring(start, i));
                result.append("<span style=\"color: ").append(COLOR_STRING).append(";\">").append(str).append("</span>");
                continue;
            }

            // Handle identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String word = text.substring(start, i);

                if (KEYWORDS.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append("; font-weight: bold;\">")
                            .append(escapeHtml(word)).append("</span>");
                } else if (TYPE_NAMES.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                            .append(escapeHtml(word)).append("</span>");
                } else {
                    result.append(escapeHtml(word));
                }
                continue;
            }

            // Handle numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                String num = text.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                        .append(escapeHtml(num)).append("</span>");
                continue;
            }

            // Other characters - just escape and append
            result.append(escapeHtml(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    /**
     * Colorize code text with syntax highlighting (no newline conversion).
     * For use inside {@code <pre>} tags where newlines should be preserved.
     */
    @NotNull
    public static String colorizeCodeNoBreaks(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Handle string literals (quoted strings)
            if (c == '"') {
                int start = i;
                i++; // skip opening quote
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) {
                        i++; // skip escaped char
                    }
                    i++;
                }
                if (i < len) i++; // skip closing quote
                String str = escapeHtmlNoBreaks(text.substring(start, i));
                result.append("<span style=\"color: ").append(COLOR_STRING).append(";\">").append(str).append("</span>");
                continue;
            }

            // Handle identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                String word = text.substring(start, i);

                if (KEYWORDS.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_KEYWORD).append("; font-weight: bold;\">")
                            .append(escapeHtmlNoBreaks(word)).append("</span>");
                } else if (TYPE_NAMES.contains(word)) {
                    result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                            .append(escapeHtmlNoBreaks(word)).append("</span>");
                } else {
                    result.append(escapeHtmlNoBreaks(word));
                }
                continue;
            }

            // Handle numbers
            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                String num = text.substring(start, i);
                result.append("<span style=\"color: ").append(COLOR_NUMBER).append(";\">")
                        .append(escapeHtmlNoBreaks(num)).append("</span>");
                continue;
            }

            // Other characters - just escape and append
            result.append(escapeHtmlNoBreaks(String.valueOf(c)));
            i++;
        }

        return result.toString();
    }

    /**
     * Colorize decorator text with syntax highlighting.
     * The @ symbol and decorator name get special coloring, arguments get colorized like code.
     */
    @NotNull
    public static String colorizeDecoratorNoBreaks(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = text.length();

        // Handle @ symbol
        if (i < len && text.charAt(i) == '@') {
            result.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">@</span>");
            i++;
        }

        // Handle decorator name (identifier after @)
        if (i < len && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '_')) {
            int start = i;
            while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                i++;
            }
            String decoratorName = text.substring(start, i);
            result.append("<span style=\"color: ").append(COLOR_DECORATOR).append(";\">")
                    .append(escapeHtmlNoBreaks(decoratorName)).append("</span>");
        }

        // Handle arguments (everything after decorator name) - colorize like code
        if (i < len) {
            String args = text.substring(i);
            result.append(colorizeCodeNoBreaks(args));
        }

        return result.toString();
    }

    /**
     * Format members as plain text with vertical alignment at '='.
     * Uses regular spaces for alignment (for use inside {@code <pre>} tags).
     * Applies syntax highlighting colors to types and values.
     *
     * @param members List of String arrays: [type, name, defaultValue (or null)]
     */
    @NotNull
    public static String formatAlignedMembersPlain(List<String[]> members) {
        if (members.isEmpty()) {
            return "";
        }

        // Find max length of "type name" portion (before '=')
        int maxLeftLength = 0;
        for (String[] parts : members) {
            int leftLength = parts[0].length() + 1 + parts[1].length(); // "type name"
            if (leftLength > maxLeftLength) {
                maxLeftLength = leftLength;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                result.append("\n");
            }

            String[] parts = members.get(i);
            String typeName = parts[0];
            String varName = parts[1];

            // Colorize the type name if it's a known type
            if (TYPE_NAMES.contains(typeName)) {
                result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                        .append(escapeHtmlNoBreaks(typeName)).append("</span>");
            } else {
                result.append(escapeHtmlNoBreaks(typeName));
            }
            result.append(" ").append(escapeHtmlNoBreaks(varName));

            if (parts[2] != null) {
                // Add padding to align '='
                String left = typeName + " " + varName;
                int padding = maxLeftLength - left.length();
                for (int p = 0; p < padding; p++) {
                    result.append(" ");
                }
                result.append(" = ").append(colorizeCodeNoBreaks(parts[2]));
            }
        }

        return result.toString();
    }

    /**
     * Format members with vertical alignment at '='.
     * Uses HTML non-breaking spaces ({@code &nbsp;}) for alignment.
     * Applies syntax highlighting colors to types and values.
     *
     * @param members List of String arrays: [type, name, defaultValue (or null)]
     */
    @NotNull
    public static String formatAlignedMembers(List<String[]> members) {
        if (members.isEmpty()) {
            return "";
        }

        // Find max length of "type name" portion (before '=')
        int maxLeftLength = 0;
        for (String[] parts : members) {
            int leftLength = parts[0].length() + 1 + parts[1].length(); // "type name"
            if (leftLength > maxLeftLength) {
                maxLeftLength = leftLength;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                result.append("<br/>");
            }

            String[] parts = members.get(i);
            String typeName = parts[0];
            String varName = parts[1];

            // Colorize the type name if it's a known type
            if (TYPE_NAMES.contains(typeName)) {
                result.append("<span style=\"color: ").append(COLOR_TYPE).append(";\">")
                        .append(escapeHtml(typeName)).append("</span>");
            } else {
                result.append(escapeHtml(typeName));
            }
            result.append(" ").append(escapeHtml(varName));

            if (parts[2] != null) {
                // Add padding to align '='
                String left = typeName + " " + varName;
                int padding = maxLeftLength - left.length();
                for (int p = 0; p < padding; p++) {
                    result.append("&nbsp;");
                }
                result.append(" = ").append(colorizeCode(parts[2]));
            }
        }

        return result.toString();
    }

    /**
     * Clean up block comment content by removing leading asterisks and normalizing indentation.
     */
    @NotNull
    public static String cleanBlockCommentContent(String content) {
        if (content == null) return "";

        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // Remove leading asterisks (common in multi-line comments)
            if (line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            if (!line.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append("\n");
                }
                result.append(line);
            }
        }

        return result.toString();
    }
}
