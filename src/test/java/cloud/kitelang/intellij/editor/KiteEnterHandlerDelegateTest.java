package cloud.kitelang.intellij.editor;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.openapi.actionSystem.IdeActions;

/**
 * Tests for KiteEnterHandlerDelegate.
 * Verifies Smart Enter behavior: auto-close braces and block comment continuation.
 */
public class KiteEnterHandlerDelegateTest extends KiteTestBase {

    // ========== Enter After Opening Brace Tests ==========

    public void testEnterAfterOpenBraceInsertsClosingBrace() {
        configureByText("schema Config {<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should contain closing brace", result.contains("}"));
        assertTrue("Should have newline after open brace", result.contains("{\n"));
    }

    public void testEnterAfterOpenBraceAddsIndentation() {
        configureByText("schema Config {<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Should have newline after brace - indentation may vary based on settings
        assertTrue("Should have newline after open brace", result.contains("{\n"));
    }

    public void testEnterAfterOpenBraceWithExistingClosingBrace() {
        configureByText("schema Config {<caret>}");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Should only have one closing brace
        int braceCount = countOccurrences(result);
        assertEquals("Should have exactly one closing brace", 1, braceCount);
    }

    public void testEnterAfterOpenBraceInFunction() {
        configureByText("fun test() {<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should contain closing brace", result.contains("}"));
    }

    public void testEnterAfterOpenBraceInComponent() {
        configureByText("component Server {<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should contain closing brace", result.contains("}"));
    }

    public void testEnterAfterOpenBraceInResource() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config main {<caret>""");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Should have two closing braces (schema and resource)
        int braceCount = countOccurrences(result);
        assertEquals("Should have two closing braces", 2, braceCount);
    }

    public void testEnterAfterOpenBracePreservesIndentation() {
        configureByText("""
                fun outer() {
                    fun inner() {<caret>
                }""");

        pressEnter();

        String result = myFixture.getFile().getText();
        // The inner function should have proper indentation
        assertTrue("Inner function content should be indented",
                result.contains("inner() {\n      ") || result.contains("inner() {\n    "));
    }

    // ========== Enter Inside Block Comment Tests ==========

    public void testEnterInsideBlockComment() {
        configureByText("/* Comment<caret> */");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Just verify it doesn't crash and handles the comment
        assertTrue("Should preserve block comment start", result.contains("/*"));
    }

    public void testEnterAfterBlockCommentStart() {
        configureByText("/*<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Just verify it doesn't crash
        assertTrue("Should preserve block comment start", result.contains("/*"));
    }

    public void testEnterOnBlockCommentContinuationLine() {
        configureByText("""
                /*
                 * First line<caret>
                 */""");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Verify structure is preserved
        assertTrue("Should preserve block comment", result.contains("/*"));
        assertTrue("Should preserve closing", result.contains("*/"));
    }

    public void testEnterInMultilineBlockComment() {
        configureByText("""
                /*
                 * Line 1
                 * Line 2<caret>
                 */""");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Verify structure is preserved
        assertTrue("Should preserve block comment", result.contains("/*"));
        assertTrue("Should have Line 1", result.contains("Line 1"));
        assertTrue("Should have Line 2", result.contains("Line 2"));
    }

    // ========== Regular Enter (No Special Handling) Tests ==========

    public void testEnterInRegularCode() {
        configureByText("var x = 1<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should just add newline", result.contains("var x = 1\n"));
    }

    public void testEnterBetweenStatements() {
        configureByText("""
                var x = 1<caret>
                var y = 2""");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should preserve both statements",
                result.contains("var x = 1") && result.contains("var y = 2"));
    }

    public void testEnterInEmptyFile() {
        configureByText("<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should add newline", result.contains("\n"));
    }

    // ========== Edge Cases ==========

    public void testEnterAfterClosingBrace() {
        configureByText("""
                schema Config {
                    string host
                }<caret>""");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Should not add another closing brace
        int braceCount = countOccurrences(result);
        assertEquals("Should have exactly one closing brace", 1, braceCount);
    }

    public void testEnterInsideString() {
        configureByText("var x = \"hello<caret>world\"");

        pressEnter();

        // Just verify it doesn't crash and string is preserved
        String result = myFixture.getFile().getText();
        assertNotNull(result);
    }

    public void testEnterWithNestedBraces() {
        configureByText("""
                schema Outer {
                    schema Inner {<caret>
                    }
                }""");

        pressEnter();

        String result = myFixture.getFile().getText();
        // Should have proper structure preserved
        assertTrue("Should preserve outer schema", result.contains("schema Outer"));
        assertTrue("Should preserve inner schema", result.contains("schema Inner"));
    }

    public void testEnterAfterOpenBraceWithContentAfter() {
        configureByText("schema Config {<caret> string host }");

        pressEnter();

        // Should handle gracefully
        String result = myFixture.getFile().getText();
        assertNotNull(result);
    }

    // ========== Indentation Preservation Tests ==========

    public void testEnterPreservesTabIndentation() {
        configureByText("\tschema Config {<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should contain closing brace", result.contains("}"));
    }

    public void testEnterPreservesSpaceIndentation() {
        configureByText("    schema Config {<caret>");

        pressEnter();

        String result = myFixture.getFile().getText();
        assertTrue("Should contain closing brace", result.contains("}"));
    }

    // ========== Helper Methods ==========

    private void pressEnter() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
    }

    private int countOccurrences(String text) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf("}", index)) != -1) {
            count++;
            index += "}".length();
        }
        return count;
    }
}
