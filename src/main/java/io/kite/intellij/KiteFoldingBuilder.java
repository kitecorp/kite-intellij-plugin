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
            elementType == KiteElementTypes.FUNCTION_DECLARATION) {

            // Find LBRACE and RBRACE tokens within this declaration
            ASTNode lbrace = findFirstTokenOfType(node, KiteTokenTypes.LBRACE);
            if (lbrace != null) {
                ASTNode rbrace = findMatchingRBrace(lbrace);
                if (rbrace != null) {
                    TextRange range = new TextRange(lbrace.getStartOffset(), rbrace.getStartOffset() + 1);
                    int startLine = document.getLineNumber(range.getStartOffset());
                    int endLine = document.getLineNumber(range.getEndOffset());

                    if (endLine > startLine) {
                        descriptors.add(new FoldingDescriptor(node, range));
                    }
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
     * Finds the first token of the specified type within an AST node.
     */
    private ASTNode findFirstTokenOfType(ASTNode node, IElementType type) {
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getElementType() == type) {
                return child;
            }
            ASTNode found = findFirstTokenOfType(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Finds the matching RBRACE for the given LBRACE token by counting brace depth.
     */
    private ASTNode findMatchingRBrace(ASTNode lbrace) {
        int depth = 1;
        ASTNode current = lbrace.getTreeNext();

        while (current != null && depth > 0) {
            IElementType type = current.getElementType();
            if (type == KiteTokenTypes.LBRACE) {
                depth++;
            } else if (type == KiteTokenTypes.RBRACE) {
                depth--;
                if (depth == 0) {
                    return current;
                }
            }

            // Recursively search in children
            if (current.getFirstChildNode() != null) {
                ASTNode childResult = findMatchingRBraceRecursive(current.getFirstChildNode(), depth);
                if (childResult != null) {
                    return childResult;
                }
            }

            current = current.getTreeNext();
        }

        return null;
    }

    /**
     * Helper for recursive brace matching within a subtree.
     */
    private ASTNode findMatchingRBraceRecursive(ASTNode node, int initialDepth) {
        int depth = initialDepth;
        ASTNode current = node;

        while (current != null && depth > 0) {
            IElementType type = current.getElementType();
            if (type == KiteTokenTypes.LBRACE) {
                depth++;
            } else if (type == KiteTokenTypes.RBRACE) {
                depth--;
                if (depth == 0) {
                    return current;
                }
            }

            // Recursively search in children
            if (current.getFirstChildNode() != null) {
                ASTNode childResult = findMatchingRBraceRecursive(current.getFirstChildNode(), depth);
                if (childResult != null) {
                    return childResult;
                }
            }

            current = current.getTreeNext();
        }

        return null;
    }
}