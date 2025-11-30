package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.List;

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
     * Test that non-existent file import produces an error.
     */
    public void testNonExistentFileImportProducesError() {
        configureByText("""
                import something from "nonexistent.kite"

                var x = "hello"
                """);

        // Verify error is produced for non-existent import path
        HighlightInfo error = getFirstError();
        assertNotNull("Should have an error for non-existent import path", error);
        assertTrue("Error should be about cannot resolve import path: " + error.getDescription(),
                error.getDescription().contains("Cannot resolve import path"));
    }

    /**
     * Test that wildcard import from non-existent file produces an error.
     */
    public void testWildcardImportNonExistentFileProducesError() {
        configureByText("""
                import * from "missing.kite"

                var x = "hello"
                """);

        // Non-existent file should produce "Cannot resolve import path" error
        HighlightInfo error = getFirstError();
        assertNotNull("Should have an error for non-existent import path", error);
        assertTrue("Error should be about cannot resolve import path: " + error.getDescription(),
                error.getDescription().contains("Cannot resolve import path"));
    }

    /**
     * Test that multi-symbol import from non-existent file produces an error.
     */
    public void testMultiSymbolImportNonExistentFileProducesError() {
        configureByText("""
                import a, b, c from "notfound.kite"

                var x = "hello"
                """);

        // Non-existent file should produce "Cannot resolve import path" error
        HighlightInfo error = getFirstError();
        assertNotNull("Should have an error for non-existent import path", error);
        assertTrue("Error should be about cannot resolve import path: " + error.getDescription(),
                error.getDescription().contains("Cannot resolve import path"));
    }

    /**
     * Test that multiple broken imports produce errors.
     */
    public void testMultipleBrokenImportsProduceErrors() {
        configureByText("""
                import a from "missing1.kite"
                import b from "missing2.kite"

                var x = "hello"
                """);

        // Each missing file should produce "Cannot resolve import path" error
        List<HighlightInfo> errors = getErrors();
        assertEquals("Should have 2 errors for non-existent import paths", 2, errors.size());
        for (HighlightInfo error : errors) {
            assertTrue("Error should be about cannot resolve import path: " + error.getDescription(),
                    error.getDescription().contains("Cannot resolve import path"));
        }
    }

    /**
     * Test that mix of valid and broken imports produces error for broken import.
     */
    public void testMixOfValidAndBrokenImportsProducesErrorForBroken() {
        addFile("valid.kite", """
                var validVar = "valid"
                """);

        configureByText("""
                import validVar from "valid.kite"
                import broken from "broken.kite"

                var x = validVar
                """);

        // Broken import should produce "Cannot resolve import path" error
        HighlightInfo error = getFirstError();
        assertNotNull("Should have an error for non-existent import path", error);
        assertTrue("Error should be about cannot resolve import path: " + error.getDescription(),
                error.getDescription().contains("Cannot resolve import path"));
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
     * Test that wrong relative path produces an error.
     */
    public void testWrongRelativePathProducesError() {
        addFile("correct/file.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import myVar from "wrong/file.kite"

                var x = myVar
                """);

        // Wrong path import should produce "Cannot resolve import path" error
        HighlightInfo error = getFirstError();
        assertNotNull("Should have an error for non-existent import path", error);
        assertTrue("Error should be about cannot resolve import path: " + error.getDescription(),
                error.getDescription().contains("Cannot resolve import path"));
    }

    /**
     * Test that empty import path produces an error.
     */
    public void testEmptyImportPathProducesError() {
        configureByText("""
                import something from ""

                var x = "hello"
                """);

        // Empty path import should produce an error
        HighlightInfo error = getFirstError();
        assertNotNull("Empty import path should produce an error", error);
        assertEquals("Empty import path", error.getDescription());
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

}
