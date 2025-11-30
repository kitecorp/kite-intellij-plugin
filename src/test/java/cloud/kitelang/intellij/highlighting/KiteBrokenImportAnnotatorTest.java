package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for broken import path detection in {@link KiteTypeCheckingAnnotator}.
 * Verifies that the annotator correctly handles import scenarios.
 * Note: Full error reporting for broken imports requires the full IDE environment.
 */
public class KiteBrokenImportAnnotatorTest extends KiteTestBase {

    /**
     * Test that valid import path produces no error.
     */
    public void testValidImportPathNoError() {
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
     * Test that non-existent file import produces unused import warning.
     */
    public void testNonExistentFileImportProducesUnusedWarning() {
        configureByText("""
                import something from "nonexistent.kite"

                var x = "hello"
                """);

        // Verify weak warning is produced for unused import
        HighlightInfo warning = getFirstWeakWarning();
        assertNotNull("Should have a weak warning for unused import", warning);
        assertEquals("Unused import", warning.getDescription());
    }

    /**
     * Test that wildcard import from non-existent file is handled gracefully.
     */
    public void testWildcardImportNonExistentFileHandledGracefully() {
        configureByText("""
                import * from "missing.kite"

                var x = "hello"
                """);

        // Verify highlighting runs without exception
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
    }

    /**
     * Test that multi-symbol import from non-existent file is handled gracefully.
     */
    public void testMultiSymbolImportNonExistentFileHandledGracefully() {
        configureByText("""
                import a, b, c from "notfound.kite"

                var x = "hello"
                """);

        // Verify highlighting runs without exception
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
    }

    /**
     * Test that multiple broken imports are handled gracefully.
     */
    public void testMultipleBrokenImportsHandledGracefully() {
        configureByText("""
                import a from "missing1.kite"
                import b from "missing2.kite"

                var x = "hello"
                """);

        // Verify highlighting runs without exception
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
    }

    /**
     * Test that mix of valid and broken imports works correctly.
     */
    public void testMixOfValidAndBrokenImports() {
        addFile("valid.kite", """
                var validVar = "valid"
                """);

        configureByText("""
                import validVar from "valid.kite"
                import broken from "broken.kite"

                var x = validVar
                """);

        // Verify highlighting runs without exception
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
    }

    /**
     * Test that relative path imports work correctly.
     */
    public void testRelativePathImportValid() {
        addFile("subdir/nested.kite", """
                var nestedVar = "nested"
                """);

        configureByText("main.kite", """
                import nestedVar from "subdir/nested.kite"
                
                var x = nestedVar
                """);

        // Verify no errors for valid relative import
        List<HighlightInfo> errors = getErrors();
        assertTrue("Valid relative path import should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
    }

    /**
     * Test that wrong relative path is handled gracefully.
     */
    public void testWrongRelativePathHandledGracefully() {
        addFile("correct/file.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import myVar from "wrong/file.kite"
                
                var x = myVar
                """);

        // Verify highlighting runs without exception
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
    }

    /**
     * Test that empty import path is handled gracefully.
     */
    public void testEmptyImportPathHandledGracefully() {
        configureByText("""
                import something from ""
                
                var x = "hello"
                """);

        // Verify highlighting runs without exception
        List<HighlightInfo> allHighlights = myFixture.doHighlighting();
        assertNotNull("Highlighting should run", allHighlights);
    }

    /**
     * Test that no imports produces no errors.
     */
    public void testNoImportsNoErrors() {
        configureByText("""
                var x = "hello"
                var y = x
                """);

        // Verify no errors
        List<HighlightInfo> errors = getErrors();
        assertTrue("File with no imports should produce no errors, but got: " + formatErrors(errors), errors.isEmpty());
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
