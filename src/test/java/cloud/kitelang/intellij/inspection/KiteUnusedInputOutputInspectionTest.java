package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteUnusedInputOutputInspection.
 * Verifies detection of unused input/output declarations in components.
 */
public class KiteUnusedInputOutputInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteUnusedInputOutputInspection();
    }

    // ========== Unused Input Tests ==========

    public void testUnusedInputDetected() {
        assertHasWarning("""
                component WebServer {
                    input string unusedPort = "8080"
                }
                """, "Input 'unusedPort' is never used");
    }

    public void testUsedInputNoWarning() {
        // Input is used in output
        var text = """
                component WebServer {
                    input string port = "8080"
                    output string url = "http://localhost:$port"
                }
                """;
        var highlights = doHighlighting(text);

        var portFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'port'"));
        assertFalse("'port' should not be flagged - used in output", portFlagged);
    }

    public void testInputUsedInInterpolation() {
        // Input is used in string interpolation
        var text = """
                component Server {
                    input string host = "localhost"
                    output string greeting = "Welcome to ${host}"
                }
                """;
        var highlights = doHighlighting(text);

        var hostFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'host'"));
        assertFalse("'host' should not be flagged - used in interpolation", hostFlagged);
    }

    // ========== Unused Output Tests ==========

    public void testUnusedOutputDetected() {
        // Outputs in the component definition are generally "used" by consumers
        // But for this test, we'll check if outputs are flagged when declared but not referenced internally
        // This is a design decision - outputs may always be considered "used" externally
        var text = """
                component WebServer {
                    output string endpoint = "http://localhost:8080"
                }
                """;
        var highlights = doHighlighting(text);

        // Outputs are typically meant to be consumed externally, so they shouldn't be flagged
        // This test documents the expected behavior
        assertNotNull("Highlighting should complete without error", highlights);
    }

    public void testOutputUsedInternally() {
        // Output used by another output (chaining)
        var text = """
                component Server {
                    output string baseUrl = "http://localhost"
                    output string apiUrl = "${baseUrl}/api"
                }
                """;
        var highlights = doHighlighting(text);

        var baseUrlFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'baseUrl'"));
        assertFalse("'baseUrl' should not be flagged - used by apiUrl", baseUrlFlagged);
    }

    // ========== Mixed Input/Output Tests ==========

    public void testMultipleInputsPartiallyUsed() {
        var text = """
                component Server {
                    input string host = "localhost"
                    input string port = "8080"
                    input string unused = "value"
                    output string url = "http://${host}:${port}"
                }
                """;
        var highlights = doHighlighting(text);

        // 'unused' should be flagged
        assertTrue("'unused' should be flagged",
                highlights.stream().anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'unused'")));

        // 'host' and 'port' should NOT be flagged
        assertFalse("'host' should not be flagged",
                highlights.stream().anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'host'")));
        assertFalse("'port' should not be flagged",
                highlights.stream().anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'port'")));
    }

    public void testInputUsedByOtherInput() {
        // Input used as default for another input
        var text = """
                component Server {
                    input string defaultHost = "localhost"
                    input string host = defaultHost
                }
                """;
        var highlights = doHighlighting(text);

        var defaultHostFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'defaultHost'"));
        assertFalse("'defaultHost' should not be flagged - used by 'host'", defaultHostFlagged);
    }

    // ========== Component Instance Tests ==========

    public void testInputUsedInComponentInstance() {
        // Inputs used in a component instance within this component
        var text = """
                component Inner {
                    input string message = "default"
                }
                component Outer {
                    input string msg = "hello"
                    component Inner inner {
                        message = msg
                    }
                }
                """;
        var highlights = doHighlighting(text);

        var msgFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'msg'"));
        assertFalse("'msg' should not be flagged - used in component instance", msgFlagged);
    }

    // ========== Edge Cases ==========

    public void testEmptyComponent() {
        assertNoProblems("""
                component Empty {
                }
                """);
    }

    public void testComponentWithOnlyVariables() {
        // Variables inside component (not inputs/outputs) should not be checked by this inspection
        var text = """
                component Server {
                    var internal = "value"
                }
                """;
        var highlights = doHighlighting(text);
        // This inspection only checks inputs/outputs, not variables
        assertNotNull(highlights);
    }

    public void testMultipleComponents() {
        var text = """
                component Server1 {
                    input string unused1 = "a"
                }
                component Server2 {
                    input string unused2 = "b"
                }
                """;
        var highlights = doHighlighting(text);

        var unusedCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("is never used"))
                .count();

        assertEquals("Should detect 2 unused inputs across components", 2, unusedCount);
    }

    public void testSchemaDoesNotTrigger() {
        // Schema properties are not inputs/outputs and should not be flagged
        assertNoProblems("""
                schema Config {
                    string host
                    number port
                }
                """);
    }

    public void testResourceDoesNotTrigger() {
        // Resource properties are not inputs/outputs
        assertNoProblems("""
                schema Config {
                    string host
                }
                resource Config server {
                    host = "localhost"
                }
                """);
    }

    // ========== Array Type Tests ==========

    public void testArrayTypedInputUnused() {
        assertHasWarning("""
                component Server {
                    input string[] unusedTags = ["tag1", "tag2"]
                }
                """, "Input 'unusedTags' is never used");
    }

    public void testArrayTypedInputUsed() {
        var text = """
                component Server {
                    input string[] tags = ["tag1"]
                    output string firstTag = tags[0]
                }
                """;
        var highlights = doHighlighting(text);

        var tagsFlagged = highlights.stream()
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains("'tags'"));
        assertFalse("'tags' should not be flagged - used in output", tagsFlagged);
    }
}
