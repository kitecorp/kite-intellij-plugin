package io.kite.intellij.refactoring;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.rename.RenameHandler;
import io.kite.intellij.KiteFileType;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Custom rename handler for Kite language.
 * Handles rename refactoring for identifiers which are leaf PSI elements.
 */
public class KiteRenameHandler implements RenameHandler {

    @Override
    public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);

        if (editor == null || psiFile == null) {
            return false;
        }

        // Check if we're in a Kite file
        if (psiFile.getLanguage() != KiteLanguage.INSTANCE) {
            return false;
        }

        // Get element at caret
        PsiElement element = getTargetElement(editor, psiFile);
        return element != null && isRenameable(element);
    }

    @Override
    public boolean isRenaming(@NotNull DataContext dataContext) {
        return isAvailableOnDataContext(dataContext);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile, DataContext dataContext) {
        if (editor == null || psiFile == null) {
            return;
        }

        PsiElement element = getTargetElement(editor, psiFile);
        if (element == null || !isRenameable(element)) {
            return;
        }

        performRename(project, editor, psiFile, element);
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
        // This is called for non-editor based rename (e.g., from Project view)
        // For simplicity, we don't support this path
    }

    /**
     * Get the PSI element at the editor caret position.
     */
    @Nullable
    private PsiElement getTargetElement(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);

        // If we're right after an identifier, try to get the previous element
        if (element != null && isWhitespace(element.getNode().getElementType())) {
            if (offset > 0) {
                element = psiFile.findElementAt(offset - 1);
            }
        }

        return element;
    }

    /**
     * Check if the element can be renamed.
     */
    private boolean isRenameable(@NotNull PsiElement element) {
        if (element.getNode() == null) {
            return false;
        }

        IElementType type = element.getNode().getElementType();
        return type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.INTERP_SIMPLE;
    }

    /**
     * Perform the rename operation.
     */
    private void performRename(@NotNull Project project, @NotNull Editor editor,
                               @NotNull PsiFile psiFile, @NotNull PsiElement element) {
        String oldName = getIdentifierName(element);

        // Show rename dialog
        String newName = Messages.showInputDialog(
                project,
                "Rename '" + oldName + "' to:",
                "Rename",
                Messages.getQuestionIcon(),
                oldName,
                new KiteIdentifierInputValidator(project)
        );

        if (newName == null || newName.isEmpty() || newName.equals(oldName)) {
            return;
        }

        // Check if it's a valid identifier
        KiteNamesValidator validator = new KiteNamesValidator();
        if (!validator.isIdentifier(newName, project)) {
            Messages.showErrorDialog(project, "'" + newName + "' is not a valid identifier", "Rename Error");
            return;
        }
        if (validator.isKeyword(newName, project)) {
            Messages.showErrorDialog(project, "'" + newName + "' is a keyword and cannot be used as an identifier", "Rename Error");
            return;
        }

        // Determine if we're on a declaration or a usage
        PsiElement declaration = findDeclaration(element, psiFile);
        if (declaration == null) {
            // The element itself might be the declaration
            declaration = element;
        }

        // Collect all occurrences to rename
        List<PsiElement> occurrences = collectAllOccurrences(declaration, element, psiFile);

        // Perform the rename
        final String finalNewName = newName;
        final List<PsiElement> finalOccurrences = occurrences;

        WriteCommandAction.runWriteCommandAction(project, "Rename '" + oldName + "' to '" + newName + "'", null, () -> {
            renameOccurrences(finalOccurrences, finalNewName, project);
        });
    }

    /**
     * Get the identifier name from the element.
     */
    private String getIdentifierName(@NotNull PsiElement element) {
        String text = element.getText();
        IElementType type = element.getNode().getElementType();

        // For INTERP_SIMPLE tokens ($varname), extract just the variable name
        if (type == KiteTokenTypes.INTERP_SIMPLE && text.startsWith("$")) {
            return text.substring(1);
        }

        return text;
    }

    /**
     * Find the declaration for a given element.
     */
    @Nullable
    private PsiElement findDeclaration(@NotNull PsiElement element, @NotNull PsiFile psiFile) {
        String name = getIdentifierName(element);

        // Search for declarations in the file
        return findDeclarationRecursive(psiFile, name, element);
    }

    @Nullable
    private PsiElement findDeclarationRecursive(@NotNull PsiElement element, @NotNull String name, @NotNull PsiElement original) {
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && name.equals(getIdentifierName(nameElement))) {
                return nameElement;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclarationRecursive(child, name, original);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    /**
     * Collect all occurrences of the identifier to rename.
     */
    private List<PsiElement> collectAllOccurrences(@NotNull PsiElement declaration,
                                                    @NotNull PsiElement original,
                                                    @NotNull PsiFile psiFile) {
        String name = getIdentifierName(declaration);
        List<PsiElement> occurrences = new ArrayList<>();

        // Add the declaration itself
        occurrences.add(declaration);

        // Find all usages of this name
        collectOccurrencesRecursive(psiFile, name, declaration, occurrences);

        // Sort by offset (descending) so we can rename from end to start
        // This prevents offset shifts from affecting subsequent renames
        occurrences.sort((a, b) -> Integer.compare(b.getTextOffset(), a.getTextOffset()));

        return occurrences;
    }

    private void collectOccurrencesRecursive(@NotNull PsiElement element, @NotNull String name,
                                              @NotNull PsiElement declaration, @NotNull List<PsiElement> occurrences) {
        if (element.getNode() == null) {
            return;
        }

        IElementType type = element.getNode().getElementType();

        if (type == KiteTokenTypes.IDENTIFIER) {
            String elementName = element.getText();
            if (name.equals(elementName) && element != declaration && !occurrences.contains(element)) {
                occurrences.add(element);
            }
        } else if (type == KiteTokenTypes.INTERP_SIMPLE) {
            String text = element.getText();
            if (text.startsWith("$") && name.equals(text.substring(1)) && element != declaration && !occurrences.contains(element)) {
                occurrences.add(element);
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            collectOccurrencesRecursive(child, name, declaration, occurrences);
            child = child.getNextSibling();
        }
    }

    /**
     * Rename all occurrences.
     */
    private void renameOccurrences(@NotNull List<PsiElement> occurrences, @NotNull String newName, @NotNull Project project) {
        for (PsiElement element : occurrences) {
            if (!element.isValid()) {
                continue;
            }

            IElementType type = element.getNode().getElementType();

            // Create replacement element
            String replacementText = newName;
            if (type == KiteTokenTypes.INTERP_SIMPLE) {
                replacementText = "$" + newName;
            }

            // Create a dummy file to get a new identifier token
            PsiFileFactory factory = PsiFileFactory.getInstance(project);
            String dummyCode = "var " + replacementText + " = 0";
            PsiFile dummyFile = factory.createFileFromText("dummy.kite", KiteFileType.INSTANCE, dummyCode);

            // Find the identifier in the dummy file
            PsiElement newElement = findIdentifierInTree(dummyFile, replacementText);

            if (newElement != null) {
                try {
                    element.replace(newElement);
                } catch (Exception e) {
                    // Log error but continue with other occurrences
                    System.err.println("[KiteRename] Failed to rename element: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Find an identifier element in the PSI tree.
     */
    @Nullable
    private PsiElement findIdentifierInTree(@NotNull PsiElement root, @NotNull String targetText) {
        for (PsiElement child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if ((type == KiteTokenTypes.IDENTIFIER || type == KiteTokenTypes.INTERP_SIMPLE)
                    && child.getText().equals(targetText)) {
                    return child;
                }
            }

            // Recurse into children
            PsiElement found = findIdentifierInTree(child, targetText);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT;
    }

    @Nullable
    private PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child;
                }
                child = child.getNextSibling();
            }
        }

        PsiElement lastIdentifier = null;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier;
    }

    private boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Input validator for rename dialog.
     */
    private static class KiteIdentifierInputValidator implements com.intellij.openapi.ui.InputValidator {
        private final Project project;
        private final KiteNamesValidator namesValidator;

        KiteIdentifierInputValidator(Project project) {
            this.project = project;
            this.namesValidator = new KiteNamesValidator();
        }

        @Override
        public boolean checkInput(String inputString) {
            return inputString != null && !inputString.isEmpty() &&
                   namesValidator.isIdentifier(inputString, project) &&
                   !namesValidator.isKeyword(inputString, project);
        }

        @Override
        public boolean canClose(String inputString) {
            return checkInput(inputString);
        }
    }
}
