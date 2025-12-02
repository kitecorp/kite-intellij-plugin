package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for redundant boolean comparison detection (x == true, x != false, etc.).
 */
public class KiteRedundantBooleanAnnotatorTest extends KiteTestBase {

    public void testEqualsTrue() {
        configureByText("""
                var isValid = true
                if isValid == true {
                    var x = 1
                }
                """);

        var warnings = getRedundantBooleanWarnings();
        assertFalse("Should report 'isValid == true'", warnings.isEmpty());
        assertTrue("Warning should mention simplified",
                warnings.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("simplified")));
    }

    public void testEqualsFalse() {
        configureByText("""
                var isValid = true
                if isValid == false {
                    var x = 1
                }
                """);

        var warnings = getRedundantBooleanWarnings();
        assertFalse("Should report 'isValid == false'", warnings.isEmpty());
    }

    public void testNotEqualsTrue() {
        configureByText("""
                var isValid = true
                if isValid != true {
                    var x = 1
                }
                """);

        var warnings = getRedundantBooleanWarnings();
        assertFalse("Should report 'isValid != true'", warnings.isEmpty());
    }

    public void testNotEqualsFalse() {
        configureByText("""
                var isValid = true
                if isValid != false {
                    var x = 1
                }
                """);

        var warnings = getRedundantBooleanWarnings();
        assertFalse("Should report 'isValid != false'", warnings.isEmpty());
    }

    public void testTrueEqualsVariable() {
        configureByText("""
                var isValid = true
                if true == isValid {
                    var x = 1
                }
                """);

        var warnings = getRedundantBooleanWarnings();
        assertFalse("Should report 'true == isValid'", warnings.isEmpty());
    }

    public void testNormalComparison() {
        configureByText("""
                var a = 5
                var b = 10
                if a == b {
                    var x = 1
                }
                """);

        var warnings = getRedundantBooleanWarnings();
        assertTrue("Should not warn for normal comparison", warnings.isEmpty());
    }

    public void testSimpleBooleanCondition() {
        configureByText("""
                var isValid = true
                if isValid {
                    var x = 1
                }
                """);

        var warnings = getRedundantBooleanWarnings();
        assertTrue("Should not warn for simple boolean condition", warnings.isEmpty());
    }

    private List<HighlightInfo> getRedundantBooleanWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("simplified"))
                .collect(Collectors.toList());
    }
}
