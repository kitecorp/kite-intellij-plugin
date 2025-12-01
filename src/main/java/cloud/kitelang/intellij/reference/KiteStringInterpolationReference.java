package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import com.intellij.codeInsight.highlighting.HighlightedReference;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference for variables inside string interpolation expressions like ${var} or $var.
 * Implements HighlightedReference to ensure only the variable name portion is highlighted
 * when hovering with Cmd/Ctrl, not the entire string literal.
 */
public class KiteStringInterpolationReference extends PsiReferenceBase<PsiElement> implements HighlightedReference {
    private static final Logger LOG = Logger.getInstance(KiteStringInterpolationReference.class);
    private final String variableName;

    public KiteStringInterpolationReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement, @NotNull String variableName) {
        super(element, rangeInElement);
        this.variableName = variableName;
        LOG.info("[KiteStringInterpRef] Created reference: variableName=" + variableName + ", range=" + rangeInElement);
    }

    @Override
    public @Nullable PsiElement resolve() {
        LOG.info("[KiteStringInterpRef] resolve() called for: " + variableName);
        PsiFile file = myElement.getContainingFile();
        if (file == null) {
            LOG.info("[KiteStringInterpRef] No containing file!");
            return null;
        }

        // Search for declaration in file scope
        PsiElement result = findDeclaration(file, variableName);
        LOG.info("[KiteStringInterpRef] resolve() result for " + variableName + ": " + (result != null ? result.getText() : "null"));
        return result;
    }

    /**
     * Find a declaration with the given name in the file.
     */
    @Nullable
    private PsiElement findDeclaration(PsiElement element, String targetName) {
        IElementType type = element.getNode().getElementType();

        if (KiteDeclarationHelper.isDeclarationType(type)) {
            var nameElement = KiteDeclarationHelper.findNameElementInDeclaration(element, type);
            if (nameElement != null && targetName.equals(nameElement.getText())) {
                return nameElement;
            }
        }

        // Recurse into children
        PsiElement child = element.getFirstChild();
        while (child != null) {
            PsiElement result = findDeclaration(child, targetName);
            if (result != null) {
                return result;
            }
            child = child.getNextSibling();
        }

        return null;
    }
}
