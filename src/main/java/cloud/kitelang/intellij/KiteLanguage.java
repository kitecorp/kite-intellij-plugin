package cloud.kitelang.intellij;

import com.intellij.lang.Language;

/**
 * Kite language definition.
 * This is the entry point for all language-specific features.
 */
public class KiteLanguage extends Language {
    public static final KiteLanguage INSTANCE = new KiteLanguage();

    private KiteLanguage() {
        super("Kite");
    }
}