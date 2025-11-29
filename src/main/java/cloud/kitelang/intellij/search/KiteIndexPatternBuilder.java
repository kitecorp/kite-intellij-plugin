package cloud.kitelang.intellij.search;

import cloud.kitelang.intellij.lexer.KiteLexerAdapter;
import cloud.kitelang.intellij.psi.KiteFile;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Index pattern builder for Kite language.
 * Enables TODO/FIXME highlighting in comments.
 * <p>
 * This tells the IntelliJ platform how to find TODO patterns
 * within Kite comment tokens.
 */
public class KiteIndexPatternBuilder implements IndexPatternBuilder {

    private static final TokenSet COMMENT_TOKENS = TokenSet.create(
            KiteTokenTypes.LINE_COMMENT,
            KiteTokenTypes.BLOCK_COMMENT
    );

    @Nullable
    @Override
    public Lexer getIndexingLexer(@NotNull PsiFile file) {
        if (file instanceof KiteFile) {
            return new KiteLexerAdapter();
        }
        return null;
    }

    @Nullable
    @Override
    public TokenSet getCommentTokenSet(@NotNull PsiFile file) {
        if (file instanceof KiteFile) {
            return COMMENT_TOKENS;
        }
        return null;
    }

    @Override
    public int getCommentStartDelta(IElementType tokenType) {
        // Skip the comment prefix characters
        if (tokenType == KiteTokenTypes.LINE_COMMENT) {
            // Line comments start with "//"
            return 2;
        }
        if (tokenType == KiteTokenTypes.BLOCK_COMMENT) {
            // Block comments start with "/*"
            return 2;
        }
        return 0;
    }

    @Override
    public int getCommentEndDelta(IElementType tokenType) {
        // Skip the comment suffix characters (only for block comments)
        if (tokenType == KiteTokenTypes.BLOCK_COMMENT) {
            // Block comments end with "*/"
            return 2;
        }
        // Line comments have no suffix
        return 0;
    }

    @NotNull
    @Override
    public String getCharsAllowedInContinuationPrefix(@NotNull IElementType tokenType) {
        // Characters that can appear between comment start and TODO pattern
        // For block comments, allow whitespace and asterisks (common in multi-line comments)
        if (tokenType == KiteTokenTypes.BLOCK_COMMENT) {
            return " \t*";
        }
        // For line comments, just whitespace
        return " \t";
    }
}
