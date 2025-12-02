package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for negated comparison detection (!(x == y) vs x != y).
 */
public class KiteNegatedComparisonAnnotatorTest extends KiteTestBase {

    public void testNegatedEquals() {
        configureByText("""
                var x = 5
                var y = 5
                if !(x == y) {
                    var z = 1
                }
                """);

        var hints = getNegatedComparisonHints();
        assertFalse("Should suggest simplification for !(x == y)", hints.isEmpty());
        assertTrue("Hint should mention !=",
                hints.stream().anyMatch(h -> h.getDescription().contains("!=")));
    }

    public void testNegatedGreaterThan() {
        configureByText("""
                var a = 5
                var b = 10
                if !(a > b) {
                    var z = 1
                }
                """);

        var hints = getNegatedComparisonHints();
        assertFalse("Should suggest simplification for !(a > b)", hints.isEmpty());
        assertTrue("Hint should mention <=",
                hints.stream().anyMatch(h -> h.getDescription().contains("<=")));
    }

    public void testNegatedLessThan() {
        configureByText("""
                var count = 15
                if !(count < 10) {
                    var z = 1
                }
                """);

        var hints = getNegatedComparisonHints();
        assertFalse("Should suggest simplification for !(count < 10)", hints.isEmpty());
        assertTrue("Hint should mention >=",
                hints.stream().anyMatch(h -> h.getDescription().contains(">=")));
    }

    public void testNegatedNotEquals() {
        configureByText("""
                var x = 5
                var y = 10
                if !(x != y) {
                    var z = 1
                }
                """);

        var hints = getNegatedComparisonHints();
        assertFalse("Should suggest simplification for !(x != y)", hints.isEmpty());
        assertTrue("Hint should mention ==",
                hints.stream().anyMatch(h -> h.getDescription().contains("==")));
    }

    public void testAlreadySimplified() {
        configureByText("""
                var x = 5
                var y = 10
                if x != y {
                    var z = 1
                }
                """);

        var hints = getNegatedComparisonHints();
        assertTrue("Should not warn for already simplified comparison", hints.isEmpty());
    }

    public void testSimpleNotBoolean() {
        configureByText("""
                var isValid = true
                if !isValid {
                    var z = 1
                }
                """);

        var hints = getNegatedComparisonHints();
        assertTrue("Should not warn for simple not operator on boolean", hints.isEmpty());
    }

    private List<HighlightInfo> getNegatedComparisonHints() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.INFORMATION ||
                             h.getSeverity() == HighlightSeverity.WEAK_WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("simplified"))
                .collect(Collectors.toList());
    }
}
