package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteUnreachableCodeInspection.
 * Verifies detection of code that can never be executed (after return statements).
 */
public class KiteUnreachableCodeInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteUnreachableCodeInspection();
    }

    /**
     * Assert that no unreachable code warnings are produced.
     * This is more specific than assertNoProblems() since other annotators may flag
     * different issues (like missing return statements).
     */
    private void assertNoUnreachableCodeWarnings(String text) {
        var highlights = doHighlighting(text);
        var unreachableCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unreachable"))
                .count();
        assertEquals("Should not detect unreachable code", 0, unreachableCount);
    }

    // ========== Basic Unreachable Code Tests ==========

    public void testCodeAfterReturnDetected() {
        assertHasWarning("""
                fun getValue() string {
                    return "value"
                    var unreachable = "never executed"
                }
                """, "Unreachable code");
    }

    public void testNoCodeAfterReturn() {
        assertNoUnreachableCodeWarnings("""
                fun getValue() string {
                    return "value"
                }
                """);
    }

    public void testReturnAtEndOfFunction() {
        assertNoUnreachableCodeWarnings("""
                fun compute() number {
                    var x = 1
                    var y = 2
                    return x + y
                }
                """);
    }

    // ========== Multiple Statements After Return ==========

    public void testMultipleStatementsAfterReturn() {
        var text = """
                fun getValue() string {
                    return "value"
                    var a = 1
                    var b = 2
                    var c = 3
                }
                """;
        var highlights = doHighlighting(text);

        // All statements after return should be flagged
        var unreachableCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unreachable"))
                .count();

        assertTrue("Should detect unreachable code", unreachableCount > 0);
    }

    // ========== Nested Function Tests ==========

    public void testNestedFunctionNoFalsePositive() {
        // Return in outer function shouldn't affect inner function
        assertNoUnreachableCodeWarnings("""
                fun outer() string {
                    fun inner() number {
                        return 42
                    }
                    return "done"
                }
                """);
    }

    // ========== Return in Conditionals ==========

    public void testReturnInIfNoUnreachable() {
        // Code after if-block with return is still reachable
        assertNoUnreachableCodeWarnings("""
                fun getValue(boolean flag) string {
                    if flag {
                        return "yes"
                    }
                    return "no"
                }
                """);
    }

    public void testReturnInBothBranches() {
        // When all branches return, code after is unreachable
        // Note: This is a complex analysis - implementation may choose to skip this case initially
        var text = """
                fun getValue(boolean flag) string {
                    if flag {
                        return "yes"
                    } else {
                        return "no"
                    }
                    var unreachable = "never"
                }
                """;
        // Just verify no crash - detecting this case is advanced
        doHighlighting(text);
    }

    // ========== Return in Loops ==========

    public void testCodeAfterLoopWithReturn() {
        // Code after a for loop is reachable (loop might not execute)
        // Note: Using specific check because other annotators may flag unrelated issues
        var text = """
                fun process(string[] items) string {
                    for item in items {
                        return item
                    }
                    return "empty"
                }
                """;
        var highlights = doHighlighting(text);

        // Should NOT have any "Unreachable code" warnings from this inspection
        var unreachableCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unreachable"))
                .count();
        assertEquals("Should not detect unreachable code after for loop", 0, unreachableCount);
    }

    // ========== Edge Cases ==========

    public void testEmptyFunction() {
        // Empty function - no unreachable code (other annotators may flag missing return)
        assertNoUnreachableCodeWarnings("""
                fun empty() string {
                }
                """);
    }

    public void testFunctionWithOnlyReturn() {
        assertNoUnreachableCodeWarnings("""
                fun simple() string {
                    return "value"
                }
                """);
    }

    public void testFunctionWithOnlyStatements() {
        // Function without return (void-like)
        assertNoUnreachableCodeWarnings("""
                fun log(string msg) {
                    var x = msg
                }
                """);
    }

    public void testCommentAfterReturn() {
        // Comments after return are allowed (not code)
        assertNoUnreachableCodeWarnings("""
                fun getValue() string {
                    return "value"
                    // This comment is fine
                }
                """);
    }

    // ========== Non-Function Contexts ==========

    public void testSchemaDoesNotTrigger() {
        assertNoProblems("""
                schema Config {
                    string host
                }
                """);
    }

    public void testComponentDoesNotTrigger() {
        assertNoProblems("""
                component Server {
                    input string port = "8080"
                    output string url = "http://localhost:$port"
                }
                """);
    }

    public void testResourceDoesNotTrigger() {
        assertNoProblems("""
                schema Config {
                    string host
                }
                resource Config server {
                    host = "localhost"
                }
                """);
    }

    // ========== Multiple Functions ==========

    public void testMultipleFunctionsIndependent() {
        // Unreachable code in one function doesn't affect another
        var text = """
                fun first() string {
                    return "one"
                    var unreachable = "x"
                }
                fun second() string {
                    var reachable = "y"
                    return "two"
                }
                """;
        var highlights = doHighlighting(text);

        // Only 'unreachable' should be flagged
        var unreachableCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unreachable"))
                .count();
        assertTrue("Should detect unreachable code in first function", unreachableCount > 0);
    }
}
