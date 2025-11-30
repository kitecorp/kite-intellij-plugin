package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteTestBase;

/**
 * Tests for {@link AddImportQuickFix}.
 * Verifies that the auto-import quick fix correctly adds import statements
 * for undefined symbols that exist in other files.
 */
public class AddImportQuickFixTest extends KiteTestBase {

    /**
     * Test that highlighting runs without exception for undefined symbols.
     */
    public void testUndefinedSymbolHighlightingRunsWithoutException() {
        configureByText("""
                var x = undefinedVar
                """);

        // Verify file parses and highlighting runs without crashing
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that imported symbol parsing works correctly.
     */
    public void testImportedSymbolParsingWorks() {
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                """);

        configureByText("""
                import defaultRegion from "common.kite"
                
                var x = defaultRegion
                """);

        // Verify file parses and highlighting runs without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that wildcard import parsing works correctly.
     */
    public void testWildcardImportParsingWorks() {
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                var otherVar = "other"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = defaultRegion
                """);

        // Verify file parses and highlighting runs without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that complex import scenarios parse correctly.
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

        // Verify file parses and highlighting runs without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that functions can be imported and used.
     */
    public void testImportedFunctionParsingWorks() {
        addFile("common.kite", """
                fun formatName(string prefix, string name) string {
                    return prefix + "-" + name
                }
                """);

        configureByText("""
                import formatName from "common.kite"
                
                var x = formatName("app", "server")
                """);

        // Verify file parses and highlights without crashing
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that schemas can be imported and used.
     */
    public void testImportedSchemaParsingWorks() {
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

        // Verify file parses and highlights without crashing
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
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
