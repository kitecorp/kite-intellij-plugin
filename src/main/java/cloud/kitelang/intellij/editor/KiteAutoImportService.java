package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Service that handles auto-importing symbols in Kite files.
 * This is used by paste handlers and other features that need to add imports.
 */
public class KiteAutoImportService {

    private static final Logger LOG = Logger.getInstance(KiteAutoImportService.class);

    private static final Set<String> KEYWORDS = Set.of(
            "var", "fun", "schema", "component", "resource", "type", "import", "from",
            "input", "output", "return", "if", "else", "for", "in", "while", "break", "continue",
            "true", "false", "null"
    );

    private static final Set<String> BUILTIN_TYPES = Set.of(
            "string", "number", "boolean", "any", "object", "null"
    );

    /**
     * Process auto-imports for a region of code.
     *
     * @param file        The file to process
     * @param startOffset Start of the region to analyze
     * @param endOffset   End of the region to analyze
     */
    public static void processAutoImports(@NotNull PsiFile file, int startOffset, int endOffset) {
        LOG.info("processAutoImports called for " + file.getName() + " from " + startOffset + " to " + endOffset);

        if (file.getFileType() != KiteFileType.INSTANCE) {
            LOG.info("Not a Kite file, skipping");
            return;
        }

        Project project = file.getProject();

        // Find undefined symbols in the region
        Set<String> undefinedSymbols = findUndefinedSymbols(file, startOffset, endOffset);
        LOG.info("Found undefined symbols: " + undefinedSymbols);
        if (undefinedSymbols.isEmpty()) {
            return;
        }

        // Get already imported symbols
        Set<String> alreadyImported = KiteImportHelper.getImportedSymbols(file);
        LOG.info("Already imported: " + alreadyImported);

        // Get locally defined symbols
        Set<String> locallyDefined = getLocallyDefinedSymbols(file);
        LOG.info("Locally defined: " + locallyDefined);

        // Filter out already imported and locally defined symbols
        undefinedSymbols.removeAll(alreadyImported);
        undefinedSymbols.removeAll(locallyDefined);
        LOG.info("After filtering: " + undefinedSymbols);

        if (undefinedSymbols.isEmpty()) {
            return;
        }

        // Find symbols in project files
        Map<String, String> symbolToFile = findSymbolsInProject(project, file, undefinedSymbols);
        LOG.info("Symbol to file mapping: " + symbolToFile);

        if (symbolToFile.isEmpty()) {
            return;
        }

        // Add imports
        addImports(file, symbolToFile);
    }

    /**
     * Find undefined symbols (identifiers) in the specified range.
     */
    private static Set<String> findUndefinedSymbols(PsiFile file, int startOffset, int endOffset) {
        Set<String> symbols = new HashSet<>();

        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                int offset = element.getTextOffset();
                if (offset >= startOffset && offset < endOffset) {
                    if (element.getNode() != null) {
                        IElementType type = element.getNode().getElementType();
                        if (type == KiteTokenTypes.IDENTIFIER) {
                            String text = element.getText();
                            if (isValidSymbolCandidate(text) && !isDeclarationName(element)) {
                                symbols.add(text);
                            }
                        }
                    }
                }
                super.visitElement(element);
            }
        });

        return symbols;
    }

    /**
     * Check if the text is a valid symbol candidate (not a keyword or builtin type).
     */
    private static boolean isValidSymbolCandidate(String text) {
        if (text == null || text.isEmpty()) return false;
        if (KEYWORDS.contains(text)) return false;
        if (BUILTIN_TYPES.contains(text)) return false;
        return true;
    }

    /**
     * Check if this identifier is a declaration name (not a reference).
     */
    private static boolean isDeclarationName(PsiElement element) {
        PsiElement next = skipWhitespace(element.getNextSibling(), true);
        if (next == null || next.getNode() == null) return false;

        IElementType nextType = next.getNode().getElementType();
        // If followed by = or { or :, it's a declaration name
        return nextType == KiteTokenTypes.ASSIGN ||
               nextType == KiteTokenTypes.LBRACE ||
               nextType == KiteTokenTypes.COLON ||
               nextType == KiteTokenTypes.PLUS_ASSIGN;
    }

    /**
     * Get all locally defined symbols in the file.
     */
    private static Set<String> getLocallyDefinedSymbols(PsiFile file) {
        Set<String> symbols = new HashSet<>();

        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element.getNode() != null) {
                    IElementType type = element.getNode().getElementType();
                    if (isDeclarationType(type)) {
                        String name = findDeclarationName(element);
                        if (name != null) {
                            symbols.add(name);
                        }
                    }
                }
                super.visitElement(element);
            }
        });

        return symbols;
    }

    private static boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION;
    }

    private static String findDeclarationName(PsiElement declaration) {
        for (PsiElement child = declaration.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                PsiElement next = skipWhitespace(child.getNextSibling(), true);
                if (next != null && next.getNode() != null) {
                    IElementType nextType = next.getNode().getElementType();
                    if (nextType == KiteTokenTypes.ASSIGN ||
                        nextType == KiteTokenTypes.LBRACE ||
                        nextType == KiteTokenTypes.COLON ||
                        nextType == KiteTokenTypes.PLUS_ASSIGN ||
                        nextType == KiteTokenTypes.LPAREN) {  // For function declarations
                        return child.getText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find which project files export the given symbols.
     * Prioritizes files closer to the current file (same directory, fewer parent traversals).
     * Note: Must be called from a read action context.
     */
    private static Map<String, String> findSymbolsInProject(Project project, PsiFile currentFile, Set<String> symbols) {
        // Collect all candidates for each symbol with their relative paths
        Map<String, List<String>> symbolToCandidates = new LinkedHashMap<>();

        var psiManager = PsiManager.getInstance(project);
        var virtualFiles = FileTypeIndex.getFiles(KiteFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        var currentVirtualFile = currentFile.getVirtualFile();

        for (var virtualFile : virtualFiles) {
            // Skip current file
            if (virtualFile.equals(currentVirtualFile)) continue;

            var psiFile = psiManager.findFile(virtualFile);
            if (psiFile == null) continue;

            // Get exports from this file
            Set<String> exports = KiteImportHelper.getExportedSymbols(psiFile);

            for (String symbol : symbols) {
                if (exports.contains(symbol)) {
                    // Calculate relative path
                    String relativePath = KiteImportHelper.getRelativePath(currentFile, psiFile);
                    if (relativePath != null) {
                        symbolToCandidates.computeIfAbsent(symbol, k -> new ArrayList<>()).add(relativePath);
                    }
                }
            }
        }

        // Pick the best candidate for each symbol (shortest path = closest file)
        Map<String, String> symbolToFile = new LinkedHashMap<>();
        for (var entry : symbolToCandidates.entrySet()) {
            String symbol = entry.getKey();
            List<String> candidates = entry.getValue();

            // Sort by path length and parent traversal count to prefer closer files
            candidates.sort((a, b) -> {
                // Count parent directory traversals (../)
                int aParentCount = countParentTraversals(a);
                int bParentCount = countParentTraversals(b);
                if (aParentCount != bParentCount) {
                    return aParentCount - bParentCount;
                }
                // If same parent level, prefer shorter path
                return a.length() - b.length();
            });

            // Use the first (best) candidate
            if (!candidates.isEmpty()) {
                symbolToFile.put(symbol, candidates.get(0));
                LOG.info("Symbol '" + symbol + "' found in " + candidates.size() + " files, selected: " + candidates.get(0));
            }
        }

        return symbolToFile;
    }

    /**
     * Count the number of parent directory traversals (../) in a path.
     */
    private static int countParentTraversals(String path) {
        int count = 0;
        int index = 0;
        while ((index = path.indexOf("../", index)) != -1) {
            count++;
            index += 3;
        }
        return count;
    }

    /**
     * Add import statements for the given symbols.
     */
    private static void addImports(PsiFile file, Map<String, String> symbolToFile) {
        LOG.info("addImports called with " + symbolToFile.size() + " symbols to import");

        // Group symbols by file
        Map<String, List<String>> fileToSymbols = new LinkedHashMap<>();
        for (var entry : symbolToFile.entrySet()) {
            fileToSymbols.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        LOG.info("Grouped by file: " + fileToSymbols);

        // Get existing imports grouped by file
        Map<String, Set<String>> existingImports = KiteImportHelper.getImportsByFile(file);
        LOG.info("Existing imports by file: " + existingImports);

        var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            LOG.warn("Document is null, cannot add imports");
            return;
        }

        StringBuilder importText = new StringBuilder();
        List<ImportUpdate> updates = new ArrayList<>();

        for (var entry : fileToSymbols.entrySet()) {
            String filePath = entry.getKey();
            List<String> newSymbols = entry.getValue();

            Set<String> existing = existingImports.get(filePath);
            if (existing != null && !existing.isEmpty()) {
                // Merge with existing import
                Set<String> allSymbols = new TreeSet<>(existing);
                allSymbols.addAll(newSymbols);
                updates.add(new ImportUpdate(filePath, allSymbols));
                LOG.info("Will update existing import for " + filePath + " with symbols: " + allSymbols);
            } else {
                // Create new import
                Collections.sort(newSymbols);
                String newImport = "import " + String.join(", ", newSymbols) + " from \"" + filePath + "\"\n";
                importText.append(newImport);
                LOG.info("Will add new import: " + newImport.trim());
            }
        }

        var project = file.getProject();
        String importToAdd = importText.toString();

        // Runnable for the write action
        Runnable writeAction = () -> {
            WriteCommandAction.runWriteCommandAction(project, "Auto-Import on Paste", null, () -> {
                if (!file.isValid()) {
                    LOG.warn("File is no longer valid, skipping import addition");
                    return;
                }

                var doc = PsiDocumentManager.getInstance(project).getDocument(file);
                if (doc == null) {
                    LOG.warn("Document is null during write action");
                    return;
                }

                // First update existing imports
                for (ImportUpdate update : updates) {
                    updateExistingImport(file, update.filePath, update.symbols);
                }

                // Then add new imports
                if (!importToAdd.isEmpty()) {
                    int insertOffset = findImportInsertOffset(file);
                    LOG.info("Inserting import at offset " + insertOffset + ": " + importToAdd.trim());
                    doc.insertString(insertOffset, importToAdd);
                }

                PsiDocumentManager.getInstance(project).commitDocument(doc);
                LOG.info("Import addition completed");
            }, file);
        };

        // If already on EDT, run synchronously; otherwise schedule on EDT
        if (ApplicationManager.getApplication().isDispatchThread()) {
            writeAction.run();
        } else {
            ApplicationManager.getApplication().invokeLater(writeAction);
        }
    }

    private static class ImportUpdate {
        final String filePath;
        final Set<String> symbols;

        ImportUpdate(String filePath, Set<String> symbols) {
            this.filePath = filePath;
            this.symbols = symbols;
        }
    }

    /**
     * Update an existing import statement to include additional symbols.
     */
    private static void updateExistingImport(PsiFile file, String filePath, Set<String> allSymbols) {
        var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return;

        String quotedPath = "\"" + filePath + "\"";
        String text = document.getText();

        // Find the import line for this file
        int lineStart = 0;
        for (String line : text.split("\n")) {
            if (line.trim().startsWith("import ") && line.contains(quotedPath)) {
                int lineEnd = lineStart + line.length();

                // Build new import line
                List<String> sortedSymbols = new ArrayList<>(allSymbols);
                Collections.sort(sortedSymbols);
                String newLine = "import " + String.join(", ", sortedSymbols) + " from " + quotedPath;

                document.replaceString(lineStart, lineEnd, newLine);
                return;
            }
            lineStart += line.length() + 1; // +1 for newline
        }
    }

    /**
     * Find the offset where new imports should be inserted.
     */
    private static int findImportInsertOffset(PsiFile file) {
        int lastImportEnd = 0;

        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteElementTypes.IMPORT_STATEMENT) {
                lastImportEnd = child.getTextRange().getEndOffset();
                // Skip past any newlines after the import
                PsiElement next = child.getNextSibling();
                while (next != null && isWhitespace(next)) {
                    if (next.getText().contains("\n")) {
                        lastImportEnd = next.getTextRange().getEndOffset();
                        break;
                    }
                    next = next.getNextSibling();
                }
            } else if (!isWhitespace(child) && child.getNode() != null) {
                // Hit non-import, non-whitespace element
                break;
            }
        }

        return lastImportEnd;
    }

    private static PsiElement skipWhitespace(PsiElement element, boolean forward) {
        while (element != null && isWhitespace(element)) {
            element = forward ? element.getNextSibling() : element.getPrevSibling();
        }
        return element;
    }

    private static boolean isWhitespace(PsiElement element) {
        if (element == null || element.getNode() == null) return false;
        IElementType type = element.getNode().getElementType();
        return type == com.intellij.psi.TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NEWLINE;
    }
}
