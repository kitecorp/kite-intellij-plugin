package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteUnusedVariableInspection.
 * Verifies detection of unused variable declarations.
 */
public class KiteUnusedVariableInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteUnusedVariableInspection();
    }

    // ========== Basic Unused Variable Tests ==========

    public void testUnusedVariableDetected() {
        assertHasWarning("""
                var unusedVar = "value"
                """, "Variable 'unusedVar' is never used");
    }

    public void testUsedVariableNoWarning() {
        // usedVar is used in interpolation, so no warnings expected for it
        var text = """
                var usedVar = "value"
                var greeting = "Hello $usedVar!"
                """;
        var highlights = doHighlighting(text);

        // usedVar should NOT be flagged (it's used in the string)
        var usedVarFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'usedVar'"));
        assertFalse("usedVar should not be flagged - it's used in interpolation", usedVarFlagged);
    }

    public void testUsedInOtherVariable() {
        // Test that 'first' is used by 'second' (first is NOT flagged)
        // Note: second will be flagged as unused since nothing uses it
        var text = """
                var first = "hello"
                var second = first
                """;
        var highlights = doHighlighting(text);

        // 'first' should NOT be flagged
        var firstFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'first'"));
        assertFalse("'first' should not be flagged - it's used by 'second'", firstFlagged);
    }

    public void testChainedVariablesLastUnused() {
        // In a chain, only the last one (unused) should be flagged
        var text = """
                var first = "hello"
                var second = first
                var third = second
                """;
        var highlights = doHighlighting(text);

        // Only 'third' should be flagged as unused
        var thirdFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'third'"));
        assertTrue("'third' should be flagged - nothing uses it", thirdFlagged);

        // 'first' and 'second' should NOT be flagged
        var firstFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'first'"));
        assertFalse("'first' should not be flagged - it's used by 'second'", firstFlagged);

        var secondFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'second'"));
        assertFalse("'second' should not be flagged - it's used by 'third'", secondFlagged);
    }

    // ========== String Interpolation Tests ==========

    public void testUsedInSimpleInterpolation() {
        // 'name' is used in the string interpolation
        var text = """
                var name = "World"
                var greeting = "Hello $name!"
                """;
        var highlights = doHighlighting(text);

        // 'name' should NOT be flagged
        var nameFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'name'"));
        assertFalse("'name' should not be flagged - used in $name interpolation", nameFlagged);
    }

    public void testUsedInBraceInterpolation() {
        // 'port' is used in the ${} interpolation
        var text = """
                var port = 8080
                var url = "http://localhost:${port}"
                """;
        var highlights = doHighlighting(text);

        // 'port' should NOT be flagged
        var portFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'port'"));
        assertFalse("'port' should not be flagged - used in ${port} interpolation", portFlagged);
    }

    public void testUsedInComplexInterpolation() {
        // Both 'host' and 'port' are used in the string
        var text = """
                var host = "localhost"
                var port = 8080
                var url = "http://${host}:${port}/api"
                """;
        var highlights = doHighlighting(text);

        // Neither 'host' nor 'port' should be flagged
        var hostFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'host'"));
        assertFalse("'host' should not be flagged - used in interpolation", hostFlagged);

        var portFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'port'"));
        assertFalse("'port' should not be flagged - used in interpolation", portFlagged);
    }

    // ========== Resource/Component Usage Tests ==========

    public void testUsedInResourceProperty() {
        // 'serverHost' is used as a value in the resource
        var text = """
                schema Config {
                    string host
                }
                var serverHost = "localhost"
                resource Config server {
                    host = serverHost
                }
                """;
        var highlights = doHighlighting(text);

        // 'serverHost' should NOT be flagged
        var serverHostFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'serverHost'"));
        assertFalse("'serverHost' should not be flagged - used in resource property", serverHostFlagged);
    }

    public void testUsedInComponentInput() {
        // 'defaultPort' is used as a default value in the component
        var text = """
                var defaultPort = "8080"
                component WebServer {
                    input string port = defaultPort
                }
                """;
        var highlights = doHighlighting(text);

        // 'defaultPort' should NOT be flagged
        var defaultPortFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'defaultPort'"));
        assertFalse("'defaultPort' should not be flagged - used in component input", defaultPortFlagged);
    }

    // ========== Property Access Tests ==========

    public void testUsedWithPropertyAccess() {
        // Test that the resource 'server' is recognized as used when accessed via property
        var text = """
                schema Config {
                    string host
                }
                resource Config server {
                    host = "localhost"
                }
                var endpoint = server.host
                """;
        var highlights = doHighlighting(text);

        // This test verifies the endpoint uses server, but server is a resource not a variable
        // The test should just verify no crash and expected behavior
        assertNotNull(highlights);
    }

    // ========== Function Tests ==========

    public void testUsedInFunctionCall() {
        // 'message' is used as a function argument
        var text = """
                var message = "hello"
                fun log(string msg) string {
                    return msg
                }
                var result = log(message)
                """;
        var highlights = doHighlighting(text);

        // 'message' should NOT be flagged
        var messageFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'message'"));
        assertFalse("'message' should not be flagged - used in function call", messageFlagged);
    }

    public void testUsedInFunctionBody() {
        // 'baseUrl' is used in the function return
        var text = """
                var baseUrl = "http://localhost"
                fun getUrl() string {
                    return baseUrl
                }
                """;
        var highlights = doHighlighting(text);

        // 'baseUrl' should NOT be flagged
        var baseUrlFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'baseUrl'"));
        assertFalse("'baseUrl' should not be flagged - used in function body", baseUrlFlagged);
    }

    // ========== Multiple Declarations Tests ==========

    public void testMultipleUnusedVariables() {
        var highlights = doHighlighting("""
                var unused1 = "a"
                var unused2 = "b"
                var unused3 = "c"
                """);

        var unusedCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("is never used"))
                .count();

        assertEquals("Should detect 3 unused variables", 3, unusedCount);
    }

    public void testMixedUsedAndUnusedVariables() {
        var text = """
                var used1 = "value"
                var unused1 = "value"
                var used2 = used1
                var unused2 = "value"
                """;

        var highlights = doHighlighting(text);

        // unused1 and unused2 should be flagged
        assertTrue("unused1 should be flagged",
                highlights.stream().anyMatch(d -> d.getDescription() != null && d.getDescription().contains("'unused1'")));
        assertTrue("unused2 should be flagged",
                highlights.stream().anyMatch(d -> d.getDescription() != null && d.getDescription().contains("'unused2'")));

        // used1 should NOT be flagged (it's used by used2)
        assertFalse("used1 should not be flagged",
                highlights.stream().anyMatch(d -> d.getDescription() != null && d.getDescription().contains("'used1'")));
    }

    // ========== Typed Variable Tests ==========

    public void testUnusedTypedVariable() {
        assertHasWarning("""
                var string typedVar = "value"
                """, "Variable 'typedVar' is never used");
    }

    public void testUsedTypedVariable() {
        // 'typedVar' is used in the string
        var text = """
                var string typedVar = "value"
                var greeting = "Hello $typedVar"
                """;
        var highlights = doHighlighting(text);

        // 'typedVar' should NOT be flagged
        var typedVarFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'typedVar'"));
        assertFalse("'typedVar' should not be flagged - used in interpolation", typedVarFlagged);
    }

    // ========== Edge Cases ==========

    public void testVariableUsedOnlyInComment() {
        // Variables in comments don't count as usage
        assertHasWarning("""
                var myVar = "value"
                // myVar is used here in comment
                """, "Variable 'myVar' is never used");
    }

    public void testEmptyFile() {
        assertNoProblems("");
    }

    public void testFileWithOnlyComments() {
        assertNoProblems("""
                // Just a comment
                /* Block comment */
                """);
    }

    public void testVariableUsedInForLoop() {
        // 'items' is used in the for loop
        var text = """
                var items = ["a", "b", "c"]
                for item in items {
                    var x = item
                }
                """;
        var highlights = doHighlighting(text);

        // 'items' should NOT be flagged
        var itemsFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'items'"));
        assertFalse("'items' should not be flagged - used in for loop", itemsFlagged);
    }

    // ========== Import Tests ==========

    public void testVariableUsedFromImport() {
        // Add imported file
        addFile("common.kite", """
                var sharedValue = "shared"
                """);

        // 'sharedValue' from the import is used, 'localValue' is not
        var text = """
                import * from "common.kite"
                var localValue = sharedValue
                """;
        var highlights = doHighlighting(text);

        // Just verify no errors and that localValue is properly flagged
        assertTrue("localValue should be flagged as unused",
                highlights.stream().anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'localValue'")));
    }

    // ========== Return Statement Tests ==========

    public void testVariableUsedInReturn() {
        // 'result' is used in the return statement
        var text = """
                fun getValue() string {
                    var result = "value"
                    return result
                }
                """;
        var highlights = doHighlighting(text);

        // 'result' should NOT be flagged
        var resultFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'result'"));
        assertFalse("'result' should not be flagged - used in return", resultFlagged);
    }

    // ========== Self-Reference Tests ==========

    public void testSelfReferenceStillUnused() {
        // A variable that only references itself is still effectively unused
        configureByText("""
                var recursive = recursive
                """);
        myFixture.doHighlighting();
        // Just verify it doesn't crash
    }
}
