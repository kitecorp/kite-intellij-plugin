package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Quick-fix that adds an import statement for an undefined reference.
 * When a symbol is not found in the current file or its imports,
 * this fix searches the project for files containing the symbol
 * and adds the appropriate import statement.
 * <p>
 * Extends BaseIntentionAction as required by IntelliJ Platform for annotator quick-fixes.
 */
public class AddImportQuickFix extends BaseIntentionAction implements HighPriorityAction {

    private final String symbolName;
    private final String importPath;

    public AddImportQuickFix(@NotNull String symbolName, @NotNull String importPath) {
        this.symbolName = symbolName;
        this.importPath = importPath;
    }

    @Override
    @NotNull
    public String getText() {
        return "Import '" + symbolName + "' from \"" + importPath + "\"";
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return "Add import";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file != null && file.getFileType() == KiteFileType.INSTANCE;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (file == null) return;

        com.intellij.openapi.editor.Document document =
                com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return;

        // Find existing imports from this file path
        List<ExistingImportInfo> allImportsFromPath = findAllImportsForPath(file, importPath);

        // Check if there's a wildcard import - if so, nothing to do
        for (ExistingImportInfo existingImport : allImportsFromPath) {
            if (existingImport.isWildcard) {
                return; // Already has wildcard import, symbol should be available
            }
            // Check if symbol is already imported
            if (existingImport.symbols.contains(symbolName)) {
                return; // Symbol already imported
            }
        }

        if (!allImportsFromPath.isEmpty()) {
            // Add to the first existing import: insert ", symbolName" before " from"
            ExistingImportInfo firstImport = allImportsFromPath.get(0);
            String insertion = ", " + symbolName;
            document.insertString(firstImport.symbolsEndOffset, insertion);
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document);
        } else {
            // No existing import - create new named import: import SymbolName from "file"
            String importStatement = "import " + symbolName + " from \"" + importPath + "\"\n";
            int insertOffset = findImportInsertionOffset(file);
            document.insertString(insertOffset, importStatement);
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }

    /**
     * Information about an existing import statement.
     */
    private static class ExistingImportInfo {
        final int startOffset;        // Start of the import statement
        final int endOffset;          // End of the import statement
        final int symbolsEndOffset;   // End of the symbols list (before "from")
        final boolean isWildcard;     // True if "import *"
        final List<String> symbols;   // List of imported symbols (empty if wildcard)

        ExistingImportInfo(int startOffset, int endOffset, int symbolsEndOffset, boolean isWildcard, List<String> symbols) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.symbolsEndOffset = symbolsEndOffset;
            this.isWildcard = isWildcard;
            this.symbols = symbols;
        }
    }

    // Pattern for named imports: import X from "path" or import X, Y, Z from "path"
    // Group 1: symbols (e.g., "formatName" or "a, b, c")
    // Group 2: path (e.g., "common.kite")
    private static final java.util.regex.Pattern NAMED_IMPORT_PATTERN = java.util.regex.Pattern.compile(
            "^(\\s*import\\s+)([\\w,\\s]+)(\\s+from\\s+[\"'])([^\"']+)([\"'])",
            java.util.regex.Pattern.MULTILINE
    );

    /**
     * Find ALL existing import statements for the given path using text-based parsing.
     * This is more reliable than PSI-based parsing when the PSI structure varies.
     * Returns an empty list if no imports from this path exist.
     */
    @NotNull
    private List<ExistingImportInfo> findAllImportsForPath(@NotNull PsiFile file, @NotNull String targetPath) {
        List<ExistingImportInfo> imports = new ArrayList<>();
        String text = file.getText();

        java.util.regex.Matcher matcher = NAMED_IMPORT_PATTERN.matcher(text);
        while (matcher.find()) {
            String importPath = matcher.group(4); // The path inside quotes

            if (targetPath.equals(importPath)) {
                String symbolsStr = matcher.group(2).trim(); // The symbols
                boolean isWildcard = "*".equals(symbolsStr);

                List<String> symbols = new ArrayList<>();
                if (!isWildcard) {
                    // Parse comma-separated symbols
                    for (String symbol : symbolsStr.split(",")) {
                        String trimmed = symbol.trim();
                        if (!trimmed.isEmpty()) {
                            symbols.add(trimmed);
                        }
                    }
                }

                int startOffset = matcher.start();
                int endOffset = matcher.end();

                // Calculate symbolsEndOffset - position after last symbol, before " from"
                int symbolsEndOffset = matcher.start(3); // Start of " from"

                imports.add(new ExistingImportInfo(startOffset, endOffset, symbolsEndOffset, isWildcard, symbols));
            }
        }

        // Sort by startOffset to ensure correct order
        imports.sort((a, b) -> Integer.compare(a.startOffset, b.startOffset));

        return imports;
    }

    /**
     * Extract import path by scanning forward from the IMPORT keyword token.
     */
    @Nullable
    private String extractImportPathFromToken(@NotNull PsiElement importToken) {
        boolean foundFrom = false;
        PsiElement sibling = importToken.getNextSibling();

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType type = sibling.getNode().getElementType();
            String text = sibling.getText();

            // Skip whitespace
            if (type == com.intellij.psi.TokenType.WHITE_SPACE ||
                type == KiteTokenTypes.WHITESPACE) {
                sibling = sibling.getNextSibling();
                continue;
            }

            // Look for "from" keyword
            if (type == KiteTokenTypes.FROM || "from".equals(text)) {
                foundFrom = true;
                sibling = sibling.getNextSibling();
                continue;
            }

            // After FROM, look for the string literal
            if (foundFrom) {
                if (text.length() >= 2 &&
                    ((text.startsWith("\"") && text.endsWith("\"")) ||
                     (text.startsWith("'") && text.endsWith("'")))) {
                    return text.substring(1, text.length() - 1);
                }
            }

            // Stop at newline
            if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                break;
            }

            sibling = sibling.getNextSibling();
        }

        return null;
    }

    /**
     * Analyze an import from a raw IMPORT token.
     */
    @NotNull
    private ExistingImportInfo analyzeImportFromToken(@NotNull PsiElement importToken) {
        int startOffset = importToken.getTextRange().getStartOffset();
        int endOffset = startOffset;
        int symbolsEndOffset = startOffset;
        boolean isWildcard = false;
        List<String> symbols = new ArrayList<>();

        PsiElement sibling = importToken.getNextSibling();
        PsiElement lastSymbolElement = null;
        boolean foundFrom = false;

        while (sibling != null) {
            if (sibling.getNode() == null) {
                sibling = sibling.getNextSibling();
                continue;
            }

            IElementType type = sibling.getNode().getElementType();

            // Skip whitespace
            if (type == com.intellij.psi.TokenType.WHITE_SPACE ||
                type == KiteTokenTypes.WHITESPACE) {
                sibling = sibling.getNextSibling();
                continue;
            }

            // Check for wildcard
            if (!foundFrom && type == KiteTokenTypes.MULTIPLY) {
                isWildcard = true;
                lastSymbolElement = sibling;
            }

            // Collect symbol names before FROM
            if (!foundFrom && type == KiteTokenTypes.IDENTIFIER) {
                symbols.add(sibling.getText());
                lastSymbolElement = sibling;
            }

            // Found FROM - record where symbols end
            if (type == KiteTokenTypes.FROM) {
                foundFrom = true;
                if (lastSymbolElement != null) {
                    symbolsEndOffset = lastSymbolElement.getTextRange().getEndOffset();
                }
            }

            // Stop at newline and record end offset
            if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                endOffset = sibling.getTextRange().getEndOffset();
                break;
            }

            // Track end position for elements on the import line
            endOffset = sibling.getTextRange().getEndOffset();
            sibling = sibling.getNextSibling();
        }

        return new ExistingImportInfo(startOffset, endOffset, symbolsEndOffset, isWildcard, symbols);
    }

    /**
     * Extract the path from an import statement.
     */
    @Nullable
    private String extractImportPath(@NotNull PsiElement importStatement) {
        boolean foundFrom = false;
        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.FROM) {
                foundFrom = true;
            } else if (foundFrom) {
                String text = child.getText();
                // Check for quoted string
                if (text.length() >= 2 &&
                    ((text.startsWith("\"") && text.endsWith("\"")) ||
                     (text.startsWith("'") && text.endsWith("'")))) {
                    return text.substring(1, text.length() - 1);
                }
                // Check for STRING token types
                if (childType == KiteTokenTypes.STRING || childType == KiteTokenTypes.SINGLE_STRING ||
                    childType == KiteTokenTypes.DQUOTE) {
                    return extractStringContent(child);
                }
            }
        }
        return null;
    }

    /**
     * Extract string content from a string element.
     */
    @NotNull
    private String extractStringContent(@NotNull PsiElement element) {
        String text = element.getText();
        if (text.length() >= 2 &&
            ((text.startsWith("\"") && text.endsWith("\"")) ||
             (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        // Check children for STRING_TEXT
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.STRING_TEXT) {
                return child.getText();
            }
        }
        return text;
    }

    /**
     * Analyze an import statement to extract its structure.
     */
    @NotNull
    private ExistingImportInfo analyzeImportStatement(@NotNull PsiElement importStatement) {
        int startOffset = importStatement.getTextRange().getStartOffset();
        int endOffset = importStatement.getTextRange().getEndOffset();
        int symbolsEndOffset = startOffset;
        boolean isWildcard = false;
        List<String> symbols = new ArrayList<>();

        boolean foundImport = false;
        PsiElement lastSymbolElement = null;

        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IMPORT) {
                foundImport = true;
            } else if (foundImport && childType == KiteTokenTypes.MULTIPLY) {
                isWildcard = true;
                lastSymbolElement = child;
            } else if (foundImport && childType == KiteTokenTypes.IDENTIFIER) {
                symbols.add(child.getText());
                lastSymbolElement = child;
            } else if (childType == KiteTokenTypes.FROM) {
                // Symbols end before "from"
                if (lastSymbolElement != null) {
                    symbolsEndOffset = lastSymbolElement.getTextRange().getEndOffset();
                }
                break;
            }
        }

        return new ExistingImportInfo(startOffset, endOffset, symbolsEndOffset, isWildcard, symbols);
    }

    /**
     * Find the offset where a new import statement should be inserted.
     * Returns offset after the last import statement, or 0 if no imports exist.
     */
    private int findImportInsertionOffset(@NotNull PsiFile file) {
        int lastImportEnd = 0;

        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;

            IElementType type = child.getNode().getElementType();

            // Check for import statements
            if (type == KiteElementTypes.IMPORT_STATEMENT) {
                lastImportEnd = child.getTextRange().getEndOffset();
                // Skip past any trailing newlines
                PsiElement next = child.getNextSibling();
                while (next != null && isWhitespaceOrNewline(next)) {
                    if (next.getText().contains("\n")) {
                        lastImportEnd = next.getTextRange().getEndOffset();
                        break;
                    }
                    next = next.getNextSibling();
                }
            }

            // Also check for IMPORT keyword in case the PSI structure is different
            if (type == KiteTokenTypes.IMPORT) {
                // Find the end of this import line
                PsiElement current = child;
                while (current != null) {
                    if (current.getNode() != null && current.getNode().getElementType() == KiteTokenTypes.NL) {
                        lastImportEnd = current.getTextRange().getEndOffset();
                        break;
                    }
                    current = current.getNextSibling();
                }
            }
        }

        return lastImportEnd;
    }

    private boolean isWhitespaceOrNewline(@NotNull PsiElement element) {
        if (element.getNode() == null) return false;
        IElementType type = element.getNode().getElementType();
        return type == com.intellij.psi.TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }

    /**
     * Search for files in the project that declare the given symbol.
     * Returns a list of import paths (relative to the current file).
     * Only returns files that are NOT already fully imported (wildcard imports).
     * Files with named imports can still be suggested if the symbol isn't already imported.
     */
    @NotNull
    public static List<ImportCandidate> findImportCandidates(@NotNull String symbolName, @NotNull PsiFile currentFile) {
        List<ImportCandidate> candidates = new ArrayList<>();
        Project project = currentFile.getProject();
        VirtualFile currentVFile = currentFile.getVirtualFile();
        if (currentVFile == null) return candidates;

        Set<String> visitedPaths = new HashSet<>();
        String currentPath = currentVFile.getPath();
        visitedPaths.add(currentPath);

        // Get files with wildcard imports (import * from "file") - these are fully imported
        Set<String> wildcardImportedPaths = new HashSet<>();
        // Get named imports mapping: file path -> set of imported symbol names
        java.util.Map<String, Set<String>> namedImports = new java.util.HashMap<>();
        collectImportInfo(currentFile, wildcardImportedPaths, namedImports);

        // Search all .kite files in the project
        Collection<VirtualFile> kiteFiles = FileTypeIndex.getFiles(
                KiteFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
        );

        for (VirtualFile vFile : kiteFiles) {
            String filePath = vFile.getPath();

            // Skip current file
            if (visitedPaths.contains(filePath)) continue;

            // Skip files with wildcard imports - all symbols are already available
            if (wildcardImportedPaths.contains(filePath)) continue;

            PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
            if (psiFile == null) continue;

            // Check if this file declares the symbol
            if (fileDeclaresSymbol(psiFile, symbolName)) {
                String relativePath = calculateRelativePath(currentVFile, vFile);
                if (relativePath != null) {
                    // Check if this symbol is already named-imported from this file
                    // Check both by relative path and absolute path
                    Set<String> importedSymbols = namedImports.get(relativePath);
                    if (importedSymbols == null) {
                        importedSymbols = namedImports.get(filePath); // Try absolute path
                    }
                    if (importedSymbols != null && importedSymbols.contains(symbolName)) {
                        continue; // Symbol already imported from this file
                    }
                    candidates.add(new ImportCandidate(symbolName, relativePath, vFile.getPath()));
                }
            }
        }

        return candidates;
    }

    /**
     * Collect import information: wildcard imports and named imports.
     *
     * @param file                  The file to analyze
     * @param wildcardImportedPaths Output: set of file paths that have wildcard imports
     * @param namedImports          Output: map of import path -> set of imported symbol names
     */
    private static void collectImportInfo(@NotNull PsiFile file,
                                          @NotNull Set<String> wildcardImportedPaths,
                                          @NotNull java.util.Map<String, Set<String>> namedImports) {
        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType type = child.getNode().getElementType();

            if (type == KiteElementTypes.IMPORT_STATEMENT) {
                analyzeImportForInfo(child, wildcardImportedPaths, namedImports, file);
            }
        }
    }

    /**
     * Analyze an import statement to determine if it's wildcard or named.
     */
    private static void analyzeImportForInfo(@NotNull PsiElement importStatement,
                                             @NotNull Set<String> wildcardImportedPaths,
                                             @NotNull java.util.Map<String, Set<String>> namedImports,
                                             @NotNull PsiFile containingFile) {
        boolean isWildcard = false;
        Set<String> symbols = new HashSet<>();
        String importPath = null;
        boolean foundImport = false;
        boolean foundFrom = false;

        for (PsiElement child = importStatement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() == null) continue;
            IElementType childType = child.getNode().getElementType();

            if (childType == KiteTokenTypes.IMPORT) {
                foundImport = true;
            } else if (foundImport && childType == KiteTokenTypes.MULTIPLY) {
                isWildcard = true;
            } else if (foundImport && !foundFrom && childType == KiteTokenTypes.IDENTIFIER) {
                symbols.add(child.getText());
            } else if (childType == KiteTokenTypes.FROM) {
                foundFrom = true;
            } else if (foundFrom) {
                // Extract path
                String text = child.getText();
                if (text.length() >= 2 &&
                    ((text.startsWith("\"") && text.endsWith("\"")) ||
                     (text.startsWith("'") && text.endsWith("'")))) {
                    importPath = text.substring(1, text.length() - 1);
                    break;
                }
            }
        }

        if (importPath == null) return;

        // Resolve the import path to an absolute path
        PsiFile resolvedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
        String absolutePath = null;
        if (resolvedFile != null && resolvedFile.getVirtualFile() != null) {
            absolutePath = resolvedFile.getVirtualFile().getPath();
        }

        if (isWildcard) {
            if (absolutePath != null) {
                wildcardImportedPaths.add(absolutePath);
            }
        } else if (!symbols.isEmpty()) {
            // Store both by import path and absolute path for lookup
            namedImports.put(importPath, symbols);
            if (absolutePath != null) {
                namedImports.put(absolutePath, symbols);
            }
        }
    }

    /**
     * Recursively collect all imported file paths (including transitive imports).
     */
    private static void collectImportedFilePaths(@NotNull PsiFile file, @NotNull Set<String> importedPaths, @NotNull Set<String> visited) {
        if (file.getVirtualFile() == null) return;

        String filePath = file.getVirtualFile().getPath();
        if (visited.contains(filePath)) return;
        visited.add(filePath);

        List<PsiFile> importedFiles = KiteImportHelper.getImportedFiles(file);
        for (PsiFile importedFile : importedFiles) {
            if (importedFile != null && importedFile.getVirtualFile() != null) {
                importedPaths.add(importedFile.getVirtualFile().getPath());
                // Recursively collect transitive imports
                collectImportedFilePaths(importedFile, importedPaths, visited);
            }
        }
    }

    /**
     * Check if a file declares a symbol with the given name.
     */
    private static boolean fileDeclaresSymbol(@NotNull PsiFile file, @NotNull String symbolName) {
        return fileDeclaresSymbolRecursive(file, symbolName);
    }

    private static boolean fileDeclaresSymbolRecursive(@NotNull PsiElement element, @NotNull String symbolName) {
        if (element.getNode() == null) return false;

        IElementType type = element.getNode().getElementType();

        // Check if this is a declaration
        if (isDeclarationType(type)) {
            String name = KitePsiUtil.findDeclarationName(element, type);
            if (symbolName.equals(name)) {
                return true;
            }
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (fileDeclaresSymbolRecursive(child, symbolName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION;
    }

    /**
     * Calculate the relative path from the current file to the target file.
     */
    @Nullable
    private static String calculateRelativePath(@NotNull VirtualFile currentFile, @NotNull VirtualFile targetFile) {
        VirtualFile currentDir = currentFile.getParent();
        if (currentDir == null) return null;

        String currentDirPath = currentDir.getPath();
        String targetPath = targetFile.getPath();

        // If target is in the same directory, just use the filename
        VirtualFile targetDir = targetFile.getParent();
        if (targetDir != null && targetDir.getPath().equals(currentDirPath)) {
            return targetFile.getName();
        }

        // Calculate relative path
        String[] currentParts = currentDirPath.split("/");
        String[] targetParts = targetPath.split("/");

        // Find common prefix
        int commonLength = 0;
        int minLength = Math.min(currentParts.length, targetParts.length - 1); // -1 because target includes filename
        for (int i = 0; i < minLength; i++) {
            if (currentParts[i].equals(targetParts[i])) {
                commonLength++;
            } else {
                break;
            }
        }

        // Build relative path
        StringBuilder relativePath = new StringBuilder();

        // Go up from current directory
        int stepsUp = currentParts.length - commonLength;
        for (int i = 0; i < stepsUp; i++) {
            relativePath.append("../");
        }

        // Go down to target
        for (int i = commonLength; i < targetParts.length; i++) {
            if (i > commonLength) {
                relativePath.append("/");
            }
            relativePath.append(targetParts[i]);
        }

        String result = relativePath.toString();

        // If the path doesn't start with ../ or /, add ./
        if (!result.startsWith("../") && !result.startsWith("/")) {
            // If it's just a filename, keep it as is (IntelliJ convention)
            if (!result.contains("/")) {
                return result;
            }
        }

        return result;
    }

    /**
     * Represents a candidate import that can resolve an undefined symbol.
     */
    public static class ImportCandidate {
        public final String symbolName;
        public final String importPath;
        public final String fullPath;

        public ImportCandidate(@NotNull String symbolName, @NotNull String importPath, @NotNull String fullPath) {
            this.symbolName = symbolName;
            this.importPath = importPath;
            this.fullPath = fullPath;
        }
    }
}
