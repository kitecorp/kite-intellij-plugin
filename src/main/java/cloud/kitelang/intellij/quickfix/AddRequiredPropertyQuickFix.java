package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that adds a missing required property to a resource or component block.
 * The property is added with a default placeholder value based on its type.
 */
public class AddRequiredPropertyQuickFix implements LocalQuickFix {

    private final String propertyName;
    private final String propertyType;

    public AddRequiredPropertyQuickFix(@NotNull String propertyName, @NotNull String propertyType) {
        this.propertyName = propertyName;
        this.propertyType = propertyType;
    }

    @Override
    @NotNull
    public String getName() {
        return "Add missing property '" + propertyName + "'";
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return "Add missing property";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) return;

        // Find the containing resource/component declaration
        PsiElement resourceDecl = findResourceDeclaration(element);
        if (resourceDecl == null) return;

        PsiFile file = element.getContainingFile();
        if (file == null) return;

        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return;

        // Find the closing brace of the resource block
        int insertOffset = findInsertionOffset(resourceDecl, document);
        if (insertOffset < 0) return;

        // Generate the property assignment with default value
        String defaultValue = getDefaultValueForType(propertyType);
        String indentation = detectIndentation(resourceDecl, document);
        String propertyLine = indentation + propertyName + " = " + defaultValue + "\n";

        // Insert the new property
        document.insertString(insertOffset, propertyLine);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    /**
     * Find the containing resource or component declaration.
     */
    private PsiElement findResourceDeclaration(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current.getNode() != null) {
                var type = current.getNode().getElementType();
                if (type == cloud.kitelang.intellij.psi.KiteElementTypes.RESOURCE_DECLARATION ||
                    type == cloud.kitelang.intellij.psi.KiteElementTypes.COMPONENT_DECLARATION) {
                    return current;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Find the offset where the new property should be inserted.
     * This is typically just before the closing brace.
     */
    private int findInsertionOffset(PsiElement resourceDecl, Document document) {
        // Find the closing brace
        PsiElement child = resourceDecl.getFirstChild();

        while (child != null) {
            if (child.getNode() != null &&
                child.getNode().getElementType() == KiteTokenTypes.RBRACE) {
                // Insert before the closing brace
                int braceOffset = child.getTextRange().getStartOffset();

                // Get the text before the brace to check if we need a newline
                String text = document.getText();
                int lineStart = braceOffset;
                while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
                    lineStart--;
                }

                // If there's content on the same line as the brace, insert before the brace
                // Otherwise, insert at the line start (which is right after the last property)
                String lineBeforeBrace = text.substring(lineStart, braceOffset).trim();
                if (lineBeforeBrace.isEmpty()) {
                    return lineStart;
                } else {
                    return braceOffset;
                }
            }
            child = child.getNextSibling();
        }

        return -1;
    }

    /**
     * Detect the indentation used inside the resource block.
     */
    private String detectIndentation(PsiElement resourceDecl, Document document) {
        // Find the opening brace
        PsiElement child = resourceDecl.getFirstChild();
        while (child != null) {
            if (child.getNode() != null &&
                child.getNode().getElementType() == KiteTokenTypes.LBRACE) {
                int braceOffset = child.getTextRange().getStartOffset();
                String text = document.getText();

                // Find the start of the line containing the brace
                int lineStart = braceOffset;
                while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
                    lineStart--;
                }

                // Count leading spaces on that line and add indentation
                StringBuilder indent = new StringBuilder();
                for (int i = lineStart; i < braceOffset && i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == ' ' || c == '\t') {
                        indent.append(c);
                    } else {
                        break;
                    }
                }

                // Add one more level of indentation (4 spaces or 1 tab)
                return indent + "    ";
            }
            child = child.getNextSibling();
        }

        return "    "; // Default indentation
    }

    /**
     * Get a default placeholder value based on the property type.
     */
    private String getDefaultValueForType(String type) {
        if (type == null) {
            return "\"\"";
        }

        return switch (type.toLowerCase()) {
            case "string" -> "\"\"";
            case "number" -> "0";
            case "boolean" -> "false";
            case "any" -> "null";
            default -> {
                // For array types like "string[]"
                if (type.endsWith("[]")) {
                    yield "[]";
                }
                // For custom types, use empty object
                yield "{}";
            }
        };
    }
}
