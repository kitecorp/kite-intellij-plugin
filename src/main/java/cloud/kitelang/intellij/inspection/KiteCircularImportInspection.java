package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inspection that detects circular import dependencies.
 * Currently only detects direct self-imports (file importing itself).
 *
 * Note: Transitive circular import detection is complex and may cause
 * performance issues, so it's not implemented in this version.
 */
public class KiteCircularImportInspection extends KiteInspectionBase {

    @Override
    public @NotNull String getShortName() {
        return "KiteCircularImport";
    }

    @Override
    protected void checkKiteFile(@NotNull KiteFile file,
                                  @NotNull InspectionManager manager,
                                  boolean isOnTheFly,
                                  @NotNull List<ProblemDescriptor> problems) {
        // Get the current file name
        var currentFileName = getFileName(file);
        if (currentFileName == null) return;

        // Track which imports we've already warned about
        var warnedImports = new HashSet<String>();

        // Find all import statements in this file
        checkImportsRecursive(file, manager, isOnTheFly, problems, currentFileName, warnedImports);
    }

    private void checkImportsRecursive(PsiElement element,
                                        InspectionManager manager,
                                        boolean isOnTheFly,
                                        List<ProblemDescriptor> problems,
                                        String currentFileName,
                                        Set<String> warnedImports) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        if (type == KiteTokenTypes.IMPORT) {
            checkSingleImport(element, manager, isOnTheFly, problems, currentFileName, warnedImports);
        }

        var child = element.getFirstChild();
        while (child != null) {
            checkImportsRecursive(child, manager, isOnTheFly, problems, currentFileName, warnedImports);
            child = child.getNextSibling();
        }
    }

    private void checkSingleImport(PsiElement importKeyword,
                                    InspectionManager manager,
                                    boolean isOnTheFly,
                                    List<ProblemDescriptor> problems,
                                    String currentFileName,
                                    Set<String> warnedImports) {
        var stringLiteral = findImportPath(importKeyword);
        if (stringLiteral == null) return;

        var importPathText = stringLiteral.getText();
        // Remove quotes
        var importPath = importPathText.substring(1, importPathText.length() - 1);

        // Skip if already warned
        if (warnedImports.contains(importPath)) return;

        // Check for direct self-import
        if (isSelfImport(importPath, currentFileName)) {
            warnedImports.add(importPath);
            var problem = createWarning(
                    manager,
                    stringLiteral,
                    "Circular import: file imports itself",
                    isOnTheFly
            );
            problems.add(problem);
        }
    }

    /**
     * Check if the import path refers to the same file.
     */
    private boolean isSelfImport(String importPath, String currentFileName) {
        // Extract filename from import path
        var importFileName = getFileNameFromPath(importPath);
        return currentFileName.equals(importFileName);
    }

    private String getFileNameFromPath(String path) {
        if (path == null) return "";
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    @Nullable
    private PsiElement findImportPath(PsiElement importKeyword) {
        var current = importKeyword.getNextSibling();
        while (current != null) {
            if (current.getNode() != null) {
                var type = current.getNode().getElementType();
                if (type == KiteTokenTypes.STRING) {
                    return current;
                }
            }
            current = current.getNextSibling();
        }
        return null;
    }

    @Nullable
    private String getFileName(KiteFile file) {
        if (file == null) return null;
        var virtualFile = file.getVirtualFile();
        if (virtualFile == null) return null;
        return virtualFile.getName();
    }
}
