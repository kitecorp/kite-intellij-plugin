package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Factory for creating highlight usages handlers for Kite language.
 * When clicking on an identifier, highlights all other occurrences of the same identifier in the file.
 */
public class KiteHighlightUsagesHandlerFactory implements HighlightUsagesHandlerFactory {

    @Override
    public @Nullable HighlightUsagesHandlerBase<?> createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file) {
        // Only handle Kite files
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);

        if (element == null) {
            return null;
        }

        IElementType elementType = element.getNode().getElementType();

        // Only handle IDENTIFIER tokens
        if (elementType != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        return new KiteHighlightUsagesHandler(editor, file, element);
    }

    /**
     * Handler that highlights all occurrences of an identifier in the file.
     */
    private static class KiteHighlightUsagesHandler extends HighlightUsagesHandlerBase<PsiElement> {
        private final PsiElement targetElement;
        private final String targetName;

        KiteHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
            super(editor, file);
            this.targetElement = target;
            this.targetName = target.getText();
        }

        @Override
        public @NotNull List<PsiElement> getTargets() {
            return Collections.singletonList(targetElement);
        }

        @Override
        protected void selectTargets(@NotNull List<? extends PsiElement> targets, @NotNull Consumer<? super List<? extends PsiElement>> selectionConsumer) {
            selectionConsumer.consume(targets);
        }

        @Override
        public void computeUsages(@NotNull List<? extends PsiElement> targets) {
            // Find all identifiers with the same name in the file
            List<PsiElement> usages = new ArrayList<>();
            findIdentifiersRecursive(myFile, targetName, usages);

            // Add all usages as read usages (highlighted)
            for (PsiElement usage : usages) {
                addOccurrence(usage);
            }
        }

        /**
         * Recursively find all identifiers with the given name in the PSI tree.
         */
        private void findIdentifiersRecursive(PsiElement element, String name, List<PsiElement> results) {
            if (element.getNode() == null) {
                return;
            }

            IElementType type = element.getNode().getElementType();

            // Check if this is an identifier with the target name
            if (type == KiteTokenTypes.IDENTIFIER && name.equals(element.getText())) {
                results.add(element);
            }

            // Recurse into children
            PsiElement child = element.getFirstChild();
            while (child != null) {
                findIdentifiersRecursive(child, name, results);
                child = child.getNextSibling();
            }
        }
    }
}
