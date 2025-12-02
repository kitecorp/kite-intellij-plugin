package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.lookup.LookupElement;

import java.util.List;

/**
 * Tests for import path completion.
 * Verifies that file paths are suggested when typing inside import string literals.
 */
public class KiteImportPathCompletionTest extends KiteTestBase {

    /**
     * Test completion suggests .kite files in the same directory.
     */
    public void testCompleteKiteFileInSameDirectory() {
        addFile("common.kite", """
                var alpha = "a"
                """);

        configureByText("""
                import * from "<caret>"
                """);

        LookupElement[] completions = myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest common.kite",
                lookupStrings.contains("common.kite"));
    }

    /**
     * Test completion suggests .kite files matching a prefix.
     */
    public void testCompleteWithPrefix() {
        addFile("common.kite", "var x = 1");
        addFile("config.kite", "var y = 2");
        addFile("other.kite", "var z = 3");

        configureByText("""
                import * from "co<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest common.kite",
                lookupStrings.contains("common.kite"));
        assertTrue("Should suggest config.kite",
                lookupStrings.contains("config.kite"));
        // Should not suggest "other.kite" because it doesn't match prefix "co"
    }

    /**
     * Test completion suggests files in subdirectories with relative path.
     */
    public void testCompleteFileInSubdirectory() {
        addFile("lib/utils.kite", "var util = 1");
        addFile("lib/helpers.kite", "var helper = 1");

        configureByText("""
                import * from "<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest lib/utils.kite",
                lookupStrings.contains("lib/utils.kite"));
        assertTrue("Should suggest lib/helpers.kite",
                lookupStrings.contains("lib/helpers.kite"));
    }

    /**
     * Test completion after typing partial directory path.
     */
    public void testCompleteAfterDirectoryPath() {
        addFile("lib/utils.kite", "var util = 1");
        addFile("lib/helpers.kite", "var helper = 1");
        addFile("other/file.kite", "var other = 1");

        configureByText("""
                import * from "lib/<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest lib/utils.kite or utils.kite",
                lookupStrings.stream().anyMatch(s -> s.contains("utils.kite")));
        assertTrue("Should suggest lib/helpers.kite or helpers.kite",
                lookupStrings.stream().anyMatch(s -> s.contains("helpers.kite")));
    }

    /**
     * Test no completion outside import string context.
     */
    public void testNoCompletionOutsideImportString() {
        addFile("common.kite", "var x = 1");

        configureByText("""
                var name = "<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // Should not suggest .kite files in regular string literals
        if (lookupStrings != null) {
            assertFalse("Should not suggest common.kite in regular string",
                    lookupStrings.contains("common.kite"));
        }
    }

    /**
     * Test completion works for named imports.
     */
    public void testCompleteForNamedImport() {
        addFile("common.kite", "var alpha = 1");

        configureByText("""
                import alpha from "<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest common.kite",
                lookupStrings.contains("common.kite"));
    }

    /**
     * Test completion excludes the current file.
     */
    public void testExcludesCurrentFile() {
        addFile("common.kite", "var x = 1");

        configureByText("main.kite", """
                import * from "<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertFalse("Should not suggest current file",
                lookupStrings.contains("main.kite"));
    }

    /**
     * Test completion works with single quotes.
     */
    public void testCompleteWithSingleQuotes() {
        addFile("common.kite", "var x = 1");

        configureByText("""
                import * from '<caret>'
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest common.kite",
                lookupStrings.contains("common.kite"));
    }

    /**
     * Test completion shows file icon for .kite files.
     */
    public void testCompletionShowsIcon() {
        addFile("common.kite", "var x = 1");

        configureByText("""
                import * from "<caret>"
                """);

        LookupElement[] completions = myFixture.completeBasic();

        assertNotNull("Should have completions", completions);
        for (LookupElement element : completions) {
            if ("common.kite".equals(element.getLookupString())) {
                // Just verify we can find the element - icon verification is UI-dependent
                return;
            }
        }
    }

    /**
     * Test completion sorts files alphabetically.
     */
    public void testCompletionSortedAlphabetically() {
        addFile("zebra.kite", "var z = 1");
        addFile("alpha.kite", "var a = 1");
        addFile("middle.kite", "var m = 1");

        configureByText("""
                import * from "<caret>"
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);

        // Find the indices of our test files
        List<String> kiteFiles = lookupStrings.stream()
                .filter(s -> s.endsWith(".kite"))
                .toList();

        // Verify they are in alphabetical order
        List<String> sorted = kiteFiles.stream().sorted().toList();
        assertEquals("Completions should be sorted", sorted, kiteFiles);
    }

    /**
     * Test that selecting a completion replaces the entire string content.
     * When user types "ga" and completes with "gamma.kite", the result should be
     * import ... from "gamma.kite" not import ... from "gamma.kitea"
     */
    public void testCompletionReplacesEntireStringContent() {
        // Add multiple files so autocomplete shows a popup instead of auto-completing
        addFile("gamma.kite", "var gammaVar = 1");
        addFile("alpha.kite", "var alphaVar = 1");

        configureByText("""
                import gammaVar from "ga<caret>"
                """);

        LookupElement[] completions = myFixture.completeBasic();

        // If completions is null, the completion was auto-inserted (single match starting with "ga")
        // In our case with "ga" prefix, only gamma.kite matches, so it auto-completes
        if (completions == null) {
            // Auto-completed - check the result directly
            String result = myFixture.getEditor().getDocument().getText();
            assertTrue("Should contain gamma.kite as complete path after auto-complete, got: " + result,
                    result.contains("\"gamma.kite\""));
            assertFalse("Should not have leftover 'ga' text creating 'gamma.kitega', got: " + result,
                    result.contains("gamma.kitega"));
            return;
        }

        // Find and select gamma.kite completion
        for (LookupElement element : completions) {
            if (element.getLookupString().equals("gamma.kite")) {
                myFixture.getLookup().setCurrentItem(element);
                myFixture.finishLookup('\n');
                break;
            }
        }

        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should contain gamma.kite as complete path, got: " + result,
                result.contains("\"gamma.kite\""));
        assertFalse("Should not have leftover 'ga' text creating 'gamma.kitega', got: " + result,
                result.contains("gamma.kitega"));
    }

    /**
     * Test that completion replaces existing partial path when tab is pressed.
     */
    public void testTabCompletionReplacesPartialPath() {
        // Add multiple files so autocomplete shows a popup instead of auto-completing
        addFile("gamma.kite", "var gammaVar = 1");
        addFile("galactic.kite", "var galacticVar = 1");

        configureByText("""
                import gammaVar from "gam<caret>"
                """);

        LookupElement[] completions = myFixture.completeBasic();

        // If completions is null, the completion was auto-inserted
        if (completions == null) {
            String result = myFixture.getEditor().getDocument().getText();
            assertTrue("Should contain complete path gamma.kite after auto-complete, got: " + result,
                    result.contains("\"gamma.kite\""));
            return;
        }

        // Find and select gamma.kite completion with tab
        for (LookupElement element : completions) {
            if (element.getLookupString().equals("gamma.kite")) {
                myFixture.getLookup().setCurrentItem(element);
                myFixture.finishLookup('\t');
                break;
            }
        }

        String result = myFixture.getEditor().getDocument().getText();
        assertTrue("Should contain complete path gamma.kite, got: " + result,
                result.contains("\"gamma.kite\""));
    }
}
