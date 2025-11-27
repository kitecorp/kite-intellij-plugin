package io.kite.intellij;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Brace matcher for Kite language.
 * Provides matching brace highlighting when cursor is on a bracket, parenthesis, or brace.
 */
public class KiteBraceMatcher implements PairedBraceMatcher {

    private static final BracePair[] PAIRS = new BracePair[]{
            new BracePair(KiteTokenTypes.LBRACE, KiteTokenTypes.RBRACE, true),   // { }
            new BracePair(KiteTokenTypes.LPAREN, KiteTokenTypes.RPAREN, false),  // ( )
            new BracePair(KiteTokenTypes.LBRACK, KiteTokenTypes.RBRACK, false),  // [ ]
    };

    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType,
                                                   @Nullable IElementType contextType) {
        var x = "";
        var y = 2;
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
