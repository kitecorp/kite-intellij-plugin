package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for redundant condition detection (x && x, x || x).
 */
public class KiteRedundantConditionAnnotatorTest extends KiteTestBase {

    public void testRedundantAndCondition() {
        configureByText("""
                var x = true
                if x && x {
                    var y = 1
                }
                """);

        var warnings = getRedundantConditionWarnings();
        assertFalse("Should report redundant && condition", warnings.isEmpty());
        assertTrue("Warning should mention simplification",
                warnings.stream().anyMatch(h -> h.getDescription().contains("x")));
    }

    public void testRedundantOrCondition() {
        configureByText("""
                var enabled = true
                if enabled || enabled {
                    var y = 1
                }
                """);

        var warnings = getRedundantConditionWarnings();
        assertFalse("Should report redundant || condition", warnings.isEmpty());
    }

    public void testDifferentOperandsAnd() {
        configureByText("""
                var a = true
                var b = false
                if a && b {
                    var y = 1
                }
                """);

        var warnings = getRedundantConditionWarnings();
        assertTrue("Should not warn for different operands in &&", warnings.isEmpty());
    }

    public void testDifferentOperandsOr() {
        configureByText("""
                var a = true
                var b = false
                if a || b {
                    var y = 1
                }
                """);

        var warnings = getRedundantConditionWarnings();
        assertTrue("Should not warn for different operands in ||", warnings.isEmpty());
    }

    public void testRedundantConditionInWhile() {
        configureByText("""
                var flag = true
                while flag && flag {
                    break
                }
                """);

        var warnings = getRedundantConditionWarnings();
        assertFalse("Should report redundant condition in while", warnings.isEmpty());
    }

    public void testRedundantConditionInAssignment() {
        configureByText("""
                var x = true
                var result = x && x
                """);

        var warnings = getRedundantConditionWarnings();
        assertFalse("Should report redundant condition in assignment", warnings.isEmpty());
    }

    public void testMixedOperators() {
        configureByText("""
                var a = true
                var b = false
                if a && b || a {
                    var y = 1
                }
                """);

        var warnings = getRedundantConditionWarnings();
        assertTrue("Should not warn for mixed operator expressions", warnings.isEmpty());
    }

    private List<HighlightInfo> getRedundantConditionWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("redundant condition"))
                .collect(Collectors.toList());
    }
}
