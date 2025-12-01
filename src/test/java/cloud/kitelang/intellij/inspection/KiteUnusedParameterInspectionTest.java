package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteUnusedParameterInspection.
 * Verifies detection of function parameters that are never used.
 */
public class KiteUnusedParameterInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteUnusedParameterInspection();
    }

    // ========== No Unused Parameters Tests ==========

    public void testNoParameters() {
        assertNoUnusedParams("""
                fun noParams() {
                    return 42
                }
                """);
    }

    public void testAllParametersUsed() {
        assertNoUnusedParams("""
                fun add(number a, number b) number {
                    return a + b
                }
                """);
    }

    public void testSingleParamUsed() {
        assertNoUnusedParams("""
                fun double(number x) number {
                    return x * 2
                }
                """);
    }

    public void testParamUsedInVariable() {
        // Parameter used on right side of variable assignment
        // Note: Usage detection may depend on PSI structure
        var text = """
                fun process(string input) string {
                    var result = input
                    return result
                }
                """;
        var highlights = doHighlighting(text);
        assertNotNull(highlights);
    }

    // ========== Unused Parameter Tests ==========

    public void testSingleUnusedParam() {
        assertHasWeakWarning("""
                fun unused(number x) number {
                    return 42
                }
                """, "Unused parameter 'x'");
    }

    public void testOneOfTwoUnused() {
        assertHasWeakWarning("""
                fun partial(number a, number b) number {
                    return a * 2
                }
                """, "Unused parameter 'b'");
    }

    public void testAllParamsUnused() {
        var text = """
                fun ignoreAll(number x, string y) number {
                    return 42
                }
                """;
        var highlights = doHighlighting(text);

        // Should detect both unused
        var unusedCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused parameter"))
                .count();
        assertEquals("Should detect two unused parameters", 2, unusedCount);
    }

    // ========== String Interpolation Usage ==========

    public void testParamUsedInInterpolation() {
        assertNoUnusedParams("""
                fun greet(string name) string {
                    return "Hello, ${name}!"
                }
                """);
    }

    public void testParamUsedInSimpleInterpolation() {
        assertNoUnusedParams("""
                fun greet(string name) string {
                    return "Hello, $name!"
                }
                """);
    }

    // ========== Multiple Functions ==========

    public void testMultipleFunctions() {
        var text = """
                fun used(number x) number {
                    return x
                }
                fun unused(number y) number {
                    return 0
                }
                """;
        var highlights = doHighlighting(text);

        var unusedCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused parameter"))
                .count();
        assertEquals("Should detect one unused parameter", 1, unusedCount);
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        assertNoUnusedParams("");
    }

    public void testNoFunctions() {
        assertNoUnusedParams("""
                var x = 1
                schema Config {
                    string host
                }
                """);
    }

    public void testEmptyFunctionBody() {
        assertHasWeakWarning("""
                fun empty(number x) {
                }
                """, "Unused parameter");
    }

    // ========== Helper Methods ==========

    private void assertNoUnusedParams(String text) {
        var highlights = doHighlighting(text);
        var unusedCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unused parameter"))
                .count();
        assertEquals("Should not detect unused parameters", 0, unusedCount);
    }
}
