package cloud.kitelang.intellij.quickfix;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link RemoveUnusedImportQuickFix}.
 * Verifies that the quick fix correctly removes unused imports.
 */
public class RemoveUnusedImportQuickFixTest extends KiteTestBase {

    /**
     * Test that applying the quick fix removes an entire unused import line.
     */
    public void testRemoveEntireUnusedImport() {
        addFile("common.kite", """
                var unusedVar = "unused"
                """);

        configureByText("""
                import unusedVar from "common.kite"
                
                var x = "hello"
                """);

        // Find and apply the quick fix
        List<IntentionAction> fixes = getRemoveImportFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Apply the fix
        myFixture.launchAction(fixes.get(0));

        // Verify the import was removed, only var x remains
        String result = myFixture.getFile().getText().trim();
        assertEquals("var x = \"hello\"", result);
    }

    /**
     * Test that applying the quick fix removes only the unused symbol from a multi-symbol import.
     */
    public void testRemoveSingleSymbolFromMultiImport() {
        addFile("common.kite", """
                var usedVar = "used"
                var unusedVar = "unused"
                """);

        configureByText("""
                import usedVar, unusedVar from "common.kite"
                
                var x = usedVar
                """);

        // Find the quick fix for the unused symbol
        List<IntentionAction> fixes = getRemoveImportFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Find the fix for 'unusedVar' specifically
        IntentionAction fix = fixes.stream()
                .filter(f -> f.getText().contains("unusedVar"))
                .findFirst()
                .orElse(fixes.get(0));

        // Apply the fix
        myFixture.launchAction(fix);

        // Verify only the unused symbol was removed
        String result = myFixture.getFile().getFirstChild().getText().trim();
        assertEquals("import usedVar from \"common.kite\"", result);
    }

    /**
     * Test that the quick fix is available for unused imports.
     */
    public void testQuickFixAvailability() {
        addFile("common.kite", """
                var unusedVar = "unused"
                """);

        configureByText("""
                import unusedVar from "common.kite"
                
                var x = "hello"
                """);

        List<IntentionAction> fixes = getRemoveImportFixes();
        assertFalse("Quick fix should be available for unused import", fixes.isEmpty());
        assertTrue("Quick fix should be named correctly",
                fixes.stream().anyMatch(f -> f.getText().contains("Remove unused import")));
    }

    /**
     * Test that no quick fix is shown when imports are used.
     */
    public void testNoQuickFixForUsedImport() {
        addFile("common.kite", """
                var usedVar = "used"
                """);

        configureByText("""
                import usedVar from "common.kite"
                
                var x = usedVar
                """);

        List<IntentionAction> fixes = getRemoveImportFixes();
        assertTrue("No quick fix should be available for used imports", fixes.isEmpty());
    }

    /**
     * Test that removing multiple unused symbols from the same import works.
     */
    public void testRemoveMultipleUnusedSymbols() {
        addFile("common.kite", """
                var a = "1"
                var b = "2"
                var c = "3"
                """);

        configureByText("""
                import a, b, c from "common.kite"
                
                var x = "none used"
                """);

        // Get all warnings (should be for all unused imports)
        List<IntentionAction> fixes = getRemoveImportFixes();
        assertFalse("Should have quick fixes", fixes.isEmpty());

        // Apply the fix (should remove entire line since all are unused)
        myFixture.launchAction(fixes.get(0));

        // Verify the entire import was removed, only var x remains
        String result = myFixture.getFile().getText().trim();
        assertEquals("var x = \"none used\"", result);
    }

    /**
     * Test quick fix preserves other imports in the file.
     */
    public void testPreservesOtherImports() {
        addFile("common.kite", """
                var usedVar = "used"
                """);
        addFile("other.kite", """
                var unusedVar = "unused"
                """);

        configureByText("""
                import usedVar from "common.kite"
                import unusedVar from "other.kite"
                
                var x = usedVar
                """);

        // Find the fix for the unused import
        List<IntentionAction> fixes = getRemoveImportFixes();
        assertFalse("Should have a quick fix available", fixes.isEmpty());

        // Apply the fix
        myFixture.launchAction(fixes.get(0));

        // Verify only the unused import was removed, used import and var remain
        String result = myFixture.getFile().getFirstChild().getText().trim();
        assertEquals("import usedVar from \"common.kite\"", result);
    }

    /**
     * Test quick fix text for entire import removal.
     */
    public void testQuickFixTextForEntireImport() {
        addFile("common.kite", """
                var unused = "unused"
                """);

        configureByText("""
                import unused from "common.kite"
                
                var x = "hello"
                """);

        List<IntentionAction> fixes = getRemoveImportFixes();
        assertFalse("Should have a fix", fixes.isEmpty());
        assertEquals("Quick fix text should be correct", "Remove unused import", fixes.get(0).getText());
    }

    /**
     * Test quick fix text for single symbol removal.
     */
    public void testQuickFixTextForSingleSymbol() {
        addFile("common.kite", """
                var used = "used"
                var unused = "unused"
                """);

        configureByText("""
                import used, unused from "common.kite"
                
                var x = used
                """);

        List<IntentionAction> fixes = getRemoveImportFixes();
        assertFalse("Should have a fix", fixes.isEmpty());

        // Check for the specific symbol removal text
        boolean hasSymbolFix = fixes.stream()
                .anyMatch(f -> f.getText().contains("'unused'"));
        assertTrue("Should have symbol-specific fix text", hasSymbolFix);
    }

    /**
     * Helper to get intention actions for removing unused imports.
     */
    private List<IntentionAction> getRemoveImportFixes() {
        myFixture.doHighlighting();
        List<HighlightInfo> warnings = myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WEAK_WARNING)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("import"))
                .collect(Collectors.toList());

        return warnings.stream()
                .flatMap(w -> w.quickFixActionRanges == null ?
                        java.util.stream.Stream.empty() :
                        w.quickFixActionRanges.stream())
                .map(pair -> pair.getFirst().getAction())
                .filter(action -> action.getText().contains("Remove unused import"))
                .collect(Collectors.toList());
    }
}
