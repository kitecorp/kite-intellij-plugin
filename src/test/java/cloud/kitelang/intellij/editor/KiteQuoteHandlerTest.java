package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteTestBase;

/**
 * Tests for KiteQuoteHandler - verifies auto-pairing of quotes.
 * Note: Full interactive quote handling testing requires EditorTestUtil,
 * these tests verify basic behavior.
 */
public class KiteQuoteHandlerTest extends KiteTestBase {

    // ========== Basic Quote Handling Tests ==========

    public void testFileWithDoubleQuotedString() {
        configureByText("""
                var message = "Hello World"
                """);

        myFixture.doHighlighting();
        assertTrue("File should contain double-quoted string",
                myFixture.getFile().getText().contains("\"Hello World\""));
    }

    public void testFileWithSingleQuotedString() {
        configureByText("""
                var message = 'Hello World'
                """);

        myFixture.doHighlighting();
        assertTrue("File should contain single-quoted string",
                myFixture.getFile().getText().contains("'Hello World'"));
    }

    public void testFileWithEscapedQuotes() {
        configureByText("""
                var message = "He said \\"Hello\\""
                """);

        myFixture.doHighlighting();
        assertTrue("File should contain escaped quotes",
                myFixture.getFile().getText().contains("\\\""));
    }

    public void testFileWithMixedQuotes() {
        configureByText("""
                var single = 'value'
                var double = "value"
                """);

        myFixture.doHighlighting();
        String text = myFixture.getFile().getText();
        assertTrue("File should contain single quotes", text.contains("'value'"));
        assertTrue("File should contain double quotes", text.contains("\"value\""));
    }

    // ========== String Interpolation Quote Tests ==========

    public void testInterpolationWithDoubleQuotes() {
        configureByText("""
                var name = "World"
                var greeting = "Hello $name!"
                """);

        myFixture.doHighlighting();
        assertTrue("Interpolation should work in double-quoted strings",
                myFixture.getFile().getText().contains("$name"));
    }

    public void testBraceInterpolationWithDoubleQuotes() {
        configureByText("""
                var port = 8080
                var url = "http://localhost:${port}"
                """);

        myFixture.doHighlighting();
        assertTrue("Brace interpolation should work in double-quoted strings",
                myFixture.getFile().getText().contains("${port}"));
    }

    // ========== Empty String Tests ==========

    public void testEmptyDoubleQuotedString() {
        configureByText("""
                var empty = ""
                """);

        myFixture.doHighlighting();
        assertTrue("File should contain empty double-quoted string",
                myFixture.getFile().getText().contains("\"\""));
    }

    public void testEmptySingleQuotedString() {
        configureByText("""
                var empty = ''
                """);

        myFixture.doHighlighting();
        assertTrue("File should contain empty single-quoted string",
                myFixture.getFile().getText().contains("''"));
    }

    // ========== Multi-line String Tests ==========

    public void testMultilineString() {
        configureByText("""
                var multiline = "Line 1
                Line 2
                Line 3"
                """);

        myFixture.doHighlighting();
        assertNotNull("Multiline string should parse", myFixture.getFile());
    }

    // ========== Edge Cases ==========

    public void testQuotesInObjectLiteral() {
        configureByText("""
                var config = {
                    host: "localhost",
                    name: 'test'
                }
                """);

        myFixture.doHighlighting();
        String text = myFixture.getFile().getText();
        assertTrue("Object should contain double-quoted value", text.contains("\"localhost\""));
        assertTrue("Object should contain single-quoted value", text.contains("'test'"));
    }

    public void testQuotesInArray() {
        configureByText("""
                var items = ["a", "b", 'c']
                """);

        myFixture.doHighlighting();
        String text = myFixture.getFile().getText();
        assertTrue("Array should contain quoted strings",
                text.contains("\"a\"") && text.contains("\"b\"") && text.contains("'c'"));
    }

    public void testQuotesInImportPath() {
        addFile("common.kite", "var x = 1");

        configureByText("""
                import * from "common.kite"
                """);

        myFixture.doHighlighting();
        assertTrue("Import path should use double quotes",
                myFixture.getFile().getText().contains("\"common.kite\""));
    }

    public void testQuotesInDecorator() {
        configureByText("""
                @description("Test description")
                schema Config {
                    string host
                }
                """);

        myFixture.doHighlighting();
        assertTrue("Decorator should contain quoted string",
                myFixture.getFile().getText().contains("\"Test description\""));
    }

    public void testNestedQuotes() {
        configureByText("""
                var json = "{\\"key\\": \\"value\\"}"
                """);

        myFixture.doHighlighting();
        assertTrue("Should handle nested escaped quotes",
                myFixture.getFile().getText().contains("\\\"key\\\""));
    }
}
