package cloud.kitelang.intellij.formatter;

import cloud.kitelang.intellij.KiteLanguage;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.formatting.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
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
                .before(KiteTokenTypes.LBRACE).spaces(1)
                .after(KiteTokenTypes.RBRACE).spaces(1)

            // Brackets - no spaces inside, no space before opening
                .after(KiteTokenTypes.LBRACK).spaces(0)
                .before(KiteTokenTypes.RBRACK).spaces(0)

            // Parentheses
                .after(KiteTokenTypes.LPAREN).spaces(0)
                .before(KiteTokenTypes.RPAREN).spaces(0)

            // Comma - space after, no space before
                .after(KiteTokenTypes.COMMA).spaces(1)
                .before(KiteTokenTypes.COMMA).spaces(0)

            // Assignment operators - spaces around
                .around(KiteTokenTypes.ASSIGN).spaces(1)
                .around(KiteTokenTypes.PLUS_ASSIGN).spaces(1)
                .around(KiteTokenTypes.MINUS_ASSIGN).spaces(1)
                .around(KiteTokenTypes.MUL_ASSIGN).spaces(1)
                .around(KiteTokenTypes.DIV_ASSIGN).spaces(1)

            // Arithmetic operators - spaces around
                .around(KiteTokenTypes.PLUS).spaces(1)
                .around(KiteTokenTypes.MINUS).spaces(1)
                .around(KiteTokenTypes.MULTIPLY).spaces(1)
                .around(KiteTokenTypes.DIVIDE).spaces(1)
                .around(KiteTokenTypes.MODULO).spaces(1)

            // Relational operators - spaces around
                .around(KiteTokenTypes.LT).spaces(1)
                .around(KiteTokenTypes.GT).spaces(1)
                .around(KiteTokenTypes.LE).spaces(1)
                .around(KiteTokenTypes.GE).spaces(1)
                .around(KiteTokenTypes.EQ).spaces(1)
                .around(KiteTokenTypes.NE).spaces(1)

            // Logical operators - spaces around
                .around(KiteTokenTypes.AND).spaces(1)
                .around(KiteTokenTypes.OR).spaces(1)
                .before(KiteTokenTypes.NOT).spaces(0)

            // Other operators
                .around(KiteTokenTypes.ARROW).spaces(1)
                .around(KiteTokenTypes.RANGE).spaces(0)
                .around(KiteTokenTypes.UNION).spaces(1)

            // Colon spacing in object literals
            // Before: 0 spaces by default (alignment handled via alignmentPadding in KiteBlock)
            // After: always 1 space
                .before(KiteTokenTypes.COLON).spaces(0)
                .after(KiteTokenTypes.COLON).spaces(1)

            // Space after semicolon
                .after(KiteTokenTypes.SEMICOLON).spaces(1)

            // Space after keywords
                .after(KiteTokenTypes.RESOURCE).spaces(1)
                .after(KiteTokenTypes.COMPONENT).spaces(1)
                .after(KiteTokenTypes.SCHEMA).spaces(1)
                .after(KiteTokenTypes.INPUT).spaces(1)
                .after(KiteTokenTypes.OUTPUT).spaces(1)
                .after(KiteTokenTypes.FUN).spaces(1)
                .after(KiteTokenTypes.VAR).spaces(1)
                .after(KiteTokenTypes.TYPE).spaces(1)
                .after(KiteTokenTypes.IF).spaces(1)
                .after(KiteTokenTypes.ELSE).spaces(1)
                .after(KiteTokenTypes.FOR).spaces(1)
                .after(KiteTokenTypes.WHILE).spaces(1)
                .after(KiteTokenTypes.RETURN).spaces(1)
                .after(KiteTokenTypes.IN).spaces(1)
                .before(KiteTokenTypes.IN).spaces(1)
                .after(KiteTokenTypes.IMPORT).spaces(1)
                .after(KiteTokenTypes.FROM).spaces(1)

            // No space after @ for decorators
                .after(KiteTokenTypes.AT).spaces(0)

            // No space around dots
                .around(KiteTokenTypes.DOT).spaces(0);
    }
}
