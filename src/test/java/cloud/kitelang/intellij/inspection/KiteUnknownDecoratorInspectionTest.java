package cloud.kitelang.intellij.inspection;

import com.intellij.codeInspection.LocalInspectionTool;

/**
 * Tests for KiteUnknownDecoratorInspection.
 * Verifies detection of unknown/invalid decorator names.
 */
public class KiteUnknownDecoratorInspectionTest extends KiteInspectionTestBase {

    @Override
    protected LocalInspectionTool getInspection() {
        return new KiteUnknownDecoratorInspection();
    }

    // ========== Valid Decorator Tests ==========

    public void testValidDescriptionDecorator() {
        assertNoUnknownDecorators("""
                @description("A test resource")
                schema Config {
                    string host
                }
                """);
    }

    public void testValidTagsDecorator() {
        assertNoUnknownDecorators("""
                @tags({Environment: "prod"})
                schema Config {
                    string host
                }
                """);
    }

    public void testValidMinValueDecorator() {
        assertNoUnknownDecorators("""
                schema Config {
                    @minValue(0)
                    number port
                }
                """);
    }

    public void testValidMaxLengthDecorator() {
        assertNoUnknownDecorators("""
                schema Config {
                    @maxLength(100)
                    string name
                }
                """);
    }

    public void testValidDependsOnDecorator() {
        assertNoUnknownDecorators("""
                schema DB {
                    string host
                }
                resource DB database {
                    host = "localhost"
                }
                @dependsOn(database)
                resource DB cache {
                    host = "localhost"
                }
                """);
    }

    public void testMultipleValidDecorators() {
        assertNoUnknownDecorators("""
                @description("Config schema")
                @tags({Team: "backend"})
                schema Config {
                    @minValue(1)
                    @maxValue(65535)
                    number port
                }
                """);
    }

    // ========== Unknown Decorator Tests ==========

    public void testUnknownDecoratorDetected() {
        assertHasWarning("""
                @unknownDecorator("value")
                schema Config {
                    string host
                }
                """, "Unknown decorator");
    }

    public void testMisspelledDecoratorDetected() {
        assertHasWarning("""
                @descripion("typo in decorator")
                schema Config {
                    string host
                }
                """, "Unknown decorator");
    }

    public void testCustomDecoratorDetected() {
        // Custom decorators that aren't in the known list
        assertHasWarning("""
                @myCustomDecorator
                schema Config {
                    string host
                }
                """, "Unknown decorator");
    }

    public void testUnknownOnProperty() {
        assertHasWarning("""
                schema Config {
                    @invalidValidator
                    string name
                }
                """, "Unknown decorator");
    }

    // ========== All Known Decorators ==========

    public void testAllValidationDecorators() {
        assertNoUnknownDecorators("""
                schema Config {
                    @minValue(0)
                    @maxValue(100)
                    number amount
                
                    @minLength(1)
                    @maxLength(255)
                    @nonEmpty
                    string name
                
                    @allowed(["a", "b"])
                    @unique
                    string[] tags
                
                    @validate("custom")
                    string custom
                }
                """);
    }

    public void testAllResourceDecorators() {
        assertNoUnknownDecorators("""
                schema Ref {
                    string id
                }
                resource Ref existing_ref {
                    id = "123"
                }
                @existing
                @sensitive
                @dependsOn(existing_ref)
                @tags({Env: "prod"})
                @provider("aws")
                resource Ref main {
                    id = "456"
                }
                """);
    }

    public void testAllMetadataDecorators() {
        assertNoUnknownDecorators("""
                @description("Main config")
                @count(3)
                @cloud("aws")
                schema Config {
                    string host
                }
                """);
    }

    // ========== Edge Cases ==========

    public void testNoDecorators() {
        assertNoUnknownDecorators("""
                schema Config {
                    string host
                }
                """);
    }

    public void testEmptyFile() {
        assertNoUnknownDecorators("");
    }

    public void testDecoratorWithoutParens() {
        // Some decorators can be used without arguments
        assertNoUnknownDecorators("""
                schema Config {
                    @nonEmpty
                    @unique
                    string[] items
                }
                """);
    }

    // ========== Helper Methods ==========

    private void assertNoUnknownDecorators(String text) {
        var highlights = doHighlighting(text);
        var unknownDecoratorCount = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Unknown decorator"))
                .count();
        assertEquals("Should not detect unknown decorators", 0, unknownDecoratorCount);
    }
}
