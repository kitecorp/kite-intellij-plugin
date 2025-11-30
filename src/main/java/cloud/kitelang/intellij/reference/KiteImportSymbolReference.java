package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
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

        if (isDeclarationType(type)) {
            PsiElement nameElement = findNameInDeclaration(element, type);
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

    private boolean isDeclarationType(IElementType type) {
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
     * Find the name identifier within a declaration.
     */
    @Nullable
    private PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        // For var/input/output: keyword [type] name [= value]
        // For resource/component/schema/function: keyword [type] name { ... }
        // Find the identifier that comes before '=' or '{'
        PsiElement lastIdentifier = null;
        PsiElement child = declaration.getFirstChild();
        while (child != null) {
            IElementType childType = child.getNode().getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            } else if (childType == KiteTokenTypes.ASSIGN ||
                       childType == KiteTokenTypes.LBRACE ||
                       childType == KiteTokenTypes.PLUS_ASSIGN) {
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier;
    }
}
