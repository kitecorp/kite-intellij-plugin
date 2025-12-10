package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.structure.KiteStructureViewIcons;
import cloud.kitelang.intellij.util.KiteDeclarationHelper;
import cloud.kitelang.intellij.util.KiteIndexedResourceHelper;
import cloud.kitelang.intellij.util.KiteIndexedResourceHelper.IndexedResourceInfo;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides code completion inside indexed access brackets.
 * Suggests valid indices for indexed resources and components:
 * - Numeric indices for @count decorated resources (0, 1, 2, ...)
 * - Numeric indices for for-loop range resources (0, 1, 2, ...)
 * - String keys for for-loop array resources ("dev", "prod", ...)
 */
public class KiteIndexCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile file = parameters.getOriginalFile();

        // Check if we're inside brackets after an identifier
        BracketContext bracketContext = findBracketContext(position);
        if (bracketContext == null) {
            return;
        }

        // Find the declaration for the base identifier
        PsiElement declaration = findDeclarationByName(file, bracketContext.baseName);
        if (declaration == null) {
            return;
        }

        // Check if it's an indexed resource/component
        IndexedResourceInfo indexedInfo = KiteIndexedResourceHelper.getIndexedInfo(declaration);
        if (indexedInfo == null) {
            return; // Not an indexed resource
        }

        // Add completions for valid indices
        addIndexCompletions(result, indexedInfo);
    }

    /**
     * Add completions for valid indices based on the indexed resource info.
     */
    private void addIndexCompletions(@NotNull CompletionResultSet result, IndexedResourceInfo info) {
        var validIndices = info.getValidIndices();
        Icon icon = info.indexType() == KiteIndexedResourceHelper.IndexType.NUMERIC
                ? KiteStructureViewIcons.VARIABLE
                : KiteStructureViewIcons.PROPERTY;

        for (int i = 0; i < validIndices.size(); i++) {
            String indexStr = validIndices.get(i);
            String typeText = info.indexType() == KiteIndexedResourceHelper.IndexType.NUMERIC
                    ? "index"
                    : "key";

            LookupElementBuilder element = LookupElementBuilder.create(indexStr)
                    .withTypeText(typeText)
                    .withIcon(icon)
                    .withBoldness(true);

            // Higher priority for lower indices
            double priority = 100.0 - i;
            result.addElement(PrioritizedLookupElement.withPriority(element, priority));
        }
    }

    /**
     * Find the bracket context if we're inside a bracket access expression.
     */
    @Nullable
    private BracketContext findBracketContext(PsiElement position) {
        // Look backwards to find [ and then the identifier before it
        PsiElement current = position;

        // Skip the dummy identifier inserted by completion
        if (current != null && current.getText().contains("IntellijIdeaRulezzz")) {
            current = skipWhitespaceBackward(current.getPrevSibling());
        }

        // Look for the LBRACK
        PsiElement lbrack = findPrecedingLBrack(current);
        if (lbrack == null) {
            // Try from parent
            if (position.getParent() != null) {
                lbrack = findPrecedingLBrack(position.getParent());
            }
        }

        if (lbrack == null) {
            return null;
        }

        // Find identifier before [
        PsiElement identifier = skipWhitespaceBackward(lbrack.getPrevSibling());
        if (identifier == null || identifier.getNode() == null ||
                identifier.getNode().getElementType() != KiteTokenTypes.IDENTIFIER) {
            return null;
        }

        return new BracketContext(identifier.getText(), identifier, lbrack);
    }

    /**
     * Find the preceding LBRACK token.
     */
    @Nullable
    private PsiElement findPrecedingLBrack(@Nullable PsiElement element) {
        while (element != null) {
            if (element.getNode() != null) {
                IElementType type = element.getNode().getElementType();

                // Found the [
                if (type == KiteTokenTypes.LBRACK) {
                    return element;
                }

                // Stop at ]
                if (type == KiteTokenTypes.RBRACK) {
                    return null;
                }

                // Stop at newlines - we went too far back
                if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                    return null;
                }
            }
            element = element.getPrevSibling();
        }
        return null;
    }

    /**
     * Skip whitespace backward.
     */
    @Nullable
    private PsiElement skipWhitespaceBackward(@Nullable PsiElement element) {
        while (element != null) {
            if (element.getNode() != null) {
                IElementType type = element.getNode().getElementType();
                if (type != KiteTokenTypes.WHITESPACE &&
                        type != TokenType.WHITE_SPACE) {
                    return element;
                }
            }
            element = element.getPrevSibling();
        }
        return null;
    }

    /**
     * Find a declaration by name in the file.
     */
    @Nullable
    private PsiElement findDeclarationByName(PsiFile file, String name) {
        return findDeclarationRecursive(file, name);
    }

    /**
     * Recursively find a declaration by name.
     */
    @Nullable
    private PsiElement findDeclarationRecursive(PsiElement element, String name) {
        for (PsiElement child : element.getChildren()) {
            if (child.getNode() != null) {
                IElementType type = child.getNode().getElementType();

                if (type == KiteElementTypes.RESOURCE_DECLARATION ||
                        type == KiteElementTypes.COMPONENT_DECLARATION) {
                    String declName = KiteDeclarationHelper.findNameInDeclaration(child, type);
                    if (name.equals(declName)) {
                        return child;
                    }
                }

                // Search in for-loops and other containers
                if (type == KiteElementTypes.FOR_STATEMENT ||
                        type == KiteElementTypes.WHILE_STATEMENT) {
                    PsiElement found = findDeclarationRecursive(child, name);
                    if (found != null) {
                        return found;
                    }
                }
            }

            // Also check children that might not have their own node type
            PsiElement found = findDeclarationRecursive(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Context information for bracket access.
     */
    record BracketContext(String baseName, PsiElement identifier, PsiElement lbrack) {
    }
}
