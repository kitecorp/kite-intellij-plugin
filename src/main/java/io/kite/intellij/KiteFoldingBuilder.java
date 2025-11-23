package io.kite.intellij;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides code folding support for Kite language.
 * Allows collapsing/expanding blocks, comments, arrays, and objects.
 */
public class KiteFoldingBuilder extends FoldingBuilderEx {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();

        // Traverse the PSI tree and collect foldable regions
        collectFoldingRegions(root, document, descriptors);

        return descriptors.toArray(new FoldingDescriptor[0]);
    }

    private void collectFoldingRegions(PsiElement element, Document document, List<FoldingDescriptor> descriptors) {
        ASTNode node = element.getNode();
        if (node == null) return;

        IElementType elementType = node.getElementType();

        // Fold block comments
        if (elementType == KiteTokenTypes.BLOCK_COMMENT) {
            addFoldingDescriptor(element, descriptors, document);
            return; // Don't traverse children of comments
        }

        // Fold declarations that have blocks
        if (elementType == KiteElementTypes.RESOURCE_DECLARATION ||
            elementType == KiteElementTypes.COMPONENT_DECLARATION ||
            elementType == KiteElementTypes.SCHEMA_DECLARATION ||
            elementType == KiteElementTypes.FUNCTION_DECLARATION ||
            elementType == KiteElementTypes.FOR_STATEMENT ||
            elementType == KiteElementTypes.WHILE_STATEMENT) {

            // Find the block range (from { to })
            TextRange range = findBlockRange(node);
            if (range != null) {
                int startLine = document.getLineNumber(range.getStartOffset());
                int endLine = document.getLineNumber(range.getEndOffset());

                if (endLine > startLine) {
                    descriptors.add(new FoldingDescriptor(node, range));
                }
            }
        }

        // Recursively process children
        for (PsiElement child : element.getChildren()) {
            collectFoldingRegions(child, document, descriptors);
        }
    }

    private void addFoldingDescriptor(PsiElement element, List<FoldingDescriptor> descriptors, Document document) {
        TextRange range = element.getTextRange();

        // Only fold regions that span multiple lines
        int startLine = document.getLineNumber(range.getStartOffset());
        int endLine = document.getLineNumber(range.getEndOffset());

        if (endLine > startLine) {
            descriptors.add(new FoldingDescriptor(element.getNode(), range));
        }
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        IElementType elementType = node.getElementType();

        // Block comments
        if (elementType == KiteTokenTypes.BLOCK_COMMENT) {
            return "/*...*/";
        }

        // All other foldable regions (declarations)
        return "{...}";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        // Don't collapse anything by default
        return false;
    }

    /**
     * Finds the block range by matching the first { with its closing } using depth counting on text.
     */
    private TextRange findBlockRange(ASTNode declarationNode) {
        String text = declarationNode.getText();

        // Find first '{'
        int firstBrace = text.indexOf('{');
        if (firstBrace == -1) {
            return null;
        }

        // Use depth counting to find matching '}'
        int depth = 1;
        int pos = firstBrace + 1;

        while (pos < text.length() && depth > 0) {
            char c = text.charAt(pos);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            pos++;
        }

        if (depth != 0) {
            // Didn't find matching brace
            return null;
        }

        // pos is now one past the closing brace
        int startOffset = declarationNode.getStartOffset() + firstBrace;
        int endOffset = declarationNode.getStartOffset() + pos;

        return new TextRange(startOffset, endOffset);
    }
}