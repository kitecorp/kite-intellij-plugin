package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for useless expression detection (statements with no side effects).
 */
public class KiteUselessExpressionAnnotatorTest extends KiteTestBase {

    public void testAdditionWithNoEffect() {
        configureByText("""
                fun test() {
                    var x = 5
                    x + 1
                }
                """);

        var warnings = getUselessExpressionWarnings();
        assertFalse("Should warn for x + 1 with no effect", warnings.isEmpty());
        assertTrue("Warning should mention no effect",
                warnings.stream().anyMatch(h -> h.getDescription().contains("no effect")));
    }

    public void testSubtractionWithNoEffect() {
        configureByText("""
                fun test() {
                    var a = 1
                    var b = 2
                    a - b
                }
                """);

        var warnings = getUselessExpressionWarnings();
        assertFalse("Should warn for a - b with no effect", warnings.isEmpty());
    }

    public void testMultiplicationWithNoEffect() {
        configureByText("""
                fun test() {
                    var c = 3
                    var d = 4
                    c * d
                }
                """);

        var warnings = getUselessExpressionWarnings();
        assertFalse("Should warn for c * d with no effect", warnings.isEmpty());
    }

    public void testAssignedExpression() {
        configureByText("""
                fun test() {
                    var x = 5
                    var result = x + 1
                }
                """);

        var warnings = getUselessExpressionWarnings();
        assertTrue("Should not warn when assigned to variable", warnings.isEmpty());
    }

    public void testCompoundAssignment() {
        configureByText("""
                fun test() {
                    var x = 5
                    x += 1
                }
                """);

        var warnings = getUselessExpressionWarnings();
        assertTrue("Should not warn for compound assignment", warnings.isEmpty());
    }

    public void testReturnedExpression() {
        configureByText("""
                fun test() number {
                    var x = 5
                    return x + 1
                }
                """);

        var warnings = getUselessExpressionWarnings();
        assertTrue("Should not warn when returned", warnings.isEmpty());
    }

    public void testDivisionWithNoEffect() {
        configureByText("""
                fun test() {
                    var e = 10
                    var f = 2
                    e / f
                }
                """);

        var warnings = getUselessExpressionWarnings();
        assertFalse("Should warn for e / f with no effect", warnings.isEmpty());
    }

    private List<HighlightInfo> getUselessExpressionWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("no effect"))
                .collect(Collectors.toList());
    }
}
