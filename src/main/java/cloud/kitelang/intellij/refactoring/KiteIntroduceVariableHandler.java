package cloud.kitelang.intellij.refactoring;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for Extract Variable refactoring in Kite.
 * Extracts a selected expression into a new variable declaration.
 */
public class KiteIntroduceVariableHandler implements RefactoringActionHandler {

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        // This is called from the IDE action - would typically show a dialog
        // For now, we'll use a default name
        invoke(project, editor, file, "extracted", false);
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
        // Not used for introduce variable
    }

    /**
     * Programmatic invocation with a specified variable name.
     * Used for testing and programmatic refactoring.
     *
     * @param project      the project
     * @param editor       the editor
     * @param file         the PSI file
     * @param variableName the name for the new variable
     * @param replaceAll   whether to replace all occurrences
     */
    public void invoke(@NotNull Project project,
                       @NotNull Editor editor,
                       @NotNull PsiFile file,
                       @NotNull String variableName,
                       boolean replaceAll) {
        SelectionModel selectionModel = editor.getSelectionModel();

        if (!selectionModel.hasSelection()) {
            return;
        }

        int startOffset = selectionModel.getSelectionStart();
        int endOffset = selectionModel.getSelectionEnd();
        String selectedText = selectionModel.getSelectedText();

        if (selectedText == null || selectedText.isBlank()) {
            return;
        }

        // Find the statement containing the selection to determine insertion point
        PsiElement elementAtStart = file.findElementAt(startOffset);
        if (elementAtStart == null) return;

        // Find the line/statement where we should insert the new variable
        PsiElement containingStatement = findContainingStatement(elementAtStart);
        if (containingStatement == null) {
            containingStatement = elementAtStart;
        }

        int insertionOffset = findInsertionOffset(containingStatement);

        // Get the indentation of the current line
        String indentation = getIndentation(editor, containingStatement);

        // Build the variable declaration
        String varDeclaration = indentation + "var " + variableName + " = " + selectedText + "\n";

        // Perform the refactoring
        WriteCommandAction.runWriteCommandAction(project, "Extract Variable", null, () -> {
            var document = editor.getDocument();

            if (replaceAll) {
                // Find and replace all occurrences
                List<TextRange> occurrences = findOccurrences(file, selectedText, startOffset, endOffset);

                // Replace from end to start to maintain offsets
                for (int i = occurrences.size() - 1; i >= 0; i--) {
                    TextRange range = occurrences.get(i);
                    document.replaceString(range.getStartOffset(), range.getEndOffset(), variableName);
                }
            } else {
                // Replace only the selected occurrence
                document.replaceString(startOffset, endOffset, variableName);
            }

            // Insert the variable declaration
            document.insertString(insertionOffset, varDeclaration);

            // Commit the document
            PsiDocumentManager.getInstance(project).commitDocument(document);
        });
    }

    /**
     * Find the containing statement (variable declaration, return statement, etc.)
     */
    @Nullable
    private PsiElement findContainingStatement(PsiElement element) {
        PsiElement current = element;

        while (current != null) {
            if (current.getNode() == null) {
                current = current.getParent();
                continue;
            }

            IElementType type = current.getNode().getElementType();

            // Check for statement types
            if (type == KiteElementTypes.VARIABLE_DECLARATION) {
                return current;
            }

            // Check if we're at a line that starts with 'var' or 'return'
            String text = current.getText();
            if (text != null) {
                String trimmed = text.trim();
                if (trimmed.startsWith("var ") || trimmed.startsWith("return ")) {
                    return current;
                }
            }

            // Check if parent is a function body - then current might be a statement
            PsiElement parent = current.getParent();
            if (parent != null && parent.getNode() != null) {
                IElementType parentType = parent.getNode().getElementType();
                if (parentType == KiteElementTypes.FUNCTION_DECLARATION) {
                    // We're inside a function, current might be a statement
                    return current;
                }
            }

            current = current.getParent();
        }

        return null;
    }

    /**
     * Find the offset where we should insert the new variable declaration.
     * This should be at the start of the line containing the statement.
     */
    private int findInsertionOffset(PsiElement statement) {
        int offset = statement.getTextRange().getStartOffset();
        PsiFile file = statement.getContainingFile();

        if (file == null) return offset;

        String fileText = file.getText();

        // Find the start of the line
        while (offset > 0 && fileText.charAt(offset - 1) != '\n') {
            offset--;
        }

        return offset;
    }

    /**
     * Get the indentation string for the given element's line.
     */
    private String getIndentation(Editor editor, PsiElement element) {
        var document = editor.getDocument();
        int lineNumber = document.getLineNumber(element.getTextRange().getStartOffset());
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String lineText = document.getText(new TextRange(lineStart, lineEnd));

        StringBuilder indent = new StringBuilder();
        for (char c : lineText.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }

        return indent.toString();
    }

    /**
     * Find all occurrences of the selected expression in the file.
     */
    private List<TextRange> findOccurrences(PsiFile file, String expression, int selectionStart, int selectionEnd) {
        List<TextRange> occurrences = new ArrayList<>();
        String fileText = file.getText();

        int index = 0;
        while ((index = fileText.indexOf(expression, index)) != -1) {
            // Check that this is a valid occurrence (not part of a larger identifier)
            boolean validStart = index == 0 || !Character.isLetterOrDigit(fileText.charAt(index - 1));
            boolean validEnd = index + expression.length() >= fileText.length() ||
                               !Character.isLetterOrDigit(fileText.charAt(index + expression.length()));

            // For expressions with operators, we don't need the boundary check
            boolean hasOperator = expression.contains("+") || expression.contains("-") ||
                                  expression.contains("*") || expression.contains("/") ||
                                  expression.contains("(") || expression.contains(".");

            if (hasOperator || (validStart && validEnd)) {
                occurrences.add(new TextRange(index, index + expression.length()));
            }

            index++;
        }

        return occurrences;
    }
}
