package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for unused function detection.
 */
public class KiteUnusedFunctionAnnotatorTest extends KiteTestBase {

    public void testUnusedFunction() {
        configureByText("""
                fun helper() number {
                    return 42
                }
                """);

        var warnings = getUnusedFunctionWarnings();
        assertFalse("Should warn for unused function", warnings.isEmpty());
        assertTrue("Warning should mention 'helper'",
                warnings.stream().anyMatch(h -> h.getDescription().contains("helper")));
    }

    public void testUsedFunction() {
        configureByText("""
                fun calculate() number {
                    return 1
                }

                var result = calculate()
                """);

        var warnings = getUnusedFunctionWarnings();
        assertTrue("Should not warn for used function", warnings.isEmpty());
    }

    public void testRecursiveFunction() {
        configureByText("""
                fun recursive(number n) number {
                    if n == 0 {
                        return 0
                    }
                    return recursive(n - 1)
                }
                """);

        // Recursive function calls itself - should still be considered unused
        // unless called from elsewhere
        var warnings = getUnusedFunctionWarnings();
        assertFalse("Should warn for only-recursive function", warnings.isEmpty());
    }

    public void testRecursiveFunctionCalledExternally() {
        configureByText("""
                fun recursive(number n) number {
                    if n == 0 {
                        return 0
                    }
                    return recursive(n - 1)
                }

                var result = recursive(5)
                """);

        var warnings = getUnusedFunctionWarnings();
        assertTrue("Should not warn for recursive function called externally", warnings.isEmpty());
    }

    public void testMultipleFunctions() {
        configureByText("""
                fun used() {
                    println("used")
                }

                fun unused() {
                    println("unused")
                }

                used()
                """);

        var warnings = getUnusedFunctionWarnings();
        assertEquals("Should have exactly 1 warning", 1, warnings.size());
        assertTrue("Warning should mention 'unused'",
                warnings.get(0).getDescription().contains("unused"));
    }

    private List<HighlightInfo> getUnusedFunctionWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("never") &&
                             h.getDescription().toLowerCase().contains("called"))
                .collect(Collectors.toList());
    }
}
