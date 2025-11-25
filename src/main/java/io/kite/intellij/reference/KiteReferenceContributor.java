package io.kite.intellij.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Reference contributor for Kite language.
 * Provides references for identifiers to enable "Go to Declaration" (Cmd+Click).
 */
public class KiteReferenceContributor extends PsiReferenceContributor {
    private static final Logger LOG = Logger.getInstance(KiteReferenceContributor.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        LOG.info("[KiteRefContrib] Registering reference providers");

        // Register reference provider for ANY PsiElement, then filter inside
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context) {

                        // Only handle Kite files
                        PsiFile file = element.getContainingFile();
                        if (file == null || file.getLanguage() != KiteLanguage.INSTANCE) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // Only provide references for IDENTIFIER tokens
                        if (element.getNode() == null || element.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        LOG.info("[KiteRefContrib] Creating reference for: " + element.getText());

                        // Create a reference for this identifier
                        TextRange range = new TextRange(0, element.getTextLength());
                        return new PsiReference[]{new KiteReference(element, range)};
                    }
                }
        );
    }
}
