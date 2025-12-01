package cloud.kitelang.intellij.intentions;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.editor.KiteAutoImportService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Intention action to auto-import missing symbols in a Kite file.
 * Available via Alt+Enter when there are undefined symbols that could be imported.
 */
public class AutoImportIntention extends PsiElementBaseIntentionAction implements IntentionAction {

    @Override
    @NotNull
    public String getText() {
        return "Auto-import missing symbols";
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return "Kite";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        return file != null && file.getFileType() == KiteFileType.INSTANCE;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiFile file = element.getContainingFile();
        if (file == null) return;

        // Process the entire file for auto-imports
        KiteAutoImportService.processAutoImports(file, 0, file.getTextLength());
    }

    @Override
    public boolean startInWriteAction() {
        return false; // The service handles write action internally
    }
}
