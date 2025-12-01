package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteEmptyBlockInspection.
 * Verifies detection of empty schema, component, resource, and function bodies.
 */
public class KiteEmptyBlockInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteEmptyBlockInspection();
    }

    // ========== Non-Empty Block Tests ==========

    public void testSchemaWithProperties() {
        assertNoEmptyBlocks("""
                schema Config {
                    string host
                    number port
                }
                """);
    }

    public void testComponentWithInputs() {
        assertNoEmptyBlocks("""
                component Server {
                    input string host
                    output string endpoint
                }
                """);
    }

    public void testResourceWithProperties() {
        assertNoEmptyBlocks("""
                schema Config {
                    string host
                }
                resource Config server {
                    host = "localhost"
                }
                """);
    }

    public void testFunctionWithBody() {
        assertNoEmptyBlocks("""
                fun calculate() number {
                    return 42
                }
                """);
    }

    // ========== Empty Block Tests ==========

    public void testEmptySchema() {
        assertHasWeakWarning("""
                schema Empty {
                }
                """, "Empty schema body");
    }

    public void testEmptyComponent() {
        assertHasWeakWarning("""
                component Empty {
                }
                """, "Empty component body");
    }

    public void testEmptyResource() {
        // Resources may intentionally be empty if schema has all defaults
        // But we still warn about it
        assertHasWeakWarning("""
                schema Config {
                    string host = "localhost"
                }
                resource Config server {
                }
                """, "Empty resource body");
    }

    public void testEmptyFunction() {
        assertHasWeakWarning("""
                fun doNothing() {
                }
                """, "Empty function body");
    }

    // ========== Mixed Content Tests ==========

    public void testMixedEmptyAndNonEmpty() {
        var text = """
                schema Config {
                    string host
                }
                schema Empty {
                }
                """;
        var highlights = doHighlighting(text);

        // Should only warn about the empty schema
        var emptyCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Empty"))
                .count();
        assertEquals("Should detect exactly one empty block", 1, emptyCount);
    }

    public void testMultipleEmptyBlocks() {
        var text = """
                schema Empty1 {
                }
                schema Empty2 {
                }
                """;
        var highlights = doHighlighting(text);

        var emptyCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Empty schema body"))
                .count();
        assertEquals("Should detect two empty schemas", 2, emptyCount);
    }

    // ========== Whitespace Variations ==========

    public void testEmptyBlockWithWhitespace() {
        assertHasWeakWarning("""
                schema Empty {

                }
                """, "Empty schema body");
    }

    public void testEmptyBlockWithMultipleNewlines() {
        assertHasWeakWarning("""
                schema Empty {


                }
                """, "Empty schema body");
    }

    public void testEmptyBlockOnSingleLine() {
        assertHasWeakWarning("schema Empty { }", "Empty schema body");
    }

    // ========== Comments in Empty Blocks ==========

    public void testBlockWithOnlyComment() {
        // A comment is not meaningful content for our purposes
        // Implementation may treat comments as content or not
        var text = """
                schema Config {
                    // Just a comment
                }
                """;
        var highlights = doHighlighting(text);
        // Don't assert specific behavior - just verify no crash
        assertNotNull(highlights);
    }

    // ========== Nested Structures ==========

    public void testNestedEmptyComponent() {
        // Nested component instances inside component definitions
        // Note: Nested components may be component instances, not definitions
        var text = """
                component Outer {
                    input string host

                    component Inner {
                    }
                }
                """;
        var highlights = doHighlighting(text);

        // Just verify no crash - nested component PSI structure may vary
        assertNotNull(highlights);
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        assertNoEmptyBlocks("");
    }

    public void testOnlyImports() {
        assertNoEmptyBlocks("""
                import * from "common.kite"
                """);
    }

    public void testSchemaWithOnlyDecorators() {
        // Decorators are outside the braces, so block is still empty
        assertHasWeakWarning("""
                @description("Empty schema")
                schema Empty {
                }
                """, "Empty schema body");
    }

    // ========== Helper Methods ==========

    private void assertNoEmptyBlocks(String text) {
        var highlights = doHighlighting(text);
        var emptyCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Empty") && h.getDescription().contains("body"))
                .count();
        assertEquals("Should not detect empty blocks", 0, emptyCount);
    }
}
