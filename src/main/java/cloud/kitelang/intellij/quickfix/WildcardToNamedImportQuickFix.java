package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KitePsiUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quick fix to convert wildcard imports to named imports.
 * Replaces "import * from 'file'" with "import usedSymbol1, usedSymbol2 from 'file'".
 * Only includes symbols that are actually used in the file.
 */
public class WildcardToNamedImportQuickFix extends BaseIntentionAction {

    private final int importStart;
    private final int importEnd;
    private final String importPath;

    public WildcardToNamedImportQuickFix(int importStart, int importEnd, String importPath) {
        this.importStart = importStart;
        this.importEnd = importEnd;
        this.importPath = importPath;
    }

    @NotNull
    @Override
    public String getText() {
        return "Convert to named import";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Kite import fixes";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file != null && file.isValid();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (file == null) return;

        // Resolve the imported file
        PsiFile importedFile = KiteImportHelper.resolveFilePath(importPath, file);
        if (importedFile == null) return;

        // Get all exported symbols from the imported file
        Set<String> exportedSymbols = collectExportedSymbols(importedFile);
        if (exportedSymbols.isEmpty()) return;

        // Get all used symbols in the current file
        Set<String> usedSymbols = collectUsedSymbols(file);

        // Find which exported symbols are actually used
        List<String> usedFromImport = new ArrayList<>();
        for (String exported : exportedSymbols) {
            if (usedSymbols.contains(exported)) {
                usedFromImport.add(exported);
            }
        }

        if (usedFromImport.isEmpty()) return;

        // Sort alphabetically
        Collections.sort(usedFromImport);

        // Build the new import statement
        String symbolList = String.join(", ", usedFromImport);
        String newImport = "import " + symbolList + " from \"" + importPath + "\"";

        // Replace the import in the document
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return;

        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(importStart, importEnd, newImport);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        });
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

    /**
     * Collect all symbols used in the file (excluding import statements).
     */
    private Set<String> collectUsedSymbols(PsiFile file) {
        Set<String> usedSymbols = new HashSet<>();
        collectUsedSymbolsRecursive(file, usedSymbols);
        return usedSymbols;
    }

    /**
     * Recursively collect used symbols, skipping import statements.
     */
    private void collectUsedSymbolsRecursive(PsiElement element, Set<String> usedSymbols) {
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
            if (!isInsideImport(element)) {
                usedSymbols.add(element.getText());
            }
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

    /**
     * Check if an element is inside an import statement.
     */
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

        // Also check siblings to handle flat PSI structure
        PsiElement sibling = element.getPrevSibling();
        while (sibling != null) {
            if (sibling.getNode() != null) {
                IElementType sibType = sibling.getNode().getElementType();
                if (sibType == KiteTokenTypes.IMPORT) {
                    return true;
                }
                if (sibType == KiteTokenTypes.NL || sibType == KiteTokenTypes.NEWLINE) {
                    break; // Different line, not in import
                }
            }
            sibling = sibling.getPrevSibling();
        }

        return false;
    }

    /**
     * Extract variable names from string interpolation patterns.
     */
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
}
