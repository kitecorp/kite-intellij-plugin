package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for duplicate decorator detection on declarations.
 */
public class KiteDuplicateDecoratorAnnotatorTest extends KiteTestBase {

    public void testDuplicateDecoratorOnSchema() {
        configureByText("""
                @description("First")
                @description("Second")
                schema Config { }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertFalse("Should report duplicate decorator", highlights.isEmpty());
        assertTrue("Error should mention '@description'",
                highlights.stream().anyMatch(h -> h.getDescription().contains("'@description'")));
    }

    public void testDuplicateDecoratorOnResource() {
        configureByText("""
                schema Config {
                    string name
                }
                @tags({Env: "prod"})
                @tags({Team: "platform"})
                resource Config server { }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertFalse("Should report duplicate decorator", highlights.isEmpty());
    }

    public void testDuplicateDecoratorOnInput() {
        configureByText("""
                component Server {
                    @minValue(0)
                    @minValue(1)
                    input number port = 8080
                }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertFalse("Should report duplicate @minValue", highlights.isEmpty());
    }

    public void testMultipleDifferentDecorators() {
        configureByText("""
                @description("A server component")
                @tags({Env: "prod"})
                schema Config {
                    string name
                }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertTrue("Different decorators should not conflict", highlights.isEmpty());
    }

    public void testNoDuplicateDecoratorsOnDifferentDeclarations() {
        configureByText("""
                @description("First schema")
                schema ConfigA { }

                @description("Second schema")
                schema ConfigB { }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertTrue("Same decorator on different declarations should not conflict", highlights.isEmpty());
    }

    public void testTripleDuplicateDecorator() {
        configureByText("""
                @description("First")
                @description("Second")
                @description("Third")
                schema Config { }
                """);

        var highlights = getDuplicateDecoratorErrors();
        // Should have 2 errors (second and third)
        assertEquals("Should report 2 duplicate decorators", 2, highlights.size());
    }

    public void testDuplicateDecoratorOnComponent() {
        configureByText("""
                @description("First")
                @description("Second")
                component Server {
                    input string name
                }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertFalse("Should report duplicate decorator on component", highlights.isEmpty());
    }

    public void testDuplicateDecoratorOnOutput() {
        configureByText("""
                component Server {
                    @sensitive
                    @sensitive
                    output string secret = "hidden"
                }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertFalse("Should report duplicate @sensitive on output", highlights.isEmpty());
    }

    public void testDuplicateDecoratorOnFunction() {
        configureByText("""
                @description("Calculate total")
                @description("Another description")
                fun calculate() number {
                    return 42
                }
                """);

        var highlights = getDuplicateDecoratorErrors();
        assertFalse("Should report duplicate decorator on function", highlights.isEmpty());
    }

    private List<HighlightInfo> getDuplicateDecoratorErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null && h.getDescription().contains("Duplicate decorator"))
                .collect(Collectors.toList());
    }
}
