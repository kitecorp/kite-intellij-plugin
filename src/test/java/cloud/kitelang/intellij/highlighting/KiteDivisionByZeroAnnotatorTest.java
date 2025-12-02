package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for division by zero detection (x / 0, x % 0).
 */
public class KiteDivisionByZeroAnnotatorTest extends KiteTestBase {

    public void testDivisionByZero() {
        configureByText("""
                var x = 10 / 0
                """);

        var warnings = getDivisionByZeroWarnings();
        assertFalse("Should report division by zero", warnings.isEmpty());
        assertTrue("Warning should mention division by zero",
                warnings.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("division by zero")));
    }

    public void testModuloByZero() {
        configureByText("""
                var x = 10 % 0
                """);

        var warnings = getDivisionByZeroWarnings();
        assertFalse("Should report modulo by zero", warnings.isEmpty());
        assertTrue("Warning should mention modulo by zero",
                warnings.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("modulo by zero")));
    }

    public void testDivisionByZeroFloat() {
        configureByText("""
                var x = 10 / 0.0
                """);

        var warnings = getDivisionByZeroWarnings();
        assertFalse("Should report division by 0.0", warnings.isEmpty());
    }

    public void testNormalDivision() {
        configureByText("""
                var x = 10 / 2
                """);

        var warnings = getDivisionByZeroWarnings();
        assertTrue("Should not warn for normal division", warnings.isEmpty());
    }

    public void testDivisionByVariable() {
        configureByText("""
                var n = 5
                var x = 10 / n
                """);

        var warnings = getDivisionByZeroWarnings();
        assertTrue("Should not warn when dividing by variable", warnings.isEmpty());
    }

    public void testDivisionByZeroInExpression() {
        configureByText("""
                var x = (10 + 5) / 0
                """);

        var warnings = getDivisionByZeroWarnings();
        assertFalse("Should report division by zero in expression", warnings.isEmpty());
    }

    public void testMultipleDivisionsByZero() {
        configureByText("""
                var a = 5 / 0
                var b = 10 % 0
                """);

        var warnings = getDivisionByZeroWarnings();
        assertEquals("Should report 2 warnings", 2, warnings.size());
    }

    public void testDivisionByNegativeZero() {
        configureByText("""
                var x = 10 / -0
                """);

        // -0 is still zero
        var warnings = getDivisionByZeroWarnings();
        assertFalse("Should report division by -0", warnings.isEmpty());
    }

    public void testZeroDividedByZero() {
        configureByText("""
                var x = 0 / 0
                """);

        var warnings = getDivisionByZeroWarnings();
        assertFalse("Should report 0 / 0", warnings.isEmpty());
    }

    private List<HighlightInfo> getDivisionByZeroWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             (h.getDescription().toLowerCase().contains("division by zero") ||
                              h.getDescription().toLowerCase().contains("modulo by zero")))
                .collect(Collectors.toList());
    }
}
