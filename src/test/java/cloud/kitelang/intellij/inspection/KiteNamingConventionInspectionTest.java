package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteNamingConventionInspection.
 * Verifies detection of naming convention violations.
 */
public class KiteNamingConventionInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteNamingConventionInspection();
    }

    // ========== Valid Naming Tests ==========

    public void testValidVariableCamelCase() {
        assertNoNamingViolations("""
                var myVariable = 1
                var anotherVar = 2
                var x = 3
                """);
    }

    public void testValidSchemaPascalCase() {
        assertNoNamingViolations("""
                schema MySchema {
                    string host
                }
                schema DatabaseConfig {
                    number port
                }
                """);
    }

    public void testValidFunctionCamelCase() {
        assertNoNamingViolations("""
                fun calculateTotal(number x) number {
                    return x * 2
                }
                fun getData() string {
                    return "data"
                }
                """);
    }

    public void testValidComponentPascalCase() {
        assertNoNamingViolations("""
                component WebServer {
                    input string port
                }
                component DatabaseConnection {
                    input string host
                }
                """);
    }

    public void testValidResourceInstanceCamelCase() {
        assertNoNamingViolations("""
                schema Config {
                    string host
                }
                resource Config myServer {
                    host = "localhost"
                }
                resource Config primaryDb {
                    host = "db.example.com"
                }
                """);
    }

    // ========== Invalid Variable Naming ==========

    public void testVariableStartsWithUppercase() {
        assertHasWeakWarning("""
                var MyVariable = 1
                """, "should use camelCase");
    }

    public void testVariableAllUppercase() {
        assertHasWeakWarning("""
                var CONSTANT = 42
                """, "should use camelCase");
    }

    // ========== Invalid Schema Naming ==========

    public void testSchemaStartsWithLowercase() {
        assertHasWeakWarning("""
                schema mySchema {
                    string host
                }
                """, "should use PascalCase");
    }

    // ========== Invalid Function Naming ==========

    public void testFunctionStartsWithUppercase() {
        assertHasWeakWarning("""
                fun CalculateTotal(number x) number {
                    return x
                }
                """, "should use camelCase");
    }

    // ========== Invalid Component Naming ==========

    public void testComponentDefinitionLowercase() {
        assertHasWeakWarning("""
                component webServer {
                    input string port
                }
                """, "should use PascalCase");
    }

    // ========== Invalid Resource Naming ==========

    public void testResourceInstanceUppercase() {
        assertHasWeakWarning("""
                schema Config {
                    string host
                }
                resource Config MyServer {
                    host = "localhost"
                }
                """, "should use camelCase");
    }

    // ========== Mixed Valid and Invalid ==========

    public void testMixedNaming() {
        var text = """
                var validVar = 1
                var InvalidVar = 2
                schema ValidSchema {
                    string host
                }
                schema invalidSchema {
                    number port
                }
                """;
        var highlights = doHighlighting(text);

        // Should detect both violations
        var violationCount = highlights.stream()
                .filter(h -> h.getDescription() != null &&
                            (h.getDescription().contains("camelCase") ||
                             h.getDescription().contains("PascalCase")))
                .count();
        assertTrue("Should detect naming violations", violationCount >= 2);
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        assertNoNamingViolations("");
    }

    public void testSingleLetterNames() {
        // Single lowercase letter is valid camelCase
        assertNoNamingViolations("""
                var x = 1
                fun f() { }
                """);
    }

    public void testSingleUppercaseLetter() {
        // Single uppercase letter is valid PascalCase
        assertNoNamingViolations("""
                schema A {
                    string x
                }
                """);
    }

    public void testNumbersInNames() {
        assertNoNamingViolations("""
                var config1 = "first"
                var config2 = "second"
                schema Database2Config {
                    string host
                }
                """);
    }

    // ========== Helper Methods ==========

    private void assertNoNamingViolations(String text) {
        var highlights = doHighlighting(text);
        var violationCount = highlights.stream()
                .filter(h -> h.getDescription() != null &&
                            (h.getDescription().contains("camelCase") ||
                             h.getDescription().contains("PascalCase")))
                .count();
        assertEquals("Should not detect naming violations", 0, violationCount);
    }
}
