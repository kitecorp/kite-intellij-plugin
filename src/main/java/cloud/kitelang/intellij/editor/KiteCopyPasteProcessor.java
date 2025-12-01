package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteFileType;
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Collections;
import java.util.List;

/**
 * Copy/paste processor that handles auto-import after pasting code.
 * This processor runs after the paste is complete and adds missing imports.
 */
public class KiteCopyPasteProcessor extends CopyPastePostProcessor<TextBlockTransferableData> {

    private static final Logger LOG = Logger.getInstance(KiteCopyPasteProcessor.class);

    /**
     * Marker class to identify our transferable data.
     */
    private static class KitePasteMarker implements TextBlockTransferableData {
        private static final DataFlavor FLAVOR = new DataFlavor(KitePasteMarker.class, "Kite paste marker");

        @Override
        public DataFlavor getFlavor() {
            return FLAVOR;
        }

        @Override
        public int getOffsetCount() {
            return 0;
        }

        @Override
        public int getOffsets(int[] offsets, int index) {
            return index;
        }

        @Override
        public int setOffsets(int[] offsets, int index) {
            return index;
        }
    }

    @Override
    public @NotNull List<TextBlockTransferableData> collectTransferableData(
            @NotNull PsiFile file,
            @NotNull Editor editor,
            int @NotNull [] startOffsets,
            int @NotNull [] endOffsets) {
        // Return a marker so that processTransferableData gets called
        if (file.getFileType() == KiteFileType.INSTANCE) {
            return Collections.singletonList(new KitePasteMarker());
        }
        return Collections.emptyList();
    }

    @Override
    public @NotNull List<TextBlockTransferableData> extractTransferableData(@NotNull Transferable content) {
        // Always return a marker for Kite files
        // This ensures processTransferableData is called even when pasting from other sources
        return Collections.singletonList(new KitePasteMarker());
    }

    @Override
    public void processTransferableData(
            @NotNull Project project,
            @NotNull Editor editor,
            @NotNull RangeMarker bounds,
            int caretOffset,
            @NotNull Ref<? super Boolean> indented,
            @NotNull List<? extends TextBlockTransferableData> values) {

        LOG.info("KiteCopyPasteProcessor.processTransferableData called");

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null || psiFile.getFileType() != KiteFileType.INSTANCE) {
            LOG.info("Not a Kite file, skipping");
            return;
        }

        int startOffset = bounds.getStartOffset();
        int endOffset = bounds.getEndOffset();
        LOG.info("Processing paste in " + psiFile.getName() + " from " + startOffset + " to " + endOffset);

        // Schedule auto-import to run in background to avoid EDT slow operations
        // Use ReadAction.nonBlocking to perform heavy work on a background thread
        ReadAction.nonBlocking(() -> {
                    if (!psiFile.isValid()) return null;

                    // Process auto-imports - the heavy work is done here
                    KiteAutoImportService.processAutoImports(psiFile, startOffset, endOffset);
                    return null;
                })
                .inSmartMode(project)
                .submit(NonUrgentExecutor.getInstance());
    }
}
