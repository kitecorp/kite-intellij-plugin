package cloud.kitelang.intellij.imports;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;

/**
 * Tests for {@link KiteImportOptimizer}.
 * Verifies that the import optimizer correctly removes all unused imports.
 */
public class KiteImportOptimizerTest extends KiteTestBase {

    /**
     * Test that a single unused import is removed.
     */
    public void testRemoveSingleUnusedImport() {
        addFile("common.kite", """
                var unusedVar = "unused"
                """);

        configureByText("""
                import unusedVar from "common.kite"
                
                var x = "hello"
                """);

        optimizeImports();

        String result = myFixture.getFile().getText().trim();
        assertFalse("Should not contain unused import", result.contains("import"));
        assertTrue("Should still contain var x", result.contains("var x = \"hello\""));
    }

    /**
     * Test that multiple unused imports are removed.
     */
    public void testRemoveMultipleUnusedImports() {
        addFile("fileA.kite", """
                var alpha = "a"
                """);
        addFile("fileB.kite", """
                var beta = "b"
                """);

        configureByText("""
                import alpha from "fileA.kite"
                import beta from "fileB.kite"
                
                var x = "hello"
                """);

        optimizeImports();

        String result = myFixture.getFile().getText().trim();
        assertFalse("Should not contain any imports", result.contains("import"));
        assertEquals("var x = \"hello\"", result);
    }

    /**
     * Test that used imports are preserved.
     */
    public void testPreserveUsedImports() {
        addFile("common.kite", """
                var usedVar = "used"
                """);

        configureByText("""
                import usedVar from "common.kite"
                
                var x = usedVar
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        assertTrue("Should preserve used import", result.contains("import usedVar from \"common.kite\""));
        assertTrue("Should preserve var x", result.contains("var x = usedVar"));
    }

    /**
     * Test that unused symbols are removed from multi-symbol imports.
     */
    public void testRemoveUnusedSymbolsFromMultiImport() {
        addFile("common.kite", """
                var usedVar = "used"
                var unusedVar = "unused"
                """);

        configureByText("""
                import usedVar, unusedVar from "common.kite"
                
                var x = usedVar
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        assertTrue("Should contain import for usedVar", result.contains("import usedVar from \"common.kite\""));
        assertFalse("Should not contain unusedVar", result.contains("unusedVar"));
    }

    /**
     * Test that all symbols removed means entire import line removed.
     */
    public void testRemoveEntireImportWhenAllSymbolsUnused() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import alpha, beta from "common.kite"
                
                var x = "hello"
                """);

        optimizeImports();

        String result = myFixture.getFile().getText().trim();
        assertFalse("Should not contain any imports", result.contains("import"));
        assertEquals("var x = \"hello\"", result);
    }

    /**
     * Test that wildcard imports are preserved (they're always potentially used).
     */
    public void testPreserveWildcardImports() {
        addFile("common.kite", """
                var someVar = "value"
                """);

        configureByText("""
                import * from "common.kite"
                
                var x = someVar
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        assertTrue("Should preserve wildcard import", result.contains("import * from \"common.kite\""));
    }

    /**
     * Test mixed used and unused imports from different files.
     */
    public void testMixedUsedAndUnusedImports() {
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

        optimizeImports();

        String result = myFixture.getFile().getText();
        assertTrue("Should preserve used import", result.contains("import usedVar from \"common.kite\""));
        assertFalse("Should not contain unused import", result.contains("other.kite"));
    }

    /**
     * Test that string interpolation usage is recognized.
     */
    public void testRecognizeStringInterpolationUsage() {
        addFile("common.kite", """
                var region = "us-east-1"
                """);

        configureByText("""
                import region from "common.kite"
                
                var endpoint = "https://$region.api.example.com"
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        assertTrue("Should preserve import used in interpolation", result.contains("import region"));
    }

    /**
     * Test optimizer does nothing when no imports exist.
     */
    public void testNoImportsNoChanges() {
        configureByText("""
                var x = "hello"
                var y = "world"
                """);

        String before = myFixture.getFile().getText();
        optimizeImports();
        String after = myFixture.getFile().getText();

        assertEquals("File should not change", before, after);
    }

    /**
     * Test optimizer preserves file structure.
     */
    public void testPreservesFileStructure() {
        addFile("common.kite", """
                var usedVar = "used"
                """);

        configureByText("""
                import usedVar from "common.kite"
                
                var localVar = "local"
                
                fun myFunction(string param) string {
                    return param + usedVar
                }
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        assertTrue("Should preserve import", result.contains("import usedVar"));
        assertTrue("Should preserve localVar", result.contains("var localVar"));
        assertTrue("Should preserve function", result.contains("fun myFunction"));
    }

    // ==================== Import Sorting Tests ====================

    /**
     * Test that imports are sorted alphabetically by path.
     */
    public void testSortImportsByPath() {
        addFile("alpha.kite", """
                var alphaVar = "a"
                """);
        addFile("beta.kite", """
                var betaVar = "b"
                """);
        addFile("gamma.kite", """
                var gammaVar = "c"
                """);

        configureByText("""
                import gammaVar from "gamma.kite"
                import alphaVar from "alpha.kite"
                import betaVar from "beta.kite"
                
                var x = alphaVar + betaVar + gammaVar
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        int alphaPos = result.indexOf("alpha.kite");
        int betaPos = result.indexOf("beta.kite");
        int gammaPos = result.indexOf("gamma.kite");

        assertTrue("alpha.kite should come before beta.kite", alphaPos < betaPos);
        assertTrue("beta.kite should come before gamma.kite", betaPos < gammaPos);
    }

    /**
     * Test that imports are sorted case-insensitively.
     */
    public void testSortImportsCaseInsensitive() {
        addFile("Apple.kite", """
                var appleVar = "a"
                """);
        addFile("banana.kite", """
                var bananaVar = "b"
                """);
        addFile("Cherry.kite", """
                var cherryVar = "c"
                """);

        configureByText("""
                import cherryVar from "Cherry.kite"
                import appleVar from "Apple.kite"
                import bananaVar from "banana.kite"
                
                var x = appleVar + bananaVar + cherryVar
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        int applePos = result.indexOf("Apple.kite");
        int bananaPos = result.indexOf("banana.kite");
        int cherryPos = result.indexOf("Cherry.kite");

        assertTrue("Apple.kite should come before banana.kite", applePos < bananaPos);
        assertTrue("banana.kite should come before Cherry.kite", bananaPos < cherryPos);
    }

    /**
     * Test that already sorted imports remain sorted.
     */
    public void testAlreadySortedImports() {
        addFile("alpha.kite", """
                var alphaVar = "a"
                """);
        addFile("beta.kite", """
                var betaVar = "b"
                """);

        configureByText("""
                import alphaVar from "alpha.kite"
                import betaVar from "beta.kite"
                
                var x = alphaVar + betaVar
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        int alphaPos = result.indexOf("alpha.kite");
        int betaPos = result.indexOf("beta.kite");

        assertTrue("alpha.kite should still come before beta.kite", alphaPos < betaPos);
    }

    /**
     * Test sorting with wildcard imports.
     */
    public void testSortWithWildcardImports() {
        addFile("alpha.kite", """
                var alphaVar = "a"
                """);
        addFile("beta.kite", """
                var betaVar = "b"
                """);

        configureByText("""
                import * from "beta.kite"
                import alphaVar from "alpha.kite"
                
                var x = alphaVar + betaVar
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        int alphaPos = result.indexOf("alpha.kite");
        int betaPos = result.indexOf("beta.kite");

        assertTrue("alpha.kite should come before beta.kite", alphaPos < betaPos);
    }

    /**
     * Test sorting also sorts symbols within multi-symbol imports.
     */
    public void testSortSymbolsWithinImport() {
        addFile("common.kite", """
                var zebra = "z"
                var alpha = "a"
                var mike = "m"
                """);

        configureByText("""
                import zebra, alpha, mike from "common.kite"
                
                var x = zebra + alpha + mike
                """);

        optimizeImports();

        String result = myFixture.getFile().getText();
        // The symbols should be sorted: alpha, mike, zebra
        assertTrue("Symbols should be sorted alphabetically",
                result.contains("import alpha, mike, zebra from \"common.kite\""));
    }

    /**
     * Helper method to run the import optimizer.
     */
    private void optimizeImports() {
        PsiFile file = myFixture.getFile();
        ImportOptimizer optimizer = new KiteImportOptimizer();

        if (optimizer.supports(file)) {
            Runnable runnable = optimizer.processFile(file);
            WriteCommandAction.runWriteCommandAction(file.getProject(), runnable);
        }
    }
}
