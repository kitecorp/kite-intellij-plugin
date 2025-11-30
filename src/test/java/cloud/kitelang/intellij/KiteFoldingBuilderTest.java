package cloud.kitelang.intellij;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

/**
 * Tests for {@link KiteFoldingBuilder}.
 * Verifies that import folding and block folding work correctly.
 */
public class KiteFoldingBuilderTest extends KiteTestBase {

    /**
     * Test that multiple consecutive imports are folded into a single region.
     */
    public void testFoldMultipleImports() {
        configureByText("""
                import alpha from "alpha.kite"
                import beta from "beta.kite"
                import gamma from "gamma.kite"
                
                var x = "hello"
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        // Should have exactly one folding region for imports
        boolean hasImportFolding = false;
        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            if (placeholderText != null && placeholderText.contains("imports")) {
                hasImportFolding = true;
                assertTrue("Should show correct count", placeholderText.contains("3"));
                break;
            }
        }
        assertTrue("Should have import folding region", hasImportFolding);
    }

    /**
     * Test that a single import is NOT folded (no value in folding 1 import).
     */
    public void testNoFoldingForSingleImport() {
        configureByText("""
                import alpha from "alpha.kite"
                
                var x = "hello"
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        // Should NOT have import folding for single import
        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            assertFalse("Single import should not be folded",
                    placeholderText != null && placeholderText.contains("imports"));
        }
    }

    /**
     * Test that two imports are foldable.
     */
    public void testFoldTwoImports() {
        configureByText("""
                import alpha from "alpha.kite"
                import beta from "beta.kite"
                
                var x = "hello"
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        boolean hasImportFolding = false;
        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            if (placeholderText != null && placeholderText.contains("imports")) {
                hasImportFolding = true;
                assertTrue("Should show correct count", placeholderText.contains("2"));
                break;
            }
        }
        assertTrue("Should have import folding region", hasImportFolding);
    }

    /**
     * Test that wildcard imports are also foldable.
     */
    public void testFoldWithWildcardImports() {
        configureByText("""
                import * from "common.kite"
                import alpha from "alpha.kite"
                import beta from "beta.kite"
                
                var x = "hello"
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        boolean hasImportFolding = false;
        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            if (placeholderText != null && placeholderText.contains("imports")) {
                hasImportFolding = true;
                assertTrue("Should show correct count", placeholderText.contains("3"));
                break;
            }
        }
        assertTrue("Should have import folding region", hasImportFolding);
    }

    /**
     * Test placeholder text format for import folding.
     */
    public void testImportFoldingPlaceholderText() {
        configureByText("""
                import alpha from "alpha.kite"
                import beta from "beta.kite"
                import gamma from "gamma.kite"
                import delta from "delta.kite"
                import epsilon from "epsilon.kite"
                
                var x = "hello"
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            if (placeholderText != null && placeholderText.contains("imports")) {
                assertEquals("[5 imports...]", placeholderText);
                return;
            }
        }
        fail("Should have import folding region");
    }

    /**
     * Test that imports are not collapsed by default.
     */
    public void testImportsNotCollapsedByDefault() {
        configureByText("""
                import alpha from "alpha.kite"
                import beta from "beta.kite"
                
                var x = "hello"
                """);

        KiteFoldingBuilder builder = new KiteFoldingBuilder();
        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        for (FoldingDescriptor descriptor : descriptors) {
            assertFalse("Imports should not be collapsed by default",
                    builder.isCollapsedByDefault(descriptor.getElement()));
        }
    }

    /**
     * Test that non-consecutive imports (with blank lines) are still folded together.
     * Note: Current implementation considers all imports at start of file as one block.
     */
    public void testImportsWithBlankLines() {
        configureByText("""
                import alpha from "alpha.kite"
                
                import beta from "beta.kite"
                
                var x = "hello"
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        // Check if we have import folding
        boolean hasImportFolding = false;
        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            if (placeholderText != null && placeholderText.contains("imports")) {
                hasImportFolding = true;
                break;
            }
        }
        assertTrue("Imports with blank lines should still be foldable", hasImportFolding);
    }

    /**
     * Test that existing block folding still works.
     */
    public void testBlockFoldingStillWorks() {
        configureByText("""
                schema Config {
                    string name
                    number port
                }
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        boolean hasBlockFolding = false;
        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            if (placeholderText != null && placeholderText.equals("{...}")) {
                hasBlockFolding = true;
                break;
            }
        }
        assertTrue("Schema block should be foldable", hasBlockFolding);
    }

    /**
     * Test file with no imports has no import folding.
     */
    public void testNoImports() {
        configureByText("""
                var x = "hello"
                var y = "world"
                """);

        FoldingDescriptor[] descriptors = getFoldingDescriptors();

        for (FoldingDescriptor descriptor : descriptors) {
            String placeholderText = descriptor.getPlaceholderText();
            assertFalse("File with no imports should not have import folding",
                    placeholderText != null && placeholderText.contains("imports"));
        }
    }

    /**
     * Helper method to get folding descriptors from the current file.
     */
    private FoldingDescriptor[] getFoldingDescriptors() {
        PsiFile file = myFixture.getFile();
        Document document = myFixture.getEditor().getDocument();
        KiteFoldingBuilder builder = new KiteFoldingBuilder();
        return builder.buildFoldRegions(file, document, false);
    }
}
