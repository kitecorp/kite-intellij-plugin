package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for self-comparison detection (x == x, x != x).
 */
public class KiteSelfComparisonAnnotatorTest extends KiteTestBase {

    public void testSelfEqualityComparison() {
        configureByText("""
                var x = 5
                var result = x == x
                """);

        var warnings = getSelfComparisonWarnings();
        assertFalse("Should report self-comparison", warnings.isEmpty());
        assertTrue("Warning should mention comparison",
                warnings.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("comparison")));
    }

    public void testSelfInequalityComparison() {
        configureByText("""
                var x = 5
                var result = x != x
                """);

        var warnings = getSelfComparisonWarnings();
        assertFalse("Should report self-comparison for !=", warnings.isEmpty());
    }

    public void testNoSelfComparison() {
        configureByText("""
                var x = 5
                var y = 10
                var result = x == y
                """);

        var warnings = getSelfComparisonWarnings();
        assertTrue("Should not report anything for normal comparison", warnings.isEmpty());
    }

    public void testSelfComparisonLessThan() {
        configureByText("""
                var x = 5
                var result = x < x
                """);

        var warnings = getSelfComparisonWarnings();
        assertFalse("Should report self-comparison for <", warnings.isEmpty());
    }

    public void testSelfComparisonGreaterThan() {
        configureByText("""
                var x = 5
                var result = x > x
                """);

        var warnings = getSelfComparisonWarnings();
        assertFalse("Should report self-comparison for >", warnings.isEmpty());
    }

    public void testSelfComparisonLessOrEqual() {
        configureByText("""
                var x = 5
                var result = x <= x
                """);

        // x <= x is always true, could be suspicious but less problematic
        // For now we'll warn on all self-comparisons
        var warnings = getSelfComparisonWarnings();
        assertFalse("Should report self-comparison for <=", warnings.isEmpty());
    }

    public void testSelfComparisonGreaterOrEqual() {
        configureByText("""
                var x = 5
                var result = x >= x
                """);

        var warnings = getSelfComparisonWarnings();
        assertFalse("Should report self-comparison for >=", warnings.isEmpty());
    }

    public void testComparisonWithExpression() {
        configureByText("""
                var x = 5
                var result = x == x + 1
                """);

        // x == x + 1 is not self-comparison (right side is different)
        var warnings = getSelfComparisonWarnings();
        assertTrue("x == x + 1 is not self-comparison", warnings.isEmpty());
    }

    private List<HighlightInfo> getSelfComparisonWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("comparison"))
                .collect(Collectors.toList());
    }
}
