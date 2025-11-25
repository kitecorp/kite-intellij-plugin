package io.kite.intellij.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ProcessingContext;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Reference contributor for Kite language.
 * Provides references for identifiers to enable "Go to Declaration" (Cmd+Click).
 */
public class KiteReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // Register reference provider for leaf elements in Kite files
        // We match LeafPsiElement and filter by IDENTIFIER type in the provider
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(LeafPsiElement.class)
                        .withLanguage(KiteLanguage.INSTANCE),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context) {

                        // Only provide references for IDENTIFIER tokens
                        if (element.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // Create a reference for this identifier
                        TextRange range = new TextRange(0, element.getTextLength());
                        return new PsiReference[]{new KiteReference(element, range)};
                    }
                }
        );
    }
}
