package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteLanguage;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;

/**
 * Orchestrates code completion for the Kite language.
 *
 * This contributor registers specialized completion providers for different contexts:
 * - KiteImportPathCompletionProvider: File path completion in import statements
 * - KiteDecoratorCompletionProvider: Decorator names and arguments
 * - KiteResourceCompletionProvider: Property names and values inside resource blocks
 * - KiteComponentDefinitionCompletionProvider: input/output in component definitions
 * - KiteGeneralCompletionProvider: Keywords, identifiers, property access, types
 *
 * Each provider handles its own context detection and filtering.
 */
public class KiteCompletionContributor extends CompletionContributor {

    public KiteCompletionContributor() {
        // Import path completion provider - suggests .kite files in import strings
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(KiteLanguage.INSTANCE),
                new KiteImportPathCompletionProvider());

        // Decorator completion provider - handles @ and decorator arguments
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(KiteLanguage.INSTANCE),
                new KiteDecoratorCompletionProvider());

        // Resource completion provider - handles property names and values inside resource blocks
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(KiteLanguage.INSTANCE),
                new KiteResourceCompletionProvider());

        // Component definition completion provider - handles input/output in component definitions
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(KiteLanguage.INSTANCE),
                new KiteComponentDefinitionCompletionProvider());

        // General completion provider - keywords, identifiers, property access, types
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(KiteLanguage.INSTANCE),
                new KiteGeneralCompletionProvider());
    }
}
