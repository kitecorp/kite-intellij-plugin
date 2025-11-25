package io.kite.intellij.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import io.kite.intellij.KiteLanguage;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

                        // Don't provide references for declaration names (the name being declared)
                        // e.g., in "input number port = 8080", "port" is a declaration name, not a reference
                        if (isDeclarationName(element)) {
                            LOG.info("[KiteRefContrib] Skipping declaration name: " + element.getText());
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

    /**
     * Check if this identifier is a declaration name (the name being declared, not a reference).
     * Declaration names are:
     * - The last identifier before = or { in input/output/var/resource/component/schema/function/type declarations
     * - The identifier after "for" keyword in for loops
     * - Property names in object literals (identifier before : or =)
     */
    private static boolean isDeclarationName(@NotNull PsiElement element) {
        // Check if this identifier is followed by = or { or += (declaration pattern)
        PsiElement next = skipWhitespaceForward(element.getNextSibling());
        if (next != null) {
            IElementType nextType = next.getNode().getElementType();
            if (nextType == KiteTokenTypes.ASSIGN ||
                nextType == KiteTokenTypes.LBRACE ||
                nextType == KiteTokenTypes.PLUS_ASSIGN ||
                nextType == KiteTokenTypes.COLON) {
                // This identifier is followed by = or { or : - it's a declaration/property name
                return true;
            }
        }

        // Check if this is a for loop variable (identifier after "for" keyword)
        PsiElement prev = skipWhitespaceBackward(element.getPrevSibling());
        if (prev != null && prev.getNode().getElementType() == KiteTokenTypes.FOR) {
            return true;
        }

        // Check if parent is a declaration and this is the declared name
        PsiElement parent = element.getParent();
        if (parent != null) {
            IElementType parentType = parent.getNode().getElementType();
            if (isDeclarationType(parentType)) {
                // Find the name element in this declaration
                PsiElement nameElement = findNameInDeclaration(parent, parentType);
                if (nameElement == element) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isDeclarationType(IElementType type) {
        return type == KiteElementTypes.VARIABLE_DECLARATION ||
               type == KiteElementTypes.INPUT_DECLARATION ||
               type == KiteElementTypes.OUTPUT_DECLARATION ||
               type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.TYPE_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT;
    }

    /**
     * Find the name identifier within a declaration.
     */
    @Nullable
    private static PsiElement findNameInDeclaration(PsiElement declaration, IElementType declarationType) {
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
                if (lastIdentifier != null) {
                    return lastIdentifier;
                }
            }
            child = child.getNextSibling();
        }

        return lastIdentifier;
    }

    @Nullable
    private static PsiElement skipWhitespaceForward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getNextSibling();
        }
        return element;
    }

    @Nullable
    private static PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null && isWhitespace(element.getNode().getElementType())) {
            element = element.getPrevSibling();
        }
        return element;
    }

    private static boolean isWhitespace(IElementType type) {
        return type == TokenType.WHITE_SPACE ||
               type == KiteTokenTypes.WHITESPACE ||
               type == KiteTokenTypes.NL ||
               type == KiteTokenTypes.NEWLINE;
    }
}
