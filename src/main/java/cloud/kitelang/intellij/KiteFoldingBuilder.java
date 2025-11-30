package cloud.kitelang.intellij;

import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides code folding support for Kite language.
 * Allows collapsing/expanding blocks, comments, arrays, objects, and imports.
 */
public class KiteFoldingBuilder extends FoldingBuilderEx {

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+[\\w,\\s*]+\\s+from\\s+[\"'][^\"']+[\"']\\s*(?:\\r?\\n)?",
            Pattern.MULTILINE
    );

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();

        // Collect import folding region first
        collectImportFoldingRegion(root, document, descriptors);

        // Traverse the PSI tree and collect foldable regions
        collectFoldingRegions(root, document, descriptors);

        return descriptors.toArray(new FoldingDescriptor[0]);
    }

    /**
     * Collects import statements and creates a single folding region for them.
     */
    private void collectImportFoldingRegion(PsiElement root, Document document, List<FoldingDescriptor> descriptors) {
        if (!(root instanceof PsiFile)) return;

        String text = root.getText();
        Matcher matcher = IMPORT_PATTERN.matcher(text);

        List<int[]> importRanges = new ArrayList<>();
        while (matcher.find()) {
            importRanges.add(new int[]{matcher.start(), matcher.end()});
        }

        // Only fold if there are 2+ imports
        if (importRanges.size() >= 2) {
            int firstStart = importRanges.get(0)[0];
            int lastEnd = importRanges.get(importRanges.size() - 1)[1];

            // Trim trailing newline from range
            while (lastEnd > firstStart && Character.isWhitespace(text.charAt(lastEnd - 1))) {
                lastEnd--;
            }
            lastEnd++; // Include one newline

            TextRange range = new TextRange(firstStart, Math.min(lastEnd, text.length()));
            int importCount = importRanges.size();
            String placeholder = "[" + importCount + " imports...]";

            // Find the first import node to attach the folding descriptor to
            PsiElement firstElement = root.findElementAt(firstStart);
            ASTNode node = firstElement != null ? firstElement.getNode() : root.getNode();

            if (node != null) {
                descriptors.add(new FoldingDescriptor(node, range, null, placeholder));
            }
        }
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
            elementType == KiteElementTypes.WHILE_STATEMENT ||
            elementType == KiteElementTypes.OBJECT_LITERAL) {

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