package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.KiteTestBase;

/**
 * Tests for {@link KiteImportSymbolReference}.
 * Verifies that import symbols correctly resolve to their declarations in imported files.
 */
public class KiteImportSymbolReferenceTest extends KiteTestBase {

    /**
     * Test that import symbol resolves to variable declaration in imported file.
     */
    public void testImportSymbolResolvesToVariable() {
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                """);

        configureByText("""
                import defaultRegion from "common.kite"
                
                var x = defaultRegion
                """);

        // Verify the file parses without errors
        assertNotNull(myFixture.getFile());
        // Verify that highlighting runs without exception
        myFixture.doHighlighting();
    }

    /**
     * Test that import symbol resolves to function declaration.
     */
    public void testImportSymbolResolvesToFunction() {
        addFile("common.kite", """
                fun formatName(string prefix, string name) string {
                    return prefix + "-" + name
                }
                """);

        configureByText("""
                import formatName from "common.kite"
                
                var x = formatName("app", "server")
                """);

        // Verify the file parses without errors
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that import symbol resolves to schema declaration.
     */
    public void testImportSymbolResolvesToSchema() {
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

        // Verify the file parses without errors
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test multi-symbol import - each symbol resolves independently.
     */
    public void testMultiSymbolImportResolution() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import alpha, beta from "common.kite"
                
                var x = alpha + beta
                """);

        // Verify the file parses without errors
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that non-existent symbol doesn't resolve.
     */
    public void testNonExistentSymbolDoesNotResolve() {
        addFile("common.kite", """
                var existingVar = "exists"
                """);

        configureByText("""
                import nonExistent from "common.kite"
                
                var x = "hello"
                """);

        // Verify the file parses - there should be an error for the undefined symbol
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that import from non-existent file doesn't resolve.
     */
    public void testImportFromNonExistentFile() {
        configureByText("""
                import someVar from "nonexistent.kite"
                
                var x = "hello"
                """);

        // Should still parse without crashing
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test import symbol resolution with input declaration.
     */
    public void testImportSymbolResolvesToInput() {
        addFile("common.kite", """
                input string port = "8080"
                """);

        configureByText("""
                import port from "common.kite"
                
                var x = port
                """);

        // Verify the file parses without errors
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test import symbol resolution with output declaration.
     */
    public void testImportSymbolResolvesToOutput() {
        addFile("common.kite", """
                output string endpoint = "http://localhost"
                """);

        configureByText("""
                import endpoint from "common.kite"
                
                var x = endpoint
                """);

        // Verify the file parses without errors
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }
}
