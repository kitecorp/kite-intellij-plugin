package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link AddImportQuickFix} quick fix behavior.
 * Verifies that the quick fix correctly appears for undefined symbols
 * and properly adds import statements when applied.
 */
public class AddImportIntentionTest extends KiteTestBase {

    /**
     * Test that quick fix is available when symbol exists in another file.
     */
    public void testQuickFixAvailableForUndefinedSymbol() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                var x = myVar
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());
        assertTrue("Quick fix should be named correctly",
                fixes.stream().anyMatch(f -> f.getText().contains("Import 'myVar'")));
    }

    /**
     * Test that applying the quick fix adds the import statement.
     */
    public void testApplyQuickFixAddsImport() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                var x = myVar
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Apply the fix
        myFixture.launchAction(fixes.get(0));

        // Verify the import was added
        String result = myFixture.getFile().getText();
        assertTrue("Should contain import statement", result.contains("import myVar from \"common.kite\""));
        assertTrue("Should still contain the var statement", result.contains("var x = myVar"));
    }

    /**
     * Test that quick fix adds to existing import from the same file.
     */
    public void testQuickFixAddsToExistingImport() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import alpha from "common.kite"
                
                var x = alpha + beta
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertFalse("Should have a quick fix for beta", fixes.isEmpty());

        // Apply the fix for beta
        IntentionAction betaFix = fixes.stream()
                .filter(f -> f.getText().contains("beta"))
                .findFirst()
                .orElse(null);
        assertNotNull("Should have fix for beta", betaFix);

        myFixture.launchAction(betaFix);

        // Verify beta was added to existing import
        String result = myFixture.getFile().getText();
        assertTrue("Should have combined import",
                result.contains("import alpha, beta from \"common.kite\""));
    }

    /**
     * Test that no quick fix is shown when symbol doesn't exist anywhere.
     */
    public void testNoQuickFixForNonExistentSymbol() {
        configureByText("""
                var x = nonExistentSymbol
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertTrue("Should have no quick fixes for non-existent symbol", fixes.isEmpty());
    }

    /**
     * Test that no quick fix is shown when symbol is already imported.
     */
    public void testNoQuickFixForAlreadyImportedSymbol() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import myVar from "common.kite"
                
                var x = myVar
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertTrue("Should have no quick fixes when symbol is already imported", fixes.isEmpty());
    }

    /**
     * Test that no quick fix is shown when symbol is available via wildcard import.
     */
    public void testNoQuickFixForWildcardImportedSymbol() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = myVar
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertTrue("Should have no quick fixes when symbol is wildcard imported", fixes.isEmpty());
    }

    /**
     * Test quick fix for functions.
     */
    public void testQuickFixForFunction() {
        addFile("common.kite", """
                fun formatName(string name) string {
                    return name
                }
                """);

        configureByText("""
                var x = formatName("test")
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertFalse("Should have a quick fix for function", fixes.isEmpty());
        assertTrue("Quick fix should reference function",
                fixes.stream().anyMatch(f -> f.getText().contains("formatName")));

        // Apply and verify
        myFixture.launchAction(fixes.get(0));
        String result = myFixture.getFile().getText();
        assertTrue("Should contain import statement", result.contains("import formatName from \"common.kite\""));
    }

    // Note: Schema names used in type positions (e.g., "resource Config myConfig")
    // are intentionally excluded from type checking validation. The type checker
    // skips capitalized type names to avoid false positives. This means the
    // "Add Import" quick fix is not available for schemas in type positions.
    // This is a known limitation documented in CLAUDE.md.

    /**
     * Test quick fix text format.
     */
    public void testQuickFixTextFormat() {
        addFile("common.kite", """
                var testSymbol = "value"
                """);

        configureByText("""
                var x = testSymbol
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertFalse("Should have a quick fix", fixes.isEmpty());
        assertEquals("Import 'testSymbol' from \"common.kite\"", fixes.get(0).getText());
    }

    /**
     * Test quick fix preserves existing file content.
     */
    public void testQuickFixPreservesExistingContent() {
        addFile("common.kite", """
                var importedVar = "imported"
                """);

        configureByText("""
                var localVar = "local"
                var x = importedVar
                var y = localVar
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertFalse("Should have a quick fix", fixes.isEmpty());

        myFixture.launchAction(fixes.get(0));

        String result = myFixture.getFile().getText();
        assertTrue("Should contain import", result.contains("import importedVar"));
        assertTrue("Should preserve localVar", result.contains("var localVar = \"local\""));
        assertTrue("Should preserve x", result.contains("var x = importedVar"));
        assertTrue("Should preserve y", result.contains("var y = localVar"));
    }

    /**
     * Test that applying quick fix resolves the error.
     */
    public void testQuickFixResolvesError() {
        addFile("common.kite", """
                var myVar = "value"
                """);

        configureByText("""
                var x = myVar
                """);

        // Verify error exists before fix
        List<HighlightInfo> errorsBefore = myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .collect(Collectors.toList());
        assertFalse("Should have errors before fix", errorsBefore.isEmpty());

        // Apply fix
        List<IntentionAction> fixes = getAddImportFixes();
        myFixture.launchAction(fixes.get(0));

        // Verify no more errors
        List<HighlightInfo> errorsAfter = myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("myVar"))
                .collect(Collectors.toList());
        assertTrue("Should have no errors for myVar after fix", errorsAfter.isEmpty());
    }

    /**
     * Test multiple quick fixes for symbol in multiple files.
     */
    public void testMultipleQuickFixesForMultipleFiles() {
        addFile("fileA.kite", """
                var sharedName = "from A"
                """);
        addFile("fileB.kite", """
                var sharedName = "from B"
                """);

        configureByText("""
                var x = sharedName
                """);

        List<IntentionAction> fixes = getAddImportFixes();
        assertTrue("Should have multiple quick fixes", fixes.size() >= 2);

        // Verify both files are offered
        boolean hasFileA = fixes.stream().anyMatch(f -> f.getText().contains("fileA.kite"));
        boolean hasFileB = fixes.stream().anyMatch(f -> f.getText().contains("fileB.kite"));
        assertTrue("Should offer import from fileA", hasFileA);
        assertTrue("Should offer import from fileB", hasFileB);
    }

    /**
     * Helper to get intention actions for adding imports.
     */
    private List<IntentionAction> getAddImportFixes() {
        myFixture.doHighlighting();
        List<HighlightInfo> errors = myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Cannot resolve symbol"))
                .collect(Collectors.toList());

        return errors.stream()
                .flatMap(e -> e.quickFixActionRanges == null ?
                        java.util.stream.Stream.empty() :
                        e.quickFixActionRanges.stream())
                .map(pair -> pair.getFirst().getAction())
                .filter(action -> action.getText().startsWith("Import '"))
                .collect(Collectors.toList());
    }
}
