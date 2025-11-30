package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.intention.IntentionAction;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link WildcardToNamedImportQuickFix}.
 * Verifies that the quick fix correctly converts wildcard imports to named imports.
 */
public class WildcardToNamedImportQuickFixTest extends KiteTestBase {

    /**
     * Test converting a wildcard import to named imports using only used symbols.
     */
    public void testConvertWildcardToUsedNamedImports() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                var gamma = "g"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = alpha + beta
                """);

        // Find and apply the quick fix
        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Apply the fix
        myFixture.launchAction(fixes.get(0));

        // Verify the wildcard import was converted to named imports (only used symbols)
        String result = myFixture.getFile().getText();
        assertTrue("Should contain named import", result.contains("import"));
        assertFalse("Should not contain wildcard", result.contains("import *"));
        assertTrue("Should contain alpha", result.contains("alpha"));
        assertTrue("Should contain beta", result.contains("beta"));
        assertFalse("Should not contain unused gamma", result.contains("gamma"));
    }

    /**
     * Test converting a wildcard import when only one symbol is used.
     */
    public void testConvertWildcardSingleSymbolUsed() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = alpha
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should contain named import for alpha",
                result.contains("import alpha from \"common.kite\""));
        assertFalse("Should not contain beta", result.contains("beta"));
    }

    /**
     * Test that quick fix text is correct.
     */
    public void testQuickFixText() {
        addFile("common.kite", """
                var alpha = "a"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = alpha
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a fix", fixes.isEmpty());
        assertEquals("Quick fix text should be correct",
                "Convert to named import", fixes.get(0).getText());
    }

    /**
     * Test converting wildcard with function usage.
     */
    public void testConvertWildcardWithFunctionUsage() {
        addFile("common.kite", """
                fun formatName(string name) string {
                    return "formatted-" + name
                }
                var unused = "unused"
                """);

        configureByText("""
                import * from "common.kite"
                
                var result = formatName("test")
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should import formatName",
                result.contains("import formatName from \"common.kite\""));
        assertFalse("Should not contain unused", result.contains("unused"));
    }

    /**
     * Test converting wildcard with schema usage.
     */
    public void testConvertWildcardWithSchemaUsage() {
        addFile("common.kite", """
                schema Config {
                    string name
                }
                var other = "other"
                """);

        configureByText("""
                import * from "common.kite"
                
                resource Config myConfig {
                    name = "test"
                }
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should import Config",
                result.contains("import Config from \"common.kite\""));
    }

    /**
     * Test converting wildcard with string interpolation usage.
     */
    public void testConvertWildcardWithInterpolationUsage() {
        addFile("common.kite", """
                var region = "us-east-1"
                var unused = "unused"
                """);

        configureByText("""
                import * from "common.kite"
                
                var endpoint = "https://$region.api.example.com"
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should import region",
                result.contains("import region from \"common.kite\""));
        assertFalse("Should not contain unused", result.contains("unused"));
    }

    /**
     * Test converting wildcard with ${} interpolation usage.
     */
    public void testConvertWildcardWithBracedInterpolationUsage() {
        addFile("common.kite", """
                var region = "us-east-1"
                var unused = "unused"
                """);

        configureByText("""
                import * from "common.kite"
                
                var endpoint = "https://${region}.api.example.com"
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should import region",
                result.contains("import region from \"common.kite\""));
    }

    /**
     * Test no quick fix when wildcard import is not used (nothing to convert to).
     * Or should convert to empty/remove? Let's verify current behavior.
     */
    public void testNoConversionWhenNothingUsed() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = "nothing from import used"
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        // When nothing is used, the quick fix should either:
        // - Not be available, OR
        // - Remove the import entirely
        // For now, let's say it shouldn't be available when nothing is used
        assertTrue("Should not have quick fix when nothing is used", fixes.isEmpty());
    }

    /**
     * Test that named imports are sorted alphabetically.
     */
    public void testNamedImportsAreSorted() {
        addFile("common.kite", """
                var zebra = "z"
                var alpha = "a"
                var mike = "m"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = zebra + alpha + mike
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        // Symbols should be sorted: alpha, mike, zebra
        assertTrue("Should have sorted imports",
                result.contains("import alpha, mike, zebra from \"common.kite\""));
    }

    /**
     * Test quick fix is not available for named imports.
     */
    public void testNotAvailableForNamedImports() {
        addFile("common.kite", """
                var alpha = "a"
                """);

        configureByText("""
                import alpha from "common.kite"
                
                var x = alpha
                """);

        List<IntentionAction> fixes = getWildcardToNamedFixes();
        assertTrue("Should not have quick fix for named imports", fixes.isEmpty());
    }

    /**
     * Helper to get intention actions for converting wildcard to named imports.
     */
    private List<IntentionAction> getWildcardToNamedFixes() {
        myFixture.doHighlighting();
        return myFixture.getAllQuickFixes().stream()
                .filter(action -> action.getText().contains("Convert to named import"))
                .collect(Collectors.toList());
    }
}
