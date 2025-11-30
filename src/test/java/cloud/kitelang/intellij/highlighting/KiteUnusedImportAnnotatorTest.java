package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link KiteUnusedImportAnnotator}.
 * Verifies that unused import warnings are correctly detected.
 */
public class KiteUnusedImportAnnotatorTest extends KiteTestBase {

    /**
     * Test that a completely unused named import is flagged.
     */
    public void testUnusedNamedImport() {
        // Add the imported file first
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                var appName = "myapp"
                """);

        // Configure with an unused import
        configureByText("""
                import defaultRegion from "common.kite"
                
                var x = "hello"
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("Expected one unused import warning", 1, warnings.size());
        assertTrue("Warning should mention unused import",
                warnings.get(0).getDescription().contains("Unused import"));
    }

    /**
     * Test that a used import is NOT flagged.
     */
    public void testUsedNamedImport() {
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                """);

        configureByText("""
                import defaultRegion from "common.kite"
                
                var x = defaultRegion
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("Used import should not be flagged", 0, warnings.size());
    }

    /**
     * Test that partially unused multi-symbol imports are detected.
     * When importing "a, b" but only using "a", "b" should be flagged.
     */
    public void testPartiallyUsedMultiSymbolImport() {
        addFile("common.kite", """
                var defaultRegion = "us-east-1"
                var appName = "myapp"
                """);

        configureByText("""
                import defaultRegion, appName from "common.kite"
                
                var x = defaultRegion
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("Expected one warning for unused symbol 'appName'", 1, warnings.size());
        assertTrue("Warning should mention 'appName'",
                warnings.get(0).getDescription().contains("appName"));
    }

    /**
     * Test that imports used in string interpolation ($var) are recognized.
     */
    public void testImportUsedInStringInterpolationSimple() {
        addFile("common.kite", """
                var region = "us-east-1"
                """);

        configureByText("""
                import region from "common.kite"
                
                var msg = "Region is $region"
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("Import used in $var interpolation should not be flagged", 0, warnings.size());
    }

    /**
     * Test that imports used in string interpolation (${var}) are recognized.
     */
    public void testImportUsedInStringInterpolationBraced() {
        addFile("common.kite", """
                var region = "us-east-1"
                """);

        configureByText("""
                import region from "common.kite"
                
                var msg = "Region is ${region}"
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("Import used in ${var} interpolation should not be flagged", 0, warnings.size());
    }

    /**
     * Test that imported functions used in calls are recognized.
     */
    public void testImportedFunctionUsed() {
        addFile("common.kite", """
                fun formatName(string prefix, string name) string {
                    return prefix + "-" + name
                }
                """);

        configureByText("""
                import formatName from "common.kite"
                
                var name = formatName("app", "server")
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("Used function import should not be flagged", 0, warnings.size());
    }

    /**
     * Test that all symbols unused results in whole import being flagged.
     */
    public void testAllSymbolsUnused() {
        addFile("common.kite", """
                var a = "1"
                var b = "2"
                var c = "3"
                """);

        configureByText("""
                import a, b, c from "common.kite"
                
                var x = "unused"
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        // Should get one warning for the whole import, not three individual warnings
        assertTrue("Expected warning(s) for unused imports", warnings.size() >= 1);
    }

    /**
     * Test that imported schemas used as types are recognized.
     */
    public void testImportedSchemaUsedAsType() {
        addFile("common.kite", """
                schema Config {
                    string name
                }
                """);

        configureByText("""
                import Config from "common.kite"
                
                resource Config myResource {
                    name = "test"
                }
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("Schema used as resource type should not be flagged", 0, warnings.size());
    }

    /**
     * Test no warnings when there are no imports.
     */
    public void testNoImports() {
        configureByText("""
                var x = "hello"
                var y = x
                """);

        List<HighlightInfo> warnings = getUnusedImportWarnings();
        assertEquals("No imports should produce no warnings", 0, warnings.size());
    }

    /**
     * Helper method to get only unused import warnings from highlighting.
     */
    private List<HighlightInfo> getUnusedImportWarnings() {
        myFixture.doHighlighting();
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WEAK_WARNING)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("import"))
                .collect(Collectors.toList());
    }
}
