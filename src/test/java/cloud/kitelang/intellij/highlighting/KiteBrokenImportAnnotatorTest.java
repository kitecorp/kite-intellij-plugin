package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;

/**
 * Tests for broken import path detection in {@link KiteTypeCheckingAnnotator}.
 * Verifies that the file parses and the annotator runs without exceptions.
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

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that non-existent file import is handled gracefully.
     */
    public void testNonExistentFileImportHandledGracefully() {
        configureByText("""
                import something from "nonexistent.kite"
                
                var x = "hello"
                """);

        // Verify file parses and highlighting runs without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that wildcard import from non-existent file is handled gracefully.
     */
    public void testWildcardImportNonExistentFileHandledGracefully() {
        configureByText("""
                import * from "missing.kite"
                
                var x = "hello"
                """);

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that multi-symbol import from non-existent file is handled gracefully.
     */
    public void testMultiSymbolImportNonExistentFileHandledGracefully() {
        configureByText("""
                import a, b, c from "notfound.kite"
                
                var x = "hello"
                """);

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
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

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
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

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
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

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that import with wrong relative path is handled gracefully.
     */
    public void testWrongRelativePathHandledGracefully() {
        addFile("correct/file.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import myVar from "wrong/file.kite"
                
                var x = myVar
                """);

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that empty import path is handled gracefully.
     */
    public void testEmptyImportPath() {
        configureByText("""
                import something from ""
                
                var x = "hello"
                """);

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }

    /**
     * Test that no imports produces no errors.
     */
    public void testNoImportsNoErrors() {
        configureByText("""
                var x = "hello"
                var y = x
                """);

        // Verify file parses without exception
        assertNotNull(myFixture.getFile());
        myFixture.doHighlighting();
    }
}
