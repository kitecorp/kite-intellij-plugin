package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteFileType;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quick-fix that removes unused import statements or individual unused symbols from imports.
 * <p>
 * Supports two modes:
 * 1. Remove entire import line (when all symbols are unused or for wildcard imports)
 * 2. Remove single symbol from multi-symbol import (e.g., "import a, b from x" -> "import a from x")
 */
public class RemoveUnusedImportQuickFix extends BaseIntentionAction {

    private final String symbolName;      // null for whole import removal
    private final int importLineStart;    // Start offset of the import line
    private final int importLineEnd;      // End offset of the import line (including newline)
    private final boolean removeWholeLine;

    /**
     * Create a quick fix to remove an entire import line.
     */
    public RemoveUnusedImportQuickFix(int importLineStart, int importLineEnd) {
        this.symbolName = null;
        this.importLineStart = importLineStart;
        this.importLineEnd = importLineEnd;
        this.removeWholeLine = true;
    }

    /**
     * Create a quick fix to remove a single symbol from an import.
     */
    public RemoveUnusedImportQuickFix(@NotNull String symbolName, int importLineStart, int importLineEnd) {
        this.symbolName = symbolName;
        this.importLineStart = importLineStart;
        this.importLineEnd = importLineEnd;
        this.removeWholeLine = false;
    }

    @Override
    @NotNull
    public String getText() {
        if (removeWholeLine) {
            return "Remove unused import";
        } else {
            return "Remove unused import '" + symbolName + "'";
        }
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return "Remove unused import";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file != null && file.getFileType() == KiteFileType.INSTANCE;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (file == null) return;

        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return;

        String text = document.getText();

        // Validate offsets are still valid
        if (importLineStart < 0 || importLineEnd > text.length() || importLineStart >= importLineEnd) {
            return;
        }

        if (removeWholeLine) {
            removeEntireImportLine(document, project, text);
        } else {
            removeSymbolFromImport(document, project, text);
        }
    }

    /**
     * Remove the entire import line, including trailing newline.
     */
    private void removeEntireImportLine(Document document, Project project, String text) {
        int start = importLineStart;
        int end = importLineEnd;

        // Extend to include trailing newline if present
        if (end < text.length() && text.charAt(end) == '\n') {
            end++;
        }
        // Also handle leading whitespace on the line (for clean deletion)
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }

        document.deleteString(start, end);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    /**
     * Remove a single symbol from a multi-symbol import.
     * e.g., "import a, b, c from x" -> "import a, c from x" (if removing b)
     */
    private void removeSymbolFromImport(Document document, Project project, String text) {
        String importText = text.substring(importLineStart, importLineEnd);

        // Pattern to match import statement parts
        // import symbolA, symbolB, symbolC from "path"
        Pattern importPattern = Pattern.compile(
                "^(\\s*import\\s+)([\\w,\\s]+)(\\s+from\\s+.*)$"
        );

        Matcher matcher = importPattern.matcher(importText);
        if (!matcher.matches()) {
            // Fall back to removing entire line if pattern doesn't match
            removeEntireImportLine(document, project, text);
            return;
        }

        String prefix = matcher.group(1);     // "import "
        String symbolsPart = matcher.group(2); // "a, b, c"
        String suffix = matcher.group(3);      // " from \"path\""

        // Parse and filter symbols
        String[] symbols = symbolsPart.split("\\s*,\\s*");
        StringBuilder newSymbols = new StringBuilder();
        boolean first = true;

        for (String symbol : symbols) {
            String trimmed = symbol.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(symbolName)) {
                if (!first) {
                    newSymbols.append(", ");
                }
                newSymbols.append(trimmed);
                first = false;
            }
        }

        if (newSymbols.length() == 0) {
            // All symbols removed, delete the entire line
            removeEntireImportLine(document, project, text);
        } else {
            // Replace the import with updated symbols
            String newImport = prefix + newSymbols + suffix;
            document.replaceString(importLineStart, importLineEnd, newImport);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }
}
