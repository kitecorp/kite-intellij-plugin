package io.kite.intellij.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import io.kite.intellij.KiteLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the formatting model for Kite language files.
 * This is the main entry point for code formatting via "Reformat Code" action.
 */
public class KiteFormattingModelBuilder implements FormattingModelBuilder {

    @NotNull
    @Override
    public FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
        PsiElement element = formattingContext.getPsiElement();

        SpacingBuilder spacingBuilder = createSpaceBuilder(settings);
        KiteBlock rootBlock = new KiteBlock(
            element.getNode(),
            null,
            Alignment.createAlignment(),
            Indent.getNoneIndent(),
            spacingBuilder
        );

        return FormattingModelProvider.createFormattingModelForPsiFile(
            element.getContainingFile(),
            rootBlock,
            settings
        );
    }

    /**
     * Creates spacing rules for Kite language.
     */
    private SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, KiteLanguage.INSTANCE)
            // Space before opening brace
            .before(io.kite.intellij.psi.KiteTokenTypes.LBRACE).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.RBRACE).spaces(1)

            // Brackets - no spaces inside, no space before opening
            .after(io.kite.intellij.psi.KiteTokenTypes.LBRACK).spaces(0)
            .before(io.kite.intellij.psi.KiteTokenTypes.RBRACK).spaces(0)

            // Parentheses
            .after(io.kite.intellij.psi.KiteTokenTypes.LPAREN).spaces(0)
            .before(io.kite.intellij.psi.KiteTokenTypes.RPAREN).spaces(0)

            // Comma - space after, no space before
            .after(io.kite.intellij.psi.KiteTokenTypes.COMMA).spaces(1)
            .before(io.kite.intellij.psi.KiteTokenTypes.COMMA).spaces(0)

            // Assignment operators - spaces around
            .around(io.kite.intellij.psi.KiteTokenTypes.ASSIGN).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.PLUS_ASSIGN).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.MINUS_ASSIGN).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.MUL_ASSIGN).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.DIV_ASSIGN).spaces(1)

            // Arithmetic operators - spaces around
            .around(io.kite.intellij.psi.KiteTokenTypes.PLUS).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.MINUS).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.MULTIPLY).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.DIVIDE).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.MODULO).spaces(1)

            // Relational operators - spaces around
            .around(io.kite.intellij.psi.KiteTokenTypes.LT).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.GT).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.LE).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.GE).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.EQ).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.NE).spaces(1)

            // Logical operators - spaces around
            .around(io.kite.intellij.psi.KiteTokenTypes.AND).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.OR).spaces(1)
            .before(io.kite.intellij.psi.KiteTokenTypes.NOT).spaces(0)

            // Other operators
            .around(io.kite.intellij.psi.KiteTokenTypes.ARROW).spaces(1)
            .around(io.kite.intellij.psi.KiteTokenTypes.RANGE).spaces(0)
            .around(io.kite.intellij.psi.KiteTokenTypes.UNION).spaces(1)

            // Colon spacing in object literals
            // Before: 0 spaces by default, up to 30 for alignment
            // After: always 1 space
            .before(io.kite.intellij.psi.KiteTokenTypes.COLON).spacing(0, 30, 0, true, 0)
            .after(io.kite.intellij.psi.KiteTokenTypes.COLON).spaces(1)

            // Space after semicolon
            .after(io.kite.intellij.psi.KiteTokenTypes.SEMICOLON).spaces(1)

            // Space after keywords
            .after(io.kite.intellij.psi.KiteTokenTypes.RESOURCE).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.COMPONENT).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.SCHEMA).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.INPUT).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.OUTPUT).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.FUN).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.VAR).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.TYPE).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.IF).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.ELSE).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.FOR).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.WHILE).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.RETURN).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.IN).spaces(1)
            .before(io.kite.intellij.psi.KiteTokenTypes.IN).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.IMPORT).spaces(1)
            .after(io.kite.intellij.psi.KiteTokenTypes.FROM).spaces(1)

            // No space after @ for decorators
            .after(io.kite.intellij.psi.KiteTokenTypes.AT).spaces(0)

            // No space around dots
            .around(io.kite.intellij.psi.KiteTokenTypes.DOT).spaces(0);
    }
}
