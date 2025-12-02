package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.quickfix.RemoveUnusedImportQuickFix;
import cloud.kitelang.intellij.quickfix.WildcardToNamedImportQuickFix;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KiteIdentifierContextHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Annotator that detects unused imports and shows warnings.
 * <p>
 * Handles both named imports (import X, Y from "file") and wildcard imports (import * from "file").
 * Unused imports are shown with weak warning highlighting (grayed out).
 */
public class KiteUnusedImportAnnotator implements Annotator {

    // Pattern to match import statements and capture symbols and path
    // Group 1: symbols part (e.g., "formatName" or "a, b, c" or "*")
    // Group 2: path (e.g., "common.kite")
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^(\\s*import\\s+)([\\w,\\s*]+)(\\s+from\\s+)([\"'])([^\"']+)([\"'])",
            Pattern.MULTILINE
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process at file level
        if (!(element instanceof PsiFile file)) {
            return;
        }

        if (file.getLanguage() != KiteLanguage.INSTANCE) {
            return;
        }

        // Collect all used symbols in the file (excluding import statements)
        Set<String> usedSymbols = collectUsedSymbols(file);

        // Check each import statement for unused symbols
        checkUnusedImports(file, usedSymbols, holder);
    }

    /**
     * Collect all symbols used in the file (excluding import statements).
     * This includes identifiers used in:
     * - Variable references
     * - Function calls
     * - Type annotations
     * - String interpolations ($var and ${var})
     */
    private Set<String> collectUsedSymbols(PsiFile file) {
        Set<String> usedSymbols = new HashSet<>();
        collectUsedSymbolsRecursive(file, usedSymbols, false);
        return usedSymbols;
    }

    /**
     * Recursively collect used symbols, skipping import statements.
     */
    private void collectUsedSymbolsRecursive(PsiElement element, Set<String> usedSymbols, boolean inImport) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Skip import statements - we don't count the imported symbol names as "usage"
        if (type == KiteElementTypes.IMPORT_STATEMENT) {
            return;
        }

        // Check if this is an IMPORT keyword (alternative PSI structure)
        if (type == KiteTokenTypes.IMPORT) {
            return; // Skip the rest of the import line
        }

        // Collect identifier usages
        if (type == KiteTokenTypes.IDENTIFIER) {
            // Skip if this is inside an import statement
            if (!KiteIdentifierContextHelper.isInsideImportStatement(element)) {
                usedSymbols.add(element.getText());
            }
        }


        collectStringInterpolationUsages(element, usedSymbols, type);

        // Recurse into children
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectUsedSymbolsRecursive(child, usedSymbols, inImport);
        }
    }

    public static void collectStringInterpolationUsages(PsiElement element, Set<String> usedSymbols, IElementType type) {
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
    }

    /**
     * Extract variable names from string interpolation patterns.
     */
    private static void extractInterpolationsFromString(String text, Set<String> usedSymbols) {
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
     * Check all imports in the file for unused symbols.
     */
    private void checkUnusedImports(PsiFile file, Set<String> usedSymbols, AnnotationHolder holder) {
        String text = file.getText();
        Matcher matcher = IMPORT_PATTERN.matcher(text);

        while (matcher.find()) {
            String symbolsPart = matcher.group(2).trim();
            String importPath = matcher.group(5);

            int importStart = matcher.start();
            int importEnd = matcher.end();

            if ("*".equals(symbolsPart)) {
                // Wildcard import - check if ANY exported symbol from the file is used
                checkWildcardImport(file, importPath, usedSymbols, importStart, importEnd, holder);
            } else {
                // Named import - check each symbol individually
                checkNamedImport(file, symbolsPart, importPath, usedSymbols, importStart, importEnd, matcher, holder);
            }
        }
    }

    /**
     * Check if a wildcard import is used.
     * Gets all exported symbols from the imported file and checks if any are used.
     */
    private void checkWildcardImport(PsiFile containingFile, String importPath, Set<String> usedSymbols,
                                     int importStart, int importEnd, AnnotationHolder holder) {
        PsiFile importedFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
        if (importedFile == null) {
            return; // Can't resolve file - broken import is handled elsewhere
        }

        // Get all exported symbols from the imported file
        Set<String> exportedSymbols = collectExportedSymbols(importedFile);

        // Check if any exported symbol is used
        boolean anyUsed = false;
        for (String symbol : exportedSymbols) {
            if (usedSymbols.contains(symbol)) {
                anyUsed = true;
                break;
            }
        }

        if (!anyUsed && !exportedSymbols.isEmpty()) {
            // Find the PsiElement for the import statement
            PsiElement importElement = containingFile.findElementAt(importStart);
            if (importElement != null) {
                // Find the actual import statement element
                PsiElement importStatement = findImportStatementAt(containingFile, importStart);
                if (importStatement != null) {
                    holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "Unused import")
                            .range(importStatement.getTextRange())
                            .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                            .withFix(new RemoveUnusedImportQuickFix(importStart, importEnd))
                            .create();
                }
            }
        } else if (anyUsed) {
            // Wildcard import is used - offer "Convert to named import" quick fix
            PsiElement importStatement = findImportStatementAt(containingFile, importStart);
            if (importStatement != null) {
                holder.newAnnotation(HighlightSeverity.INFORMATION, "Wildcard import can be converted to named import")
                        .range(importStatement.getTextRange())
                        .highlightType(ProblemHighlightType.INFORMATION)
                        .withFix(new WildcardToNamedImportQuickFix(importStart, importEnd, importPath))
                        .create();
            }
        }
    }

    /**
     * Check if named imports are used.
     * Marks individual unused symbols or the whole import if all are unused.
     */
    private void checkNamedImport(PsiFile file, String symbolsPart, String importPath, Set<String> usedSymbols,
                                  int importStart, int importEnd, Matcher importMatcher, AnnotationHolder holder) {
        String[] symbols = symbolsPart.split("\\s*,\\s*");
        List<String> unusedSymbols = new ArrayList<>();

        for (String symbol : symbols) {
            String trimmed = symbol.trim();
            if (!trimmed.isEmpty() && !usedSymbols.contains(trimmed)) {
                unusedSymbols.add(trimmed);
            }
        }

        if (unusedSymbols.isEmpty()) {
            return; // All symbols are used
        }

        if (unusedSymbols.size() == symbols.length) {
            // All symbols are unused - mark the whole import
            PsiElement importStatement = findImportStatementAt(file, importStart);
            if (importStatement != null) {
                holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "Unused import")
                        .range(importStatement.getTextRange())
                        .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                        .withFix(new RemoveUnusedImportQuickFix(importStart, importEnd))
                        .create();
            }
        } else {
            // Only some symbols are unused - mark individual symbols
            // Find and mark each unused symbol in the import statement
            for (String unusedSymbol : unusedSymbols) {
                markUnusedSymbolInImport(file, importStart, importEnd, unusedSymbol, holder);
            }
        }
    }

    /**
     * Find and mark a specific unused symbol within an import statement.
     */
    private void markUnusedSymbolInImport(PsiFile file, int importStart, int importEnd,
                                          String symbolName, AnnotationHolder holder) {
        // Search for the symbol name within the import statement range
        String importText = file.getText().substring(importStart, importEnd);

        // Find the symbol - it should be before "from"
        int fromIndex = importText.indexOf(" from ");
        if (fromIndex == -1) fromIndex = importText.length();

        String beforeFrom = importText.substring(0, fromIndex);

        // Find the symbol position (accounting for "import " prefix and commas)
        int symbolIndex = -1;
        Pattern symbolPattern = Pattern.compile("\\b" + Pattern.quote(symbolName) + "\\b");
        Matcher matcher = symbolPattern.matcher(beforeFrom);
        if (matcher.find()) {
            symbolIndex = matcher.start();
        }

        if (symbolIndex >= 0) {
            int absoluteStart = importStart + symbolIndex;
            int absoluteEnd = absoluteStart + symbolName.length();

            PsiElement symbolElement = file.findElementAt(absoluteStart);
            if (symbolElement != null && symbolElement.getNode() != null &&
                symbolElement.getNode().getElementType() == KiteTokenTypes.IDENTIFIER) {
                holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                                "Unused import symbol '" + symbolName + "'")
                        .range(symbolElement.getTextRange())
                        .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                        .withFix(new RemoveUnusedImportQuickFix(symbolName, importStart, importEnd))
                        .create();
            }
        }
    }

    /**
     * Find the import statement PSI element at a given offset.
     */
    @Nullable
    private PsiElement findImportStatementAt(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null) return null;

        // Walk up to find IMPORT_STATEMENT
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile)) {
            if (current.getNode() != null &&
                current.getNode().getElementType() == KiteElementTypes.IMPORT_STATEMENT) {
                return current;
            }
            current = current.getParent();
        }

        // Alternative: For flat PSI structure, find the line containing the import
        // Return the IMPORT token and extend to end of line
        element = file.findElementAt(offset);
        while (element != null) {
            if (element.getNode() != null &&
                element.getNode().getElementType() == KiteTokenTypes.IMPORT) {
                // Find the extent of this import statement (to newline)
                return findImportLineExtent(element);
            }
            element = element.getParent();
        }

        // Last resort: return element at offset
        return file.findElementAt(offset);
    }

    /**
     * Find the full extent of an import line starting from the IMPORT keyword.
     */
    private PsiElement findImportLineExtent(PsiElement importKeyword) {
        // For now, just return the import keyword - the annotation will cover it
        // In a proper implementation, we'd create a synthetic range
        return importKeyword;
    }

    /**
     * Collect all exported (declared) symbols from a file.
     */
    private Set<String> collectExportedSymbols(PsiFile file) {
        Set<String> symbols = new HashSet<>();
        collectExportedSymbolsRecursive(file, symbols);
        return symbols;
    }

    /**
     * Recursively collect all top-level declarations from a file.
     */
    private void collectExportedSymbolsRecursive(PsiElement element, Set<String> symbols) {
        if (element.getNode() == null) return;

        IElementType type = element.getNode().getElementType();

        // Check for declaration types
        if (KiteDeclarationHelper.isDeclarationType(type)) {
            var name = KitePsiUtil.findDeclarationName(element, type);
            if (name != null && !name.isEmpty()) {
                symbols.add(name);
            }
        }

        // Recurse into children (but not into nested scopes like function bodies for exports)
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            // Skip braces (function/schema/resource bodies) for export collection
            // We only want top-level declarations
            if (child.getNode() != null && child.getNode().getElementType() == KiteTokenTypes.LBRACE) {
                break; // Don't recurse into bodies
            }
            collectExportedSymbolsRecursive(child, symbols);
        }
    }
}
