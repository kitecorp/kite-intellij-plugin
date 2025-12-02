package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for invalid string interpolation detection.
 * Validates that unclosed ${...} interpolations are flagged as errors.
 */
public class KiteInvalidInterpolationAnnotatorTest extends KiteTestBase {

    // ========================================
    // Unclosed interpolation errors
    // ========================================

    public void testUnclosedInterpolation() {
        configureByText("""
                var x = "Hello ${name"
                """);

        var errors = getInterpolationErrors();
        assertFalse("Should detect unclosed interpolation", errors.isEmpty());
        assertTrue("Error should mention unclosed",
                errors.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("unclosed")));
    }

    public void testUnclosedInterpolationWithDot() {
        configureByText("""
                var x = "Value: ${a.b"
                """);

        var errors = getInterpolationErrors();
        assertFalse("Should detect unclosed interpolation", errors.isEmpty());
    }

    public void testUnclosedInterpolationWithIndex() {
        configureByText("""
                var x = "Item: ${items[0"
                """);

        var errors = getInterpolationErrors();
        assertFalse("Should detect unclosed interpolation", errors.isEmpty());
    }

    // ========================================
    // Valid cases - no errors
    // ========================================

    public void testValidClosedInterpolation() {
        configureByText("""
                var name = "World"
                var x = "Hello ${name}"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for properly closed interpolation", errors.isEmpty());
    }

    public void testValidSimpleInterpolation() {
        configureByText("""
                var name = "World"
                var x = "Hello $name"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for simple interpolation", errors.isEmpty());
    }

    public void testValidDollarSignNotInterpolation() {
        configureByText("""
                var x = "Price: $100"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for $ followed by number", errors.isEmpty());
    }

    public void testSingleQuoteNoInterpolation() {
        configureByText("""
                var x = 'No ${interp}'
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error - single quotes don't interpolate", errors.isEmpty());
    }

    public void testValidNestedInterpolation() {
        configureByText("""
                var x = "Value: ${a.b.c}"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for nested property access", errors.isEmpty());
    }

    public void testValidArrayIndexInterpolation() {
        configureByText("""
                var x = "Item: ${items[0]}"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for array index access", errors.isEmpty());
    }

    public void testValidMultipleInterpolations() {
        configureByText("""
                var first = "John"
                var last = "Doe"
                var x = "Hello ${first} ${last}"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for multiple interpolations", errors.isEmpty());
    }

    public void testValidFunctionCallInterpolation() {
        configureByText("""
                fun getName() string {
                    return "World"
                }
                var x = "Hello ${getName()}"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for function call interpolation", errors.isEmpty());
    }

    // ========================================
    // Edge cases
    // ========================================

    public void testEmptyInterpolation() {
        configureByText("""
                var x = "Value: ${}"
                """);

        // Empty interpolation might be valid syntactically but semantically wrong
        // The lexer/parser handles this
        myFixture.doHighlighting();
    }

    public void testEscapedDollarSign() {
        configureByText("""
                var x = "Price: \\$100"
                """);

        var errors = getInterpolationErrors();
        assertTrue("Should not error for escaped dollar sign", errors.isEmpty());
    }

    private List<HighlightInfo> getInterpolationErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             (h.getDescription().contains("interpolation") ||
                              h.getDescription().contains("Unclosed")))
                .collect(Collectors.toList());
    }
}
