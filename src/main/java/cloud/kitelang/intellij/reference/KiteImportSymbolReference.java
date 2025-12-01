package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference for symbols in import statements.
 * Resolves to the declaration in the specific file mentioned in the import.
 * <p>
 * For example, in "import appName from "common.kite"", the reference for appName
 * will only resolve to the appName declaration in common.kite.
 */
public class KiteImportSymbolReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final String symbolName;
    private final String importPath;

    public KiteImportSymbolReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement,
                                     @NotNull String symbolName, @NotNull String importPath) {
        super(element, rangeInElement);
        this.symbolName = symbolName;
        this.importPath = importPath;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();

        // Resolve the import path to get the target file
        PsiFile containingFile = myElement.getContainingFile();
        if (containingFile == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        PsiFile targetFile = KiteImportHelper.resolveFilePath(importPath, containingFile);
        if (targetFile == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        // Search for the symbol declaration in the target file only
        findDeclarationsInFile(targetFile, symbolName, results);

        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        return results.length >= 1 ? results[0].getElement() : null;
    }

    /**
     * Find declarations with the given name in a specific file.
     */
    private void findDeclarationsInFile(PsiElement element, String targetName, List<ResolveResult> results) {
        IElementType type = element.getNode().getElementType();

        if (KiteDeclarationHelper.isDeclarationType(type)) {
            var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                results.add(new PsiElementResolveResult(nameElement));
                return; // Found it
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findDeclarationsInFile(child, targetName, results);
            child = child.getNextSibling();
        }
    }
}
