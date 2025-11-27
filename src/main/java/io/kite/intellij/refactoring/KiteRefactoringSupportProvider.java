package io.kite.intellij.refactoring;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Refactoring support provider for Kite language.
 * Enables rename refactoring for identifiers.
 */
public class KiteRefactoringSupportProvider extends RefactoringSupportProvider {

    @Override
    public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, @Nullable PsiElement context) {
        // Enable in-place rename for identifier tokens
        IElementType elementType = element.getNode().getElementType();
        return elementType == KiteTokenTypes.IDENTIFIER ||
               elementType == KiteTokenTypes.INTERP_SIMPLE;
    }

    @Override
    public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
        // Enable in-place rename for identifier tokens
        IElementType elementType = element.getNode().getElementType();
        return elementType == KiteTokenTypes.IDENTIFIER ||
               elementType == KiteTokenTypes.INTERP_SIMPLE;
    }
}
