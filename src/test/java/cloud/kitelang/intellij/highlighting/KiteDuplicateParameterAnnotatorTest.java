package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for duplicate parameter detection in function declarations.
 */
public class KiteDuplicateParameterAnnotatorTest extends KiteTestBase {

    public void testDuplicateParameter() {
        configureByText("""
                fun calculate(number x, string x) {
                    return x
                }
                """);

        var highlights = getDuplicateParameterErrors();
        assertFalse("Should report duplicate parameter", highlights.isEmpty());
        assertTrue("Error should mention 'x' is already declared",
                highlights.stream().anyMatch(h -> h.getDescription().contains("'x' is already declared")));
    }

    public void testMultipleDuplicateParameters() {
        configureByText("""
                fun process(string name, number name, boolean name) {
                    return name
                }
                """);

        var highlights = getDuplicateParameterErrors();
        // Should have 2 errors (second and third 'name')
        assertEquals("Should report 2 duplicate parameters", 2, highlights.size());
    }

    public void testNoDuplicateParameters() {
        configureByText("""
                fun calculate(number x, string y, boolean z) number {
                    return x + y
                }
                """);

        var highlights = getDuplicateParameterErrors();
        assertTrue("Should have no duplicate parameter errors", highlights.isEmpty());
    }

    public void testDuplicateParametersAcrossFunctions() {
        configureByText("""
                fun func1(number x) {
                    return x
                }

                fun func2(number x) {
                    return x
                }
                """);

        var highlights = getDuplicateParameterErrors();
        assertTrue("Parameters in different functions should not conflict", highlights.isEmpty());
    }

    public void testSingleParameter() {
        configureByText("""
                fun process(string data) {
                    return data
                }
                """);

        var highlights = getDuplicateParameterErrors();
        assertTrue("Single parameter should not be duplicate", highlights.isEmpty());
    }

    public void testNoParameters() {
        configureByText("""
                fun noParams() {
                    return 42
                }
                """);

        var highlights = getDuplicateParameterErrors();
        assertTrue("Function with no parameters should have no errors", highlights.isEmpty());
    }

    public void testDuplicateWithDifferentTypes() {
        configureByText("""
                fun mixed(number x, string x, boolean x) {
                    return x
                }
                """);

        var highlights = getDuplicateParameterErrors();
        // Second and third 'x' are duplicates
        assertEquals("Should report 2 duplicates", 2, highlights.size());
    }

    public void testAnyTypeParameter() {
        configureByText("""
                fun process(any data, string data) {
                    return data
                }
                """);

        var highlights = getDuplicateParameterErrors();
        assertFalse("Should detect duplicate 'data' parameter", highlights.isEmpty());
        assertTrue("Error should mention 'data'",
                highlights.stream().anyMatch(h -> h.getDescription().contains("'data' is already declared")));
    }

    private List<HighlightInfo> getDuplicateParameterErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("is already declared"))
                .collect(Collectors.toList());
    }
}
