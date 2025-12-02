package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for too many parameters detection (more than 5 parameters in a function).
 */
public class KiteTooManyParametersAnnotatorTest extends KiteTestBase {

    public void testTooManyParameters() {
        configureByText("""
                fun process(number a, number b, number c, number d, number e, number f) {
                    return a
                }
                """);

        var warnings = getTooManyParametersWarnings();
        assertFalse("Should report too many parameters (6)", warnings.isEmpty());
        assertTrue("Warning should mention parameters",
                warnings.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("parameter")));
    }

    public void testFiveParametersOk() {
        configureByText("""
                fun process(number a, number b, number c, number d, number e) {
                    return a
                }
                """);

        var warnings = getTooManyParametersWarnings();
        assertTrue("5 parameters should be OK", warnings.isEmpty());
    }

    public void testThreeParametersOk() {
        configureByText("""
                fun calculate(number x, number y, number z) number {
                    return x + y + z
                }
                """);

        var warnings = getTooManyParametersWarnings();
        assertTrue("3 parameters should be OK", warnings.isEmpty());
    }

    public void testNoParametersOk() {
        configureByText("""
                fun noParams() {
                    return 0
                }
                """);

        var warnings = getTooManyParametersWarnings();
        assertTrue("0 parameters should be OK", warnings.isEmpty());
    }

    public void testSevenParameters() {
        configureByText("""
                fun manyParams(string a, string b, string c, string d, string e, string f, string g) {
                    return a
                }
                """);

        var warnings = getTooManyParametersWarnings();
        assertFalse("Should report too many parameters (7)", warnings.isEmpty());
    }

    public void testMultipleFunctionsSomeWithTooMany() {
        configureByText("""
                fun ok(number a, number b) {
                    return a
                }
                fun tooMany(number a, number b, number c, number d, number e, number f) {
                    return a
                }
                """);

        var warnings = getTooManyParametersWarnings();
        assertEquals("Should report 1 function with too many parameters", 1, warnings.size());
    }

    private List<HighlightInfo> getTooManyParametersWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("parameter"))
                .collect(Collectors.toList());
    }
}
