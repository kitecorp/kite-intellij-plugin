package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for assignment in condition detection (if x = 5 instead of if x == 5).
 */
public class KiteAssignmentInConditionAnnotatorTest extends KiteTestBase {

    public void testAssignmentInIfCondition() {
        configureByText("""
                var x = 5
                if x = 5 {
                    var y = 1
                }
                """);

        var warnings = getAssignmentInConditionWarnings();
        assertFalse("Should report assignment in if condition", warnings.isEmpty());
        assertTrue("Warning should mention '=='",
                warnings.stream().anyMatch(h -> h.getDescription().contains("==")));
    }

    public void testAssignmentInWhileCondition() {
        configureByText("""
                var x = 5
                while x = true {
                    var y = 1
                    break
                }
                """);

        var warnings = getAssignmentInConditionWarnings();
        assertFalse("Should report assignment in while condition", warnings.isEmpty());
    }

    public void testComparisonInIfCondition() {
        configureByText("""
                var x = 5
                if x == 5 {
                    var y = 1
                }
                """);

        var warnings = getAssignmentInConditionWarnings();
        assertTrue("Should not warn for comparison in if", warnings.isEmpty());
    }

    public void testNotEqualsInIfCondition() {
        configureByText("""
                var x = 5
                if x != 5 {
                    var y = 1
                }
                """);

        var warnings = getAssignmentInConditionWarnings();
        assertTrue("Should not warn for != comparison", warnings.isEmpty());
    }

    public void testBooleanCondition() {
        configureByText("""
                var isValid = true
                if isValid {
                    var y = 1
                }
                """);

        var warnings = getAssignmentInConditionWarnings();
        assertTrue("Should not warn for simple boolean condition", warnings.isEmpty());
    }

    public void testCompoundAssignmentInCondition() {
        configureByText("""
                var x = 5
                if x += 1 {
                    var y = 1
                }
                """);

        // Compound assignments like += are also suspicious in conditions
        var warnings = getAssignmentInConditionWarnings();
        assertFalse("Should report compound assignment in condition", warnings.isEmpty());
    }

    private List<HighlightInfo> getAssignmentInConditionWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("assignment in condition"))
                .collect(Collectors.toList());
    }
}
