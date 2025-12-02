package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for type coercion detection (comparing values of different types).
 */
public class KiteTypeCoercionAnnotatorTest extends KiteTestBase {

    public void testNumberStringComparison() {
        configureByText("""
                var x = 5 == "5"
                """);

        var warnings = getTypeCoercionWarnings();
        assertFalse("Should warn for number == string", warnings.isEmpty());
        assertTrue("Warning should mention types",
                warnings.stream().anyMatch(h -> h.getDescription().contains("number") &&
                                               h.getDescription().contains("string")));
    }

    public void testBooleanNumberComparison() {
        configureByText("""
                var x = true == 1
                """);

        var warnings = getTypeCoercionWarnings();
        assertFalse("Should warn for boolean == number", warnings.isEmpty());
    }

    public void testNullNumberComparison() {
        configureByText("""
                var x = null == 0
                """);

        var warnings = getTypeCoercionWarnings();
        assertFalse("Should warn for null == number", warnings.isEmpty());
    }

    public void testNullStringComparison() {
        configureByText("""
                var x = null == "hello"
                """);

        var warnings = getTypeCoercionWarnings();
        assertFalse("Should warn for null == string", warnings.isEmpty());
    }

    public void testBooleanStringComparison() {
        configureByText("""
                var x = true == "true"
                """);

        var warnings = getTypeCoercionWarnings();
        assertFalse("Should warn for boolean == string", warnings.isEmpty());
    }

    public void testSameTypeNumbers() {
        configureByText("""
                var x = 5 == 10
                """);

        var warnings = getTypeCoercionWarnings();
        assertTrue("Should not warn for number == number", warnings.isEmpty());
    }

    public void testSameTypeStrings() {
        configureByText("""
                var x = "hello" == "world"
                """);

        var warnings = getTypeCoercionWarnings();
        assertTrue("Should not warn for string == string", warnings.isEmpty());
    }

    public void testSameTypeBooleans() {
        configureByText("""
                var x = true == false
                """);

        var warnings = getTypeCoercionWarnings();
        assertTrue("Should not warn for boolean == boolean", warnings.isEmpty());
    }

    public void testNotEqualsTypeCoercion() {
        configureByText("""
                var x = 5 != "5"
                """);

        var warnings = getTypeCoercionWarnings();
        assertFalse("Should warn for number != string", warnings.isEmpty());
    }

    private List<HighlightInfo> getTypeCoercionWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("comparing"))
                .collect(Collectors.toList());
    }
}
