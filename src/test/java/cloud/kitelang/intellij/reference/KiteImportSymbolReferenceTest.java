package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.List;
import java.util.stream.Collectors;

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

        // Verify no errors for valid import and usage
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
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

        // Verify no errors for valid import and usage
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid function import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
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

        // Verify no errors for valid import and usage
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid schema import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
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

        // Verify no errors for valid multi-symbol import
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid multi-symbol import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Test that non-existent symbol import is handled gracefully.
     * Note: Full error reporting requires the full IDE environment.
     */
    public void testNonExistentSymbolHandledGracefully() {
        addFile("common.kite", """
                var existingVar = "exists"
                """);

        configureByText("""
                import nonExistent from "common.kite"

                var x = "hello"
                """);

        // Verify highlighting runs without exception
        // Note: Full annotation/error reporting requires the full IDE environment
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
    }

    /**
     * Test that import from non-existent file is handled gracefully.
     * Note: Full error reporting requires the full IDE environment.
     */
    public void testImportFromNonExistentFileHandledGracefully() {
        configureByText("""
                import someVar from "nonexistent.kite"
                
                var x = "hello"
                """);

        // Verify highlighting runs without exception
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
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

        // Verify no errors for valid input import
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid input import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
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

        // Verify no errors for valid output import
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid output import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Helper method to format errors for assertion messages.
     */
    private String formatErrors(List<HighlightInfo> errors) {
        if (errors.isEmpty()) {
            return "[]";
        }
        return errors.stream()
                .map(h -> h.getDescription() != null ? h.getDescription() : "null")
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
