package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteDeepNestingInspection.
 * Verifies detection of deeply nested code structures.
 *
 * Note: Kite may not have traditional if/for/while constructs,
 * so these tests focus on the inspection not crashing and
 * basic scenarios that work with the language structure.
 */
public class KiteDeepNestingInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteDeepNestingInspection();
    }

    // ========== No Nesting Tests ==========

    public void testEmptyFile() {
        assertNoDeepNesting("");
    }

    public void testSimpleFunction() {
        assertNoDeepNesting("""
                fun simple() number {
                    var x = 1
                    return x
                }
                """);
    }

    public void testFunctionWithVariables() {
        assertNoDeepNesting("""
                fun calculate(number a, number b) number {
                    var sum = a + b
                    var product = a * b
                    var result = sum + product
                    return result
                }
                """);
    }

    public void testMultipleFunctions() {
        assertNoDeepNesting("""
                fun first() number {
                    return 1
                }
                fun second() number {
                    return 2
                }
                fun third() number {
                    return 3
                }
                """);
    }

    // ========== Schema and Component Tests ==========

    public void testSchemaNoNesting() {
        assertNoDeepNesting("""
                schema Config {
                    string host
                    number port
                    boolean ssl
                }
                """);
    }

    public void testComponentNoNesting() {
        assertNoDeepNesting("""
                component Server {
                    input string host
                    input number port
                    output string endpoint
                }
                """);
    }

    public void testResourceNoNesting() {
        assertNoDeepNesting("""
                schema Database {
                    string connectionString
                }
                resource Database primary {
                    connectionString = "postgres://localhost/db"
                }
                """);
    }

    // ========== Complex But Valid Code ==========

    public void testFunctionWithMultipleReturns() {
        // Multiple returns don't create nesting
        assertNoDeepNesting("""
                fun getValue(number x) number {
                    var doubled = x * 2
                    var tripled = x * 3
                    return doubled + tripled
                }
                """);
    }

    public void testFunctionCallingOtherFunction() {
        // Function calls don't create nesting depth
        assertNoDeepNesting("""
                fun helper(number x) number {
                    return x * 2
                }
                fun main(number y) number {
                    var result = helper(y)
                    return result
                }
                """);
    }

    // ========== Edge Cases ==========

    public void testEmptyFunction() {
        assertNoDeepNesting("""
                fun empty() {
                }
                """);
    }

    public void testNoFunctions() {
        assertNoDeepNesting("""
                var x = 1
                var y = 2
                """);
    }

    public void testOnlySchemas() {
        assertNoDeepNesting("""
                schema First {
                    string a
                }
                schema Second {
                    string b
                }
                """);
    }

    // ========== Helper Methods ==========

    private void assertNoDeepNesting(String text) {
        var highlights = doHighlighting(text);
        var nestingCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Deeply nested"))
                .count();
        assertEquals("Should not detect deep nesting", 0, nestingCount);
    }
}
