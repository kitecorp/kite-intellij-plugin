package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteLargeFunctionInspection.
 * Verifies detection of functions that exceed the line limit.
 */
public class KiteLargeFunctionInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteLargeFunctionInspection();
    }

    // ========== Small Function Tests ==========

    public void testEmptyFunction() {
        assertNoLargeFunctions("""
                fun empty() {
                }
                """);
    }

    public void testSmallFunction() {
        assertNoLargeFunctions("""
                fun small() number {
                    var x = 1
                    var y = 2
                    return x + y
                }
                """);
    }

    public void testMediumFunction() {
        // 10 lines should be fine
        var sb = new StringBuilder();
        sb.append("fun medium() number {\n");
        for (int i = 0; i < 10; i++) {
            sb.append("    var line").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("    return 0\n");
        sb.append("}\n");

        assertNoLargeFunctions(sb.toString());
    }

    // ========== Large Function Tests ==========

    public void testLargeFunction() {
        // 35+ lines should trigger warning
        var sb = new StringBuilder();
        sb.append("fun large() number {\n");
        for (int i = 0; i < 35; i++) {
            sb.append("    var line").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("    return 0\n");
        sb.append("}\n");

        assertHasWeakWarning(sb.toString(), "exceeds");
    }

    public void testVeryLargeFunction() {
        // 50+ lines
        var sb = new StringBuilder();
        sb.append("fun veryLarge() number {\n");
        for (int i = 0; i < 50; i++) {
            sb.append("    var line").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("    return 0\n");
        sb.append("}\n");

        assertHasWeakWarning(sb.toString(), "exceeds");
    }

    // ========== Multiple Functions ==========

    public void testMultipleFunctionsOneLarge() {
        var sb = new StringBuilder();

        // Small function
        sb.append("fun small() { return 1 }\n\n");

        // Large function
        sb.append("fun large() number {\n");
        for (int i = 0; i < 35; i++) {
            sb.append("    var x").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("    return 0\n");
        sb.append("}\n");

        var text = sb.toString();
        var highlights = doHighlighting(text);

        // Should only detect one large function
        var largeCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("exceeds"))
                .count();
        assertEquals("Should detect one large function", 1, largeCount);
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        assertNoLargeFunctions("");
    }

    public void testNoFunctions() {
        assertNoLargeFunctions("""
                var x = 1
                schema Config {
                    string host
                }
                """);
    }

    public void testFunctionWithComments() {
        // Comments count towards line count
        var sb = new StringBuilder();
        sb.append("fun withComments() {\n");
        for (int i = 0; i < 35; i++) {
            sb.append("    // Comment line ").append(i).append("\n");
        }
        sb.append("}\n");

        // 35 comment lines should trigger warning
        assertHasWeakWarning(sb.toString(), "exceeds");
    }

    public void testExactlyAtLimit() {
        // 30 lines exactly should not trigger
        var sb = new StringBuilder();
        sb.append("fun atLimit() number {\n");
        for (int i = 0; i < 28; i++) {
            sb.append("    var x").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("    return 0\n");
        sb.append("}\n");

        assertNoLargeFunctions(sb.toString());
    }

    // ========== Helper Methods ==========

    private void assertNoLargeFunctions(String text) {
        var highlights = doHighlighting(text);
        var largeCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("exceeds"))
                .count();
        assertEquals("Should not detect large functions", 0, largeCount);
    }
}
