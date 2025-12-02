package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for missing return statement detection in functions with return types.
 */
public class KiteMissingReturnAnnotatorTest extends KiteTestBase {

    public void testMissingReturn() {
        configureByText("""
                fun calculate(number x) number {
                    var y = x * 2
                }
                """);

        var highlights = getMissingReturnErrors();
        assertFalse("Should report missing return", highlights.isEmpty());
        assertTrue("Error should mention function 'calculate'",
                highlights.stream().anyMatch(h -> h.getDescription().contains("'calculate'")));
    }

    public void testFunctionWithReturn() {
        configureByText("""
                fun calculate(number x) number {
                    return x * 2
                }
                """);

        var highlights = getMissingReturnErrors();
        assertTrue("Function with return should have no error", highlights.isEmpty());
    }

    public void testFunctionNoReturnType() {
        configureByText("""
                fun process() {
                    println("hello")
                }
                """);

        var highlights = getMissingReturnErrors();
        assertTrue("Function without return type should have no error", highlights.isEmpty());
    }

    public void testFunctionVoidReturnType() {
        configureByText("""
                fun process() void {
                    println("hello")
                }
                """);

        var highlights = getMissingReturnErrors();
        assertTrue("Function with void return type should have no error", highlights.isEmpty());
    }

    public void testFunctionWithConditionalReturn() {
        configureByText("""
                fun check(boolean flag) number {
                    if flag {
                        return 1
                    }
                }
                """);

        // This is tricky - technically the function might not return if flag is false
        // For now, we'll consider any return statement as sufficient
        var highlights = getMissingReturnErrors();
        assertTrue("Function with conditional return should be ok for now", highlights.isEmpty());
    }

    public void testEmptyFunctionWithReturnType() {
        configureByText("""
                fun empty() string {
                }
                """);

        var highlights = getMissingReturnErrors();
        assertFalse("Empty function with return type should report error", highlights.isEmpty());
    }

    public void testFunctionWithStringReturnType() {
        configureByText("""
                fun getName() string {
                    var name = "test"
                }
                """);

        var highlights = getMissingReturnErrors();
        assertFalse("Function missing return should report error", highlights.isEmpty());
    }

    public void testFunctionWithBooleanReturnType() {
        configureByText("""
                fun isValid() boolean {
                    return true
                }
                """);

        var highlights = getMissingReturnErrors();
        assertTrue("Function with return should have no error", highlights.isEmpty());
    }

    public void testMultipleFunctions() {
        configureByText("""
                fun withReturn() number {
                    return 42
                }

                fun withoutReturn() number {
                    var x = 10
                }

                fun noReturnType() {
                    println("ok")
                }
                """);

        var highlights = getMissingReturnErrors();
        assertEquals("Only withoutReturn should have error", 1, highlights.size());
    }

    private List<HighlightInfo> getMissingReturnErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("no return statement"))
                .collect(Collectors.toList());
    }
}
