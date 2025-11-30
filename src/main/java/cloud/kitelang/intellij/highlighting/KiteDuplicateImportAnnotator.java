package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Annotator that detects duplicate imports.
 * <p>
 * Detects:
 * - Same symbol imported twice from different files
 * - Same symbol imported twice from the same file
 * - Same symbol appearing twice in the same import statement
 * - Named import after wildcard import from same file
 */
public class KiteDuplicateImportAnnotator implements Annotator {

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^(\\s*import\\s+)([\\w,\\s*]+)(\\s+from\\s+)([\"'])([^\"']+)([\"'])\\s*(?:\\r?\\n)?",
            Pattern.MULTILINE
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile)) {
            return;
        }

        PsiFile file = (PsiFile) element;
        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        checkDuplicateImports(file, holder);
    }

    /**
     * Check for duplicate imports in the file.
     */
    private void checkDuplicateImports(PsiFile file, AnnotationHolder holder) {
        String text = file.getText();
        Matcher matcher = IMPORT_PATTERN.matcher(text);

        // Track imported symbols: symbol name -> first import info
        Map<String, ImportInfo> importedSymbols = new HashMap<>();
        // Track wildcard imports: file path -> import info
        Map<String, ImportInfo> wildcardImports = new HashMap<>();

        while (matcher.find()) {
            String symbolsPart = matcher.group(2).trim();
            String importPath = matcher.group(5);
            int importStart = matcher.start();
            int importEnd = matcher.end();

            if ("*".equals(symbolsPart)) {
                // Wildcard import
                ImportInfo wildcardInfo = new ImportInfo(importPath, importStart, importEnd, "*");
                wildcardImports.put(importPath, wildcardInfo);
            } else {
                // Named import - check each symbol
                String[] symbols = symbolsPart.split("\\s*,\\s*");
                Set<String> seenInThisStatement = new HashSet<>();

                for (String symbol : symbols) {
                    String trimmed = symbol.trim();
                    if (trimmed.isEmpty()) continue;

                    // Check for duplicate within same import statement
                    if (seenInThisStatement.contains(trimmed)) {
                        markDuplicateSymbol(file, importStart, importEnd, trimmed,
                                "'" + trimmed + "' is already imported in this statement", holder);
                        continue;
                    }
                    seenInThisStatement.add(trimmed);

                    // Check if this symbol was already imported
                    if (importedSymbols.containsKey(trimmed)) {
                        ImportInfo firstImport = importedSymbols.get(trimmed);
                        markDuplicateSymbol(file, importStart, importEnd, trimmed,
                                "'" + trimmed + "' is already imported from \"" + firstImport.path + "\"", holder);
                    } else {
                        // Check if covered by a wildcard import from the same file
                        if (wildcardImports.containsKey(importPath)) {
                            markDuplicateSymbol(file, importStart, importEnd, trimmed,
                                    "'" + trimmed + "' is already imported via wildcard from \"" + importPath + "\"", holder);
                        } else {
                            // First import of this symbol
                            importedSymbols.put(trimmed, new ImportInfo(importPath, importStart, importEnd, trimmed));
                        }
                    }
                }
            }
        }
    }

    /**
     * Mark a specific duplicate symbol in an import statement.
     */
    private void markDuplicateSymbol(PsiFile file, int importStart, int importEnd,
                                     String symbolName, String message, AnnotationHolder holder) {
        String importText = file.getText().substring(importStart, importEnd);

        // Find the symbol position within the import statement
        int fromIndex = importText.indexOf(" from ");
        if (fromIndex == -1) fromIndex = importText.length();

        String beforeFrom = importText.substring(0, fromIndex);

        // Find the symbol position
        Pattern symbolPattern = Pattern.compile("\\b" + Pattern.quote(symbolName) + "\\b");
        Matcher symbolMatcher = symbolPattern.matcher(beforeFrom);

        // Find the LAST occurrence in case of duplicates in same statement
        int symbolIndex = -1;
        while (symbolMatcher.find()) {
            symbolIndex = symbolMatcher.start();
        }

        if (symbolIndex >= 0) {
            int absoluteStart = importStart + symbolIndex;

            PsiElement symbolElement = file.findElementAt(absoluteStart);
            if (symbolElement != null && symbolElement.getNode() != null &&
                symbolElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                holder.newAnnotation(HighlightSeverity.WARNING, message)
                        .range(symbolElement.getTextRange())
                        .highlightType(ProblemHighlightType.WARNING)
                        .create();
            }
        }
    }

    /**
     * Information about an import.
     */
    private static class ImportInfo {
        final String path;
        final int startOffset;
        final int endOffset;
        final String symbol;

        ImportInfo(String path, int startOffset, int endOffset, String symbol) {
            this.path = path;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.symbol = symbol;
        }
    }
}
