package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for decorator target validation.
 * Validates that decorators are applied to the correct declaration types.
 */
public class KiteDecoratorTargetsAnnotatorTest extends KiteTestBase {

    // ========================================
    // @count - only on resource/component instances
    // ========================================

    public void testCountOnResource() {
        configureByText("""
                @count(3)
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@count should be valid on resource", errors.isEmpty());
    }

    public void testCountOnInput() {
        configureByText("""
                component Server {
                    @count(3)
                    input string name
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@count should NOT be valid on input", errors.isEmpty());
        assertTrue("Error should mention @count target",
                errors.stream().anyMatch(h -> h.getDescription().contains("@count")));
    }

    public void testCountOnSchema() {
        configureByText("""
                @count(3)
                schema Config { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@count should NOT be valid on schema", errors.isEmpty());
    }

    // ========================================
    // @existing - only on resource
    // ========================================

    public void testExistingOnResource() {
        configureByText("""
                @existing("arn:aws:s3:::my-bucket")
                resource S3.Bucket bucket { }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@existing should be valid on resource", errors.isEmpty());
    }

    public void testExistingOnInput() {
        configureByText("""
                component Server {
                    @existing("arn:aws:s3:::my-bucket")
                    input string name
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@existing should NOT be valid on input", errors.isEmpty());
    }

    public void testExistingOnSchema() {
        configureByText("""
                @existing("arn:aws:s3:::my-bucket")
                schema Config { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@existing should NOT be valid on schema", errors.isEmpty());
    }

    // ========================================
    // @cloud - only on schema property
    // ========================================

    public void testCloudOnSchemaProperty() {
        configureByText("""
                schema Instance {
                    string id
                    @cloud
                    string publicIp
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@cloud should be valid on schema property", errors.isEmpty());
    }

    public void testCloudOnInput() {
        configureByText("""
                component Server {
                    @cloud
                    input string name
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@cloud should NOT be valid on input", errors.isEmpty());
    }

    public void testCloudOnResource() {
        configureByText("""
                @cloud
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@cloud should NOT be valid on resource", errors.isEmpty());
    }

    // ========================================
    // @minValue/@maxValue - only on input/output with number type
    // ========================================

    public void testMinValueOnNumberInput() {
        configureByText("""
                component Server {
                    @minValue(1)
                    input number port
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@minValue should be valid on number input", errors.isEmpty());
    }

    public void testMinValueOnNumberOutput() {
        configureByText("""
                component Server {
                    @minValue(1)
                    output number totalCount = 10
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@minValue should be valid on number output", errors.isEmpty());
    }

    public void testMinValueOnResource() {
        configureByText("""
                @minValue(1)
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@minValue should NOT be valid on resource", errors.isEmpty());
    }

    public void testMinValueOnSchema() {
        configureByText("""
                @minValue(1)
                schema Config { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@minValue should NOT be valid on schema", errors.isEmpty());
    }

    // ========================================
    // @nonEmpty - only on input with string/array type
    // ========================================

    public void testNonEmptyOnStringInput() {
        configureByText("""
                component Server {
                    @nonEmpty
                    input string name
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@nonEmpty should be valid on string input", errors.isEmpty());
    }

    public void testNonEmptyOnArrayInput() {
        configureByText("""
                component Server {
                    @nonEmpty
                    input string[] tags
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@nonEmpty should be valid on array input", errors.isEmpty());
    }

    public void testNonEmptyOnResource() {
        configureByText("""
                @nonEmpty
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@nonEmpty should NOT be valid on resource", errors.isEmpty());
    }

    // ========================================
    // @unique - only on input with array type
    // ========================================

    public void testUniqueOnArrayInput() {
        configureByText("""
                component Server {
                    @unique
                    input string[] tags
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@unique should be valid on array input", errors.isEmpty());
    }

    public void testUniqueOnResource() {
        configureByText("""
                @unique
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@unique should NOT be valid on resource", errors.isEmpty());
    }

    // ========================================
    // @description - valid on many targets
    // ========================================

    public void testDescriptionOnInput() {
        configureByText("""
                component Server {
                    @description("The server port")
                    input number port = 8080
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@description should be valid on input", errors.isEmpty());
    }

    public void testDescriptionOnResource() {
        configureByText("""
                @description("Main database")
                resource RDS.Instance database { }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@description should be valid on resource", errors.isEmpty());
    }

    public void testDescriptionOnSchema() {
        configureByText("""
                @description("Database configuration")
                schema Config { }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@description should be valid on schema", errors.isEmpty());
    }

    public void testDescriptionOnFunction() {
        configureByText("""
                @description("Calculate total cost")
                fun calculate() number {
                    return 0
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@description should be valid on function", errors.isEmpty());
    }

    // ========================================
    // @sensitive - only on input/output
    // ========================================

    public void testSensitiveOnInput() {
        configureByText("""
                component Server {
                    @sensitive
                    input string apiKey
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@sensitive should be valid on input", errors.isEmpty());
    }

    public void testSensitiveOnOutput() {
        configureByText("""
                component Server {
                    @sensitive
                    output string connectionString = ""
                }
                """);

        var errors = getDecoratorTargetErrors();
        assertTrue("@sensitive should be valid on output", errors.isEmpty());
    }

    public void testSensitiveOnResource() {
        configureByText("""
                @sensitive
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorTargetErrors();
        assertFalse("@sensitive should NOT be valid on resource", errors.isEmpty());
    }

    private List<HighlightInfo> getDecoratorTargetErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             (h.getDescription().contains("cannot be applied") ||
                              h.getDescription().contains("only valid on")))
                .collect(Collectors.toList());
    }
}
