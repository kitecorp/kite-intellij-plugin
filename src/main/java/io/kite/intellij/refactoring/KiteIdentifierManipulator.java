package io.kite.intellij.refactoring;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Element manipulator for Kite identifiers.
 * Handles renaming of identifier tokens by creating a new element with the updated text.
 */
public class KiteIdentifierManipulator extends AbstractElementManipulator<PsiElement> {

    @Override
    public @Nullable PsiElement handleContentChange(@NotNull PsiElement element,
                                                    @NotNull TextRange range,
                                                    String newContent) throws IncorrectOperationException {
        String oldText = element.getText();
        String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
        return handleContentChange(element, newText);
    }

    @Override
    public @Nullable PsiElement handleContentChange(@NotNull PsiElement element,
                                                    String newContent) throws IncorrectOperationException {
        IElementType elementType = element.getNode().getElementType();

        // Handle INTERP_SIMPLE tokens ($varname) - preserve the $ prefix
        if (elementType == KiteTokenTypes.INTERP_SIMPLE) {
            // INTERP_SIMPLE includes the $ prefix, so we need to preserve it
            if (!newContent.startsWith("$")) {
                newContent = "$" + newContent;
            }
        }

        // Create a dummy file with the new identifier
        // We use a simple variable declaration to get a proper identifier token
        String dummyCode = "var " + newContent + " = 0";

        var factory = PsiFileFactory.getInstance(element.getProject());
        var dummyFile = factory.createFileFromText("dummy.kite", KiteLanguage.INSTANCE, dummyCode);

        // Find the identifier in the dummy file
        PsiElement newElement = findIdentifierInTree(dummyFile, newContent);

        if (newElement != null) {
            return element.replace(newElement);
        }

        return element;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement(@NotNull PsiElement element) {
        IElementType elementType = element.getNode().getElementType();

        // For INTERP_SIMPLE tokens ($varname), the range should exclude the $ prefix
        if (elementType == KiteTokenTypes.INTERP_SIMPLE) {
            String text = element.getText();
            if (text.startsWith("$")) {
                return new TextRange(1, text.length());
            }
        }

        // For regular identifiers, return the full range
        return new TextRange(0, element.getTextLength());
    }

    /**
     * Find an identifier element in the PSI tree that matches the target text.
     */
    @Nullable
    private PsiElement findIdentifierInTree(@NotNull PsiElement root, @NotNull String targetText) {
        // Walk through all elements to find the identifier
        for (PsiElement child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();

            if (type == KiteTokenTypes.IDENTIFIER && child.getText().equals(targetText)) {
                return child;
            }

            // Check for INTERP_SIMPLE with the $ prefix
            if (type == KiteTokenTypes.INTERP_SIMPLE && child.getText().equals(targetText)) {
                return child;
            }

            // Recurse into children
            PsiElement found = findIdentifierInTree(child, targetText);
            if (found != null) {
                return found;
            }
        }

        return null;
    }
}
