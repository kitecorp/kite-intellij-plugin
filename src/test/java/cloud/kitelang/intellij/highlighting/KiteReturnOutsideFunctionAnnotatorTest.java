package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for detecting return statements outside of functions.
 */
public class KiteReturnOutsideFunctionAnnotatorTest extends KiteTestBase {

    public void testReturnAtTopLevel() {
        configureByText("""
                return 42
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertFalse("Should report return outside function", highlights.isEmpty());
    }

    public void testReturnInFunction() {
        configureByText("""
                fun calculate() number {
                    return 42
                }
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertTrue("Return inside function should be valid", highlights.isEmpty());
    }

    public void testReturnInSchema() {
        configureByText("""
                schema Config {
                    return 42
                }
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertFalse("Return in schema should report error", highlights.isEmpty());
    }

    public void testReturnInComponent() {
        configureByText("""
                component Server {
                    return "error"
                }
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertFalse("Return in component should report error", highlights.isEmpty());
    }

    public void testReturnInResource() {
        configureByText("""
                schema Config {
                    string name
                }
                resource Config server {
                    return "error"
                }
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertFalse("Return in resource should report error", highlights.isEmpty());
    }

    public void testReturnAfterVariableDeclaration() {
        configureByText("""
                var x = 10
                return x
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertFalse("Return at top level should report error", highlights.isEmpty());
    }

    public void testReturnInNestedFunction() {
        configureByText("""
                fun outer() number {
                    return 42
                }
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertTrue("Return inside function should be valid", highlights.isEmpty());
    }

    public void testMultipleReturnsOutside() {
        configureByText("""
                return 1
                return 2
                """);

        var highlights = getReturnOutsideFunctionErrors();
        assertEquals("Should report 2 errors", 2, highlights.size());
    }

    private List<HighlightInfo> getReturnOutsideFunctionErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("outside of function"))
                .collect(Collectors.toList());
    }
}
