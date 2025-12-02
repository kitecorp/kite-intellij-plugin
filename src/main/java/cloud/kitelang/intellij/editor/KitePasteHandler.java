package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteFileType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Paste handler that automatically imports missing symbols after pasting code.
 */
public class KitePasteHandler extends EditorActionHandler {

    private static final Logger LOG = Logger.getInstance(KitePasteHandler.class);
    private final EditorActionHandler originalHandler;

    public KitePasteHandler(EditorActionHandler originalHandler) {
        this.originalHandler = originalHandler;
        LOG.info("KitePasteHandler initialized with original handler: " + originalHandler);
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        LOG.info("KitePasteHandler.doExecute called");

        Project project = editor.getProject();

        // Get file before paste
        PsiFile psiFile = project != null
                ? PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument())
                : null;

        // Check if this is a Kite file
        boolean isKiteFile = psiFile != null && psiFile.getFileType() == KiteFileType.INSTANCE;
        LOG.info("Is Kite file: " + isKiteFile + ", file: " + (psiFile != null ? psiFile.getName() : "null"));

        // Remember position before paste
        int offsetBefore = editor.getCaretModel().getOffset();

        // Execute the original paste
        if (originalHandler != null) {
            originalHandler.execute(editor, caret, dataContext);
        }

        // If it's a Kite file, process auto-imports
        if (isKiteFile && project != null) {
            int offsetAfter = editor.getCaretModel().getOffset();
            LOG.info("Paste region: " + offsetBefore + " to " + offsetAfter);

            // Commit document changes
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

            // Re-get the file after commit
            PsiFile refreshedFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (refreshedFile != null) {
                // Process auto-imports for the pasted region
                LOG.info("Processing auto-imports for file: " + refreshedFile.getName());
                KiteAutoImportService.processAutoImports(refreshedFile, offsetBefore, offsetAfter);
            }
        }
    }
}
