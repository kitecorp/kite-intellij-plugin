package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for duplicate import detection.
 * Verifies that duplicate import warnings are correctly detected.
 */
public class KiteDuplicateImportAnnotatorTest extends KiteTestBase {

    /**
     * Test that same symbol imported twice from different files is flagged.
     */
    public void testDuplicateSymbolFromDifferentFiles() {
        addFile("fileA.kite", """
                var myVar = "from A"
                """);
        addFile("fileB.kite", """
                var myVar = "from B"
                """);

        configureByText("""
                import myVar from "fileA.kite"
                import myVar from "fileB.kite"
                
                var x = myVar
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("Expected one duplicate import warning", 1, warnings.size());
        assertTrue("Warning should mention duplicate import",
                warnings.get(0).getDescription().contains("already imported"));
    }

    /**
     * Test that same symbol imported twice from same file is flagged.
     */
    public void testDuplicateSymbolFromSameFile() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import myVar from "common.kite"
                import myVar from "common.kite"
                
                var x = myVar
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("Expected one duplicate import warning", 1, warnings.size());
    }

    /**
     * Test that same symbol twice in one import statement is flagged.
     */
    public void testDuplicateSymbolInSameStatement() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import myVar, myVar from "common.kite"
                
                var x = myVar
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("Expected one duplicate import warning", 1, warnings.size());
    }

    /**
     * Test that different symbols from same file are NOT flagged.
     */
    public void testDifferentSymbolsFromSameFile() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import alpha from "common.kite"
                import beta from "common.kite"
                
                var x = alpha + beta
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("Different symbols should not be flagged", 0, warnings.size());
    }

    /**
     * Test that multi-symbol import with no duplicates is NOT flagged.
     */
    public void testMultiSymbolNoDuplicates() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                var gamma = "c"
                """);

        configureByText("""
                import alpha, beta, gamma from "common.kite"
                
                var x = alpha + beta + gamma
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("No duplicates should produce no warnings", 0, warnings.size());
    }

    /**
     * Test that no imports produces no warnings.
     */
    public void testNoImports() {
        configureByText("""
                var x = "hello"
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("No imports should produce no warnings", 0, warnings.size());
    }

    /**
     * Test multiple duplicates are each flagged.
     */
    public void testMultipleDuplicates() {
        addFile("fileA.kite", """
                var alpha = "a"
                var beta = "b"
                """);
        addFile("fileB.kite", """
                var alpha = "a2"
                var beta = "b2"
                """);

        configureByText("""
                import alpha, beta from "fileA.kite"
                import alpha, beta from "fileB.kite"
                
                var x = alpha + beta
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("Expected two duplicate import warnings", 2, warnings.size());
    }

    /**
     * Test that wildcard import followed by named import of same symbol is flagged.
     */
    public void testWildcardThenNamedImport() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import * from "common.kite"
                import myVar from "common.kite"
                
                var x = myVar
                """);

        List<HighlightInfo> warnings = getDuplicateImportWarnings();
        assertEquals("Named import after wildcard from same file should be flagged", 1, warnings.size());
    }

    /**
     * Helper method to get only duplicate import warnings from highlighting.
     */
    private List<HighlightInfo> getDuplicateImportWarnings() {
        myFixture.doHighlighting();
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING ||
                             h.getSeverity() == HighlightSeverity.WEAK_WARNING)
                .filter(h -> h.getDescription() != null &&
                             (h.getDescription().contains("already imported") ||
                              h.getDescription().contains("Duplicate import")))
                .collect(Collectors.toList());
    }
}
