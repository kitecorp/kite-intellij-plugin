package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Inspection that detects circular import dependencies.
 * Detects both direct self-imports and transitive cycles (A→B→C→A).
 * <p>
 * Uses java.nio.file for file access to avoid triggering IntelliJ's
 * VFS recursive resolution which can cause stack overflow on circular imports.
 */
public class KiteCircularImportInspection extends KiteInspectionBase {

    // Pattern to extract import paths from file text
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(?:\\*|[\\w,\\s]+)\\s+from\\s+[\"']([^\"']+)[\"']",
            Pattern.MULTILINE
    );

    @Override
    public @NotNull String getShortName() {
        return "KiteCircularImport";
    }

    @Override
    protected void checkElement(@NotNull PsiElement element, @NotNull ProblemsHolder holder) {
        // Only run analysis once at the file level
        if (!(element instanceof PsiFile)) {
            return;
        }

        var file = (KiteFile) element;
        var currentVFile = file.getVirtualFile();
        if (currentVFile == null) return;

        var currentFilePath = currentVFile.getPath();

        // Track which imports we've already warned about
        var warnedImports = new HashSet<String>();

        // Find all import statements and check each one
        checkImportsRecursive(file, holder, currentFilePath, warnedImports);
    }

    private void checkImportsRecursive(PsiElement element,
                                       ProblemsHolder holder,
                                       String currentFilePath,
                                       Set<String> warnedImports) {
        if (element == null || element.getNode() == null) return;

        var type = element.getNode().getElementType();

        if (type == KiteTokenTypes.IMPORT) {
            checkSingleImport(element, holder, currentFilePath, warnedImports);
        }

        var child = element.getFirstChild();
        while (child != null) {
            checkImportsRecursive(child, holder, currentFilePath, warnedImports);
            child = child.getNextSibling();
        }
    }

    private void checkSingleImport(PsiElement importKeyword,
                                   ProblemsHolder holder,
                                   String currentFilePath,
                                   Set<String> warnedImports) {
        var importInfo = findImportPathInfo(importKeyword);
        if (importInfo == null) {
            return;
        }

        var importPath = importInfo.path;
        var elementToHighlight = importInfo.elementToHighlight;

        // Skip if already warned
        if (warnedImports.contains(importPath)) return;

        // Resolve the imported file path using java.nio (avoids VFS)
        var importedFilePath = resolveImportPath(importPath, currentFilePath);
        if (importedFilePath == null) {
            return;
        }

        // Check for direct self-import
        if (currentFilePath.equals(importedFilePath)) {
            warnedImports.add(importPath);
            registerWarning(holder, elementToHighlight, "Circular import: file imports itself");
            return;
        }

        // Check for transitive circular import using pure path-based DFS
        var cyclePath = detectCycleByPath(importedFilePath, currentFilePath, new LinkedHashSet<>());
        if (cyclePath != null) {
            warnedImports.add(importPath);
            var cycleDescription = buildCycleDescription(getFileNameFromPath(currentFilePath), cyclePath);
            registerWarning(holder, elementToHighlight, "Circular import detected: " + cycleDescription);
        }
    }

    /**
     * Resolve an import path to an absolute file path using java.nio.
     * This avoids triggering IntelliJ's VFS which can cause recursion issues.
     */
    @Nullable
    private String resolveImportPath(String importPath, String containingFilePath) {
        if (importPath == null || containingFilePath == null) return null;

        try {
            var containingDir = Paths.get(containingFilePath).getParent();
            if (containingDir == null) return null;

            var resolved = containingDir.resolve(importPath).normalize();
            if (Files.exists(resolved)) {
                return resolved.toString();
            }
        } catch (Exception e) {
            // Ignore resolution errors
        }
        return null;
    }

    /**
     * Detect a cycle starting from the given file back to the target file.
     * Uses java.nio.file for file access to avoid VFS recursion.
     *
     * @param currentFilePath The file path to start traversing from
     * @param targetPath      The path we're looking for (to detect a cycle back to origin)
     * @param visited         Set of visited file paths (preserves insertion order for cycle path)
     * @return The cycle path if found, null otherwise
     */
    @Nullable
    private List<String> detectCycleByPath(String currentFilePath, String targetPath, LinkedHashSet<String> visited) {
        if (currentFilePath == null) return null;

        // Found a cycle back to the target
        if (currentFilePath.equals(targetPath)) {
            return new ArrayList<>(visited);
        }

        // Already visited this file in current path - no cycle to target here
        if (visited.contains(currentFilePath)) {
            return null;
        }

        // Add current file to visited path
        visited.add(currentFilePath);

        // Get import paths from file using java.nio
        var importPaths = extractImportPathsFromFile(currentFilePath);

        for (var importPath : importPaths) {
            // Resolve import path
            var importedFilePath = resolveImportPath(importPath, currentFilePath);
            if (importedFilePath != null) {
                var cyclePath = detectCycleByPath(importedFilePath, targetPath, visited);
                if (cyclePath != null) {
                    return cyclePath;
                }
            }
        }

        // Remove from path when backtracking
        visited.remove(currentFilePath);
        return null;
    }

    /**
     * Extract import paths from file using java.nio (bypasses IntelliJ VFS).
     */
    private List<String> extractImportPathsFromFile(String filePath) {
        var paths = new ArrayList<String>();
        try {
            var text = Files.readString(Path.of(filePath));
            var matcher = IMPORT_PATTERN.matcher(text);
            while (matcher.find()) {
                paths.add(matcher.group(1));
            }
        } catch (IOException e) {
            // Ignore read errors
        }
        return paths;
    }

    /**
     * Build a human-readable description of the cycle.
     */
    private String buildCycleDescription(String originFileName, List<String> cyclePath) {
        var sb = new StringBuilder();
        sb.append(originFileName);

        for (var path : cyclePath) {
            sb.append(" → ");
            sb.append(getFileNameFromPath(path));
        }

        sb.append(" → ");
        sb.append(originFileName);

        return sb.toString();
    }

    /**
     * Find the import path and proper element to highlight after the IMPORT keyword.
     * Returns both the path string and the STRING_TEXT element to highlight (not just the opening quote).
     */
    @Nullable
    private ImportPathInfo findImportPathInfo(PsiElement importKeyword) {
        boolean foundFrom = false;
        var current = importKeyword.getNextSibling();

        while (current != null) {
            if (current.getNode() != null) {
                var type = current.getNode().getElementType();

                // Skip whitespace
                if (type == KiteTokenTypes.WHITESPACE || type == KiteTokenTypes.NL ||
                    type == KiteTokenTypes.NEWLINE || type == com.intellij.psi.TokenType.WHITE_SPACE) {
                    current = current.getNextSibling();
                    continue;
                }

                // Look for FROM keyword
                if (type == KiteTokenTypes.FROM) {
                    foundFrom = true;
                    current = current.getNextSibling();
                    continue;
                }

                // After FROM, look for string tokens
                if (foundFrom) {
                    // Case 1: Full STRING or SINGLE_STRING token
                    if (type == KiteTokenTypes.STRING || type == KiteTokenTypes.SINGLE_STRING) {
                        var text = current.getText();
                        if (text.length() >= 2) {
                            var path = text.substring(1, text.length() - 1);
                            return new ImportPathInfo(path, current);
                        }
                    }
                    // Case 2: DQUOTE token - need to find STRING_TEXT sibling
                    if (type == KiteTokenTypes.DQUOTE) {
                        // Look for STRING_TEXT which contains the actual path
                        var sibling = current.getNextSibling();
                        while (sibling != null && sibling.getNode() != null) {
                            var sibType = sibling.getNode().getElementType();
                            if (sibType == KiteTokenTypes.STRING_TEXT) {
                                var path = sibling.getText();
                                // Return STRING_TEXT as the element to highlight (the file name)
                                return new ImportPathInfo(path, sibling);
                            } else if (sibType == KiteTokenTypes.STRING_DQUOTE ||
                                       sibType == KiteTokenTypes.NL ||
                                       sibType == KiteTokenTypes.NEWLINE) {
                                break;
                            }
                            sibling = sibling.getNextSibling();
                        }
                    }
                }
            }
            current = current.getNextSibling();
        }
        return null;
    }

    private String getFileNameFromPath(String path) {
        if (path == null) return "";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Helper class to hold import path info including the element to highlight.
     */
    private record ImportPathInfo(String path, PsiElement elementToHighlight) {
    }
}
