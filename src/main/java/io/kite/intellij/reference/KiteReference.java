package io.kite.intellij.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference implementation for Kite identifiers.
 * Resolves identifier usages to their declarations.
 */
public class KiteReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    private final String name;

    public KiteReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
        super(element, rangeInElement);
        this.name = element.getText();
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        List<ResolveResult> results = new ArrayList<>();
        PsiFile file = myElement.getContainingFile();

        // Find all declarations with this name
        findDeclarations(file, name, results);

        return results.toArray(new ResolveResult[0]);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        return results.length == 1 ? results[0].getElement() : null;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        // For now, don't support renaming
        return myElement;
    }

    /**
     * Find all declarations with the given name in the file.
     */
    private void findDeclarations(PsiElement element, String targetName, List<ResolveResult> results) {
        // Check if this element is a declaration
        IElementType type = element.getNode().getElementType();

        if (isDeclarationType(type)) {
            // Find the name identifier within this declaration
            PsiElement nameElement = findNameInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                // Don't resolve to self
                if (nameElement != myElement) {
                    results.add(new PsiElementResolveResult(nameElement));
                }
            }
        }

        // Recurse into children - use getFirstChild/getNextSibling to include all nodes
        PsiElement child = element.getFirstChild();
        while (child != null) {
            findDeclarations(child, targetName, results);
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
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT;  // for loop variable
    }

    /**
     * Find the name identifier within a declaration.
     * The position varies by declaration type.
     */
    @Nullable
    private PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
        // Walk through children to find the identifier that represents the name
        // For most declarations, it's the identifier after the keyword and optional type
        // Use getFirstChild/getNextSibling to include leaf tokens

        if (declarationType == KiteElementTypes.FOR_STATEMENT) {
            // For loop: "for identifier in ..." - name is right after 'for'
            boolean foundFor = false;
            PsiElement child = declaration.getFirstChild();
            while (child != null) {
                IElementType childType = child.getNode().getElementType();
                if (childType == KiteTokenTypes.FOR) {
                    foundFor = true;
                } else if (foundFor && childType == KiteTokenTypes.IDENTIFIER) {
                    return child;
                }
                child = child.getNextSibling();
            }
        }

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
                // The identifier before = or { is the name
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }

        // If no = or {, return the last identifier found (for declarations without initializer)
        return lastIdentifier;
    }
}
