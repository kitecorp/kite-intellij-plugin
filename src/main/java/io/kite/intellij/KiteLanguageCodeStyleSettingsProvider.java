package io.kite.intellij;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Provides code style settings for the Kite language.
 */
public class KiteLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    @NotNull
    @Override
    public Language getLanguage() {
        return KiteLanguage.INSTANCE;
    }

    @Override
    public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
        if (settingsType == SettingsType.SPACING_SETTINGS) {
            consumer.showStandardOptions("SPACE_AROUND_ASSIGNMENT_OPERATORS");
            consumer.showStandardOptions("SPACE_AROUND_RELATIONAL_OPERATORS");
            consumer.showStandardOptions("SPACE_AROUND_LOGICAL_OPERATORS");
            consumer.showStandardOptions("SPACE_AROUND_ADDITIVE_OPERATORS");
            consumer.showStandardOptions("SPACE_AROUND_MULTIPLICATIVE_OPERATORS");
        } else if (settingsType == SettingsType.INDENT_SETTINGS) {
            consumer.showStandardOptions("INDENT_SIZE");
            consumer.showStandardOptions("CONTINUATION_INDENT_SIZE");
            consumer.showStandardOptions("TAB_SIZE");
            consumer.showStandardOptions("USE_TAB_CHARACTER");
        }
    }

    @Override
    public IndentOptionsEditor getIndentOptionsEditor() {
        return new SmartIndentOptionsEditor();
    }

    @Override
    public CommonCodeStyleSettings getDefaultCommonSettings() {
        CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(getLanguage());
        CommonCodeStyleSettings.IndentOptions indentOptions = defaultSettings.initIndentOptions();
        indentOptions.INDENT_SIZE = 4;
        indentOptions.CONTINUATION_INDENT_SIZE = 8;
        indentOptions.TAB_SIZE = 4;
        indentOptions.USE_TAB_CHARACTER = false;
        indentOptions.SMART_TABS = false;
        return defaultSettings;
    }

    @Override
    public void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                  @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
        indentOptions.INDENT_SIZE = 4;
        indentOptions.CONTINUATION_INDENT_SIZE = 8;
        indentOptions.TAB_SIZE = 4;
        indentOptions.USE_TAB_CHARACTER = false;
        indentOptions.SMART_TABS = false;
    }

    @Override
    public String getCodeSample(@NotNull SettingsType settingsType) {
        return """
                resource WebServer api {
                    name = "my-server"
                    port = 8080
                }

                component Database db {
                    input string host = "localhost"
                    input number port = 5432

                    var string connString = host + ":" + port

                    output string endpoint = connString
                }
                """;
    }
}
