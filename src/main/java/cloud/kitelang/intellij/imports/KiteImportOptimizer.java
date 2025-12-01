package cloud.kitelang.intellij.imports;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import optimizer for Kite language files.
 * Removes unused imports when the user runs "Optimize Imports" (Ctrl+Alt+O / Cmd+Alt+O).
 */
public class KiteImportOptimizer implements ImportOptimizer {

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^(\\s*import\\s+)([\\w,\\s*]+)(\\s+from\\s+)([\"'])([^\"']+)([\"'])\\s*(?:\\r?\\n)?",
            Pattern.MULTILINE
    );

    @Override
    public boolean supports(@NotNull PsiFile file) {
        return file.getFileType() == KiteFileType.INSTANCE;
    }

    @NotNull
    @Override
    public Runnable processFile(@NotNull PsiFile file) {
        // Collect analysis data before returning the runnable
        Set<String> usedSymbols = collectUsedSymbols(file);
        List<ImportInfo> importsToProcess = analyzeImports(file, usedSymbols);

        return () -> {
            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document == null) return;

            // Filter to only keep non-removed imports and sort symbols within each import
            List<ImportInfo> importsToKeep = new ArrayList<>();
            for (ImportInfo importInfo : importsToProcess) {
                if (!importInfo.shouldRemoveEntirely) {
                    // Sort symbols within multi-symbol imports
                    if (!importInfo.isWildcard && !importInfo.symbolsToKeep.isEmpty()) {
                        Collections.sort(importInfo.symbolsToKeep, String.CASE_INSENSITIVE_ORDER);
                    }
                    importsToKeep.add(importInfo);
                }
            }

            // Sort imports by path (case-insensitive)
            importsToKeep.sort((a, b) -> a.importPath.compareToIgnoreCase(b.importPath));

            // If no imports left or nothing to change, check if we need to sort
            if (importsToProcess.isEmpty()) {
                return;
            }

            // Find the range of all imports in the document
            int firstImportStart = importsToProcess.stream()
                    .mapToInt(i -> i.startOffset)
                    .min()
                    .orElse(0);
            int lastImportEnd = importsToProcess.stream()
                    .mapToInt(i -> i.endOffset)
                    .max()
                    .orElse(0);

            // Build the new imports section
            StringBuilder newImportsSection = new StringBuilder();
            for (ImportInfo importInfo : importsToKeep) {
                if (importInfo.isWildcard) {
                    newImportsSection.append("import * from \"").append(importInfo.importPath).append("\"\n");
                } else {
                    String symbols = String.join(", ", importInfo.symbolsToKeep);
                    newImportsSection.append("import ").append(symbols).append(" from \"")
                            .append(importInfo.importPath).append("\"\n");
                }
            }

            // Replace entire import section
            document.replaceString(firstImportStart, lastImportEnd, newImportsSection.toString());

            // Commit document changes to update PSI
            PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        };
    }

    /**
     * Collect all symbols used in the file (excluding import statements).
     */
    private Set<String> collectUsedSymbols(PsiFile file) {
        Set<String> usedSymbols = new HashSet<>();
        collectUsedSymbolsRecursive(file, usedSymbols);
        return usedSymbols;
    }

    private void collectUsedSymbolsRecursive(PsiElement element, Set<String> usedSymbols) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Skip import statements
        if (type == KiteElementTypes.IMPORT_STATEMENT || type == KiteTokenTypes.IMPORT) {
            return;
        }

        // Collect identifier usages
        if (type == KiteTokenTypes.IDENTIFIER && !isInsideImport(element)) {
            usedSymbols.add(element.getText());
        }

        // Collect string interpolation usages: $varName
        if (type == KiteTokenTypes.INTERP_SIMPLE) {
            String text = element.getText();
            if (text.startsWith("$") && text.length() > 1) {
                usedSymbols.add(text.substring(1));
            }
        }

        // Collect string interpolation usages: ${varName}
        if (type == KiteTokenTypes.INTERP_IDENTIFIER) {
            usedSymbols.add(element.getText());
        }

        // Check STRING tokens for legacy interpolation patterns
        if (type == KiteTokenTypes.STRING || type == KiteTokenTypes.STRING_TEXT) {
            extractInterpolationsFromString(element.getText(), usedSymbols);
        }

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectUsedSymbolsRecursive(child, usedSymbols);
        }
    }

    private boolean isInsideImport(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent.getNode() != null) {
                IElementType type = parent.getNode().getElementType();
                if (type == KiteElementTypes.IMPORT_STATEMENT) {
                    return true;
                }
            }
            parent = parent.getParent();
        }

        // Also check siblings for flat PSI structure
        PsiElement sibling = element.getPrevSibling();
        while (sibling != null) {
            if (sibling.getNode() != null) {
                IElementType sibType = sibling.getNode().getElementType();
                if (sibType == KiteTokenTypes.IMPORT) {
                    return true;
                }
                if (sibType == KiteTokenTypes.NL || sibType == KiteTokenTypes.NEWLINE) {
                    break;
                }
            }
            sibling = sibling.getPrevSibling();
        }

        return false;
    }

    private void extractInterpolationsFromString(String text, Set<String> usedSymbols) {
        // Match $varName pattern
        Pattern simpleInterp = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher matcher = simpleInterp.matcher(text);
        while (matcher.find()) {
            usedSymbols.add(matcher.group(1));
        }

        // Match ${varName} pattern
        Pattern bracedInterp = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
        matcher = bracedInterp.matcher(text);
        while (matcher.find()) {
            usedSymbols.add(matcher.group(1));
        }
    }

    /**
     * Analyze all imports in the file and determine which should be removed.
     */
    private List<ImportInfo> analyzeImports(PsiFile file, Set<String> usedSymbols) {
        List<ImportInfo> imports = new ArrayList<>();
        String text = file.getText();
        Matcher matcher = IMPORT_PATTERN.matcher(text);

        while (matcher.find()) {
            String symbolsPart = matcher.group(2).trim();
            String importPath = matcher.group(5);
            int startOffset = matcher.start();
            int endOffset = matcher.end();

            ImportInfo info = new ImportInfo();
            info.startOffset = startOffset;
            info.endOffset = endOffset;
            info.importPath = importPath;

            if ("*".equals(symbolsPart)) {
                // Wildcard import - check if ANY exported symbol is used
                info.isWildcard = true;
                info.shouldRemoveEntirely = !isWildcardImportUsed(file, importPath, usedSymbols);
            } else {
                // Named import
                String[] symbols = symbolsPart.split("\\s*,\\s*");
                info.allSymbols = new ArrayList<>();
                info.symbolsToKeep = new ArrayList<>();

                for (String symbol : symbols) {
                    String trimmed = symbol.trim();
                    if (!trimmed.isEmpty()) {
                        info.allSymbols.add(trimmed);
                        if (usedSymbols.contains(trimmed)) {
                            info.symbolsToKeep.add(trimmed);
                        }
                    }
                }

                info.shouldRemoveEntirely = info.symbolsToKeep.isEmpty();
            }

            imports.add(info);
        }

        return imports;
    }

    /**
     * Check if a wildcard import is used by checking if any exported symbol is referenced.
     */
    private boolean isWildcardImportUsed(PsiFile containingFile, String importPath, Set<String> usedSymbols) {
        PsiFile importedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
        if (importedFile == null) {
            return true; // Can't resolve - keep the import
        }

        Set<String> exportedSymbols = collectExportedSymbols(importedFile);
        for (String symbol : exportedSymbols) {
            if (usedSymbols.contains(symbol)) {
                return true;
            }
        }

        return exportedSymbols.isEmpty(); // Keep import if file exports nothing (might be side-effect import)
    }

    /**
     * Collect all exported symbols from a file.
     */
    private Set<String> collectExportedSymbols(PsiFile file) {
        Set<String> symbols = new HashSet<>();
        collectExportedSymbolsRecursive(file, symbols);
        return symbols;
    }

    private void collectExportedSymbolsRecursive(PsiElement element, Set<String> symbols) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        if (KiteDeclarationHelper.isDeclarationType(type)) {
            var name = KitePsiUtil.findDeclarationName(element, type);
            if (name != null && !name.isEmpty()) {
                symbols.add(name);
            }
        }

        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.LBRACE) {
                break;
            }
            collectExportedSymbolsRecursive(child, symbols);
        }
    }

    /**
     * Information about an import statement for processing.
     */
    private static class ImportInfo {
        int startOffset;
        int endOffset;
        String importPath;
        boolean isWildcard;
        boolean shouldRemoveEntirely;
        List<String> allSymbols = new ArrayList<>();
        List<String> symbolsToKeep = new ArrayList<>();
    }
}
