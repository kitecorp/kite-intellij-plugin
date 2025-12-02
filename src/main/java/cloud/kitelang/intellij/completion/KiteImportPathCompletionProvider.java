package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteFileType;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import cloud.kitelang.intellij.reference.KiteImportHelper;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Provides path completion for import statements.
 * Suggests .kite files when typing inside import path strings.
 */
public class KiteImportPathCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile containingFile = parameters.getOriginalFile();

        // Check if we're inside an import path string
        if (!isInsideImportPathString(position)) {
            return;
        }

        Project project = containingFile.getProject();
        PsiManager psiManager = PsiManager.getInstance(project);

        // Get current file to exclude it
        VirtualFile currentVFile = containingFile.getVirtualFile();

        // Use FileTypeIndex to find all .kite files (works in tests too)
        Collection<VirtualFile> kiteFiles = FileTypeIndex.getFiles(
                KiteFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        for (VirtualFile vFile : kiteFiles) {
            // Skip the current file
            if (currentVFile != null && vFile.equals(currentVFile)) {
                continue;
            }

            PsiFile kiteFile = psiManager.findFile(vFile);
            if (kiteFile == null) continue;

            // Get the relative import path
            String importPath = KiteImportHelper.getRelativeImportPath(containingFile, kiteFile);
            if (importPath == null || importPath.isEmpty()) continue;

            // Create lookup element with file icon and custom insert handler
            // to replace entire string content instead of just inserting at cursor
            LookupElementBuilder element = LookupElementBuilder.create(importPath)
                    .withIcon(AllIcons.FileTypes.Any_type)
                    .withTypeText("Kite file")
                    .withInsertHandler(createImportPathInsertHandler());

            result.addElement(element);
        }
    }

    /**
     * Creates an insert handler that replaces the entire string content (between quotes)
     * with the selected import path. This ensures that partial text like "ga" gets fully
     * replaced with "gamma.kite" instead of appending to create "gamma.kitea".
     */
    private InsertHandler<LookupElement> createImportPathInsertHandler() {
        return (context, item) -> {
            Editor editor = context.getEditor();
            Document document = editor.getDocument();
            int tailOffset = context.getTailOffset();
            String text = document.getText();

            // Find the opening and closing quote of the string
            int openQuoteOffset = findOpeningQuote(text, tailOffset);
            int closeQuoteOffset = findClosingQuote(text, tailOffset);

            if (openQuoteOffset < 0 || closeQuoteOffset < 0) {
                return; // Can't find quotes, do nothing
            }

            // The completion has already been inserted at the cursor position.
            // We need to remove any leftover text between the end of the completion
            // and the closing quote.
            // Also remove any prefix text that was before the cursor.

            // Calculate what's between the quotes now
            String lookupString = item.getLookupString();
            int contentStart = openQuoteOffset + 1;

            // Replace everything between quotes with just the lookup string
            if (closeQuoteOffset > contentStart) {
                document.replaceString(contentStart, closeQuoteOffset, lookupString);
                // Move caret to end of the inserted path (before closing quote)
                editor.getCaretModel().moveToOffset(contentStart + lookupString.length());
            }
        };
    }

    /**
     * Find the opening quote before the given offset.
     */
    private int findOpeningQuote(String text, int offset) {
        for (int i = offset - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                return i;
            }
            // Stop if we hit a newline
            if (c == '\n') {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Find the closing quote after the given offset.
     */
    private int findClosingQuote(String text, int offset) {
        for (int i = offset; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                return i;
            }
            // Stop if we hit a newline
            if (c == '\n') {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Check if the position is inside an import path string.
     * Uses PSI structure to detect if we're inside an IMPORT_STATEMENT
     * and in a string context (after "from" keyword).
     *
     * This is a public static method so other completion providers can check
     * and skip when we're in import path context.
     */
    public static boolean isInsideImportPathString(@NotNull PsiElement position) {
        // First, check if we're inside an IMPORT_STATEMENT by walking up the tree
        PsiElement importStatement = findParentImportStatement(position);
        if (importStatement == null) {
            return false;
        }

        // Check if the position is in a string token type or inside a string literal context
        IElementType elementType = getElementType(position);

        // Check for string-related token types
        if (isStringToken(elementType)) {
            return true;
        }

        // Also check if we're at a dummy identifier position inside a string
        // IntelliJ replaces <caret> with "IntellijIdeaRulezzz"
        String text = position.getText();
        if (text != null && text.contains("IntellijIdeaRulezzz")) {
            return true;
        }

        // Check if parent or surrounding elements indicate string context
        PsiElement parent = position.getParent();
        if (parent != null) {
            IElementType parentType = getElementType(parent);
            if (isStringToken(parentType)) {
                return true;
            }
        }

        // Check siblings for quote tokens to detect string context
        return hasQuoteSiblings(position);
    }

    /**
     * Walk up the PSI tree to find an IMPORT_STATEMENT ancestor.
     */
    @Nullable
    private static PsiElement findParentImportStatement(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile)) {
            if (current.getNode() != null) {
                IElementType type = current.getNode().getElementType();
                if (type == KiteElementTypes.IMPORT_STATEMENT) {
                    return current;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Get the element type of a PSI element.
     */
    @Nullable
    private static IElementType getElementType(@NotNull PsiElement element) {
        if (element.getNode() != null) {
            return element.getNode().getElementType();
        }
        return null;
    }

    /**
     * Check if the element type is a string-related token.
     */
    private static boolean isStringToken(@Nullable IElementType type) {
        if (type == null) return false;
        return type == KiteTokenTypes.STRING ||
               type == KiteTokenTypes.STRING_TEXT ||
               type == KiteTokenTypes.SINGLE_STRING ||
               type == KiteTokenTypes.DQUOTE ||
               type == KiteTokenTypes.STRING_DQUOTE;
    }

    /**
     * Check if siblings include quote tokens (indicating we're inside a string).
     */
    private static boolean hasQuoteSiblings(@NotNull PsiElement element) {
        PsiElement prev = element.getPrevSibling();
        while (prev != null) {
            IElementType type = getElementType(prev);
            if (type == KiteTokenTypes.DQUOTE) {
                return true;
            }
            // Also check for single string as a whole token
            if (type == KiteTokenTypes.SINGLE_STRING) {
                return true;
            }
            prev = prev.getPrevSibling();
        }
        return false;
    }
}
