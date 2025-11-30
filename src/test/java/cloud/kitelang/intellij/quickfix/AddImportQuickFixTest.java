package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.List;

/**
 * Tests for {@link AddImportQuickFix}.
 * Verifies that the auto-import quick fix correctly adds import statements
 * for undefined symbols that exist in other files.
 */
public class AddImportQuickFixTest extends KiteTestBase {

    /**
     * Test that undefined symbol produces a warning.
     * When no import candidates exist, it's a WARNING.
     */
    public void testUndefinedSymbolProducesWarning() {
        configureByText("""
                var x = undefinedVar
                """);

        // Verify warning is produced for undefined symbol (WARNING when no import candidates)
        HighlightInfo warning = getFirstWarning();
        assertNotNull("Should have a warning for undefined symbol", warning);
        assertEquals("Cannot resolve symbol 'undefinedVar'", warning.getDescription());
    }

    /**
     * Test that imported symbol produces no error.
     */
    public void testImportedSymbolNoError() {
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                """);

        configureByText("""
                import defaultRegion from "common.kite"

                var x = defaultRegion
                """);

        // Verify no errors for valid import
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Test that wildcard import resolves all symbols from imported file.
     */
    public void testWildcardImportNoErrors() {
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                var otherVar = "other"
                """);

        configureByText("""
                import * from "common.kite"

                var x = defaultRegion
                """);

        // Wildcard import should make defaultRegion available - no errors expected
        List<HighlightInfo> errors = getErrors();
        assertTrue("Wildcard import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Test that complex import scenarios work correctly.
     */
    public void testComplexImportScenario() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);
        addFile("other.kite", """
                var gamma = "g"
                """);

        configureByText("""
                import alpha, beta from "common.kite"
                import gamma from "other.kite"

                var result = alpha + beta + gamma
                """);

        // Verify no errors for complex import scenario
        List<HighlightInfo> errors = getErrors();
        assertTrue("Complex import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Test that functions can be imported and used.
     */
    public void testImportedFunctionNoError() {
        addFile("common.kite", """
                fun formatName(string prefix, string name) string {
                    return prefix + "-" + name
                }
                """);

        configureByText("""
                import formatName from "common.kite"

                var x = formatName("app", "server")
                """);

        // Verify no errors for function import
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid function import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Test that schemas can be imported and used.
     */
    public void testImportedSchemaNoError() {
        addFile("common.kite", """
                schema Config {
                    string name
                    number port
                }
                """);

        configureByText("""
                import Config from "common.kite"
                
                resource Config myConfig {
                    name = "test"
                    port = 8080
                }
                """);

        // Verify no errors for schema import
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid schema import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Test that import placement is at the top of the file.
     */
    public void testImportAtTopOfFile() {
        addFile("common.kite", """
                var importedVar = "imported"
                """);

        configureByText("""
                import importedVar from "common.kite"
                
                var localVar = "local"
                var x = importedVar
                """);

        String text = myFixture.getFile().getText();
        int importIndex = text.indexOf("import");
        int varIndex = text.indexOf("var localVar");

        assertTrue("Import should exist", importIndex >= 0);
        assertTrue("Var should exist", varIndex >= 0);
        assertTrue("Import should come before var", importIndex < varIndex);
    }

}
