package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for decorator argument validation.
 */
public class KiteDecoratorArgumentsAnnotatorTest extends KiteTestBase {

    // ========================================
    // Number argument decorators
    // ========================================

    public void testMinValueWithString() {
        configureByText("""
                @minValue("10")
                input number port
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @minValue with string argument", errors.isEmpty());
        assertTrue("Error should mention number argument",
                errors.stream().anyMatch(h -> h.getDescription().contains("number")));
    }

    public void testMinValueWithNumber() {
        configureByText("""
                @minValue(1)
                input number port = 8080
                """);

        var errors = getDecoratorArgumentErrors();
        assertTrue("Should not error for @minValue with number", errors.isEmpty());
    }

    public void testMaxValueWithBoolean() {
        configureByText("""
                @maxValue(true)
                input number port
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @maxValue with boolean argument", errors.isEmpty());
    }

    public void testCountWithString() {
        configureByText("""
                @count("3")
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @count with string argument", errors.isEmpty());
    }

    public void testCountWithNumber() {
        configureByText("""
                @count(3)
                resource EC2.Instance server { }
                """);

        var errors = getDecoratorArgumentErrors();
        assertTrue("Should not error for @count with number", errors.isEmpty());
    }

    // ========================================
    // String argument decorators
    // ========================================

    public void testDescriptionWithNumber() {
        configureByText("""
                @description(42)
                input string name
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @description with number argument", errors.isEmpty());
        assertTrue("Error should mention string argument",
                errors.stream().anyMatch(h -> h.getDescription().contains("string")));
    }

    public void testDescriptionWithString() {
        configureByText("""
                @description("The port number")
                input number port = 8080
                """);

        var errors = getDecoratorArgumentErrors();
        assertTrue("Should not error for @description with string", errors.isEmpty());
    }

    public void testExistingWithNumber() {
        configureByText("""
                @existing(123)
                resource S3.Bucket bucket { }
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @existing with number argument", errors.isEmpty());
    }

    // ========================================
    // No argument decorators
    // ========================================

    public void testNonEmptyWithArgument() {
        configureByText("""
                @nonEmpty(true)
                input string name
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @nonEmpty with argument", errors.isEmpty());
        assertTrue("Error should mention no arguments",
                errors.stream().anyMatch(h -> h.getDescription().contains("no argument")));
    }

    public void testNonEmptyNoArgument() {
        configureByText("""
                @nonEmpty
                input string name
                """);

        var errors = getDecoratorArgumentErrors();
        assertTrue("Should not error for @nonEmpty without argument", errors.isEmpty());
    }

    public void testSensitiveWithArgument() {
        configureByText("""
                @sensitive("yes")
                input string password
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @sensitive with argument", errors.isEmpty());
    }

    public void testUniqueWithArgument() {
        configureByText("""
                @unique(true)
                input string[] tags
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @unique with argument", errors.isEmpty());
    }

    // ========================================
    // Array argument decorators
    // ========================================

    public void testAllowedWithString() {
        configureByText("""
                @allowed("dev")
                input string environment
                """);

        var errors = getDecoratorArgumentErrors();
        assertFalse("Should error for @allowed with string argument", errors.isEmpty());
        assertTrue("Error should mention array argument",
                errors.stream().anyMatch(h -> h.getDescription().contains("array")));
    }

    public void testAllowedWithArray() {
        configureByText("""
                @allowed(["dev", "staging", "prod"])
                input string environment = "dev"
                """);

        var errors = getDecoratorArgumentErrors();
        assertTrue("Should not error for @allowed with array", errors.isEmpty());
    }

    // ========================================
    // Variable references (should be allowed)
    // ========================================

    public void testMinValueWithVariable() {
        configureByText("""
                var minPort = 1024
                @minValue(minPort)
                input number port
                """);

        var errors = getDecoratorArgumentErrors();
        assertTrue("Should not error for @minValue with variable reference", errors.isEmpty());
    }

    private List<HighlightInfo> getDecoratorArgumentErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().contains("@"))
                .collect(Collectors.toList());
    }
}
