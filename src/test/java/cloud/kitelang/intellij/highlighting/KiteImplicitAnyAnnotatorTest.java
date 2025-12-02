package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for implicit any detection (variable type cannot be inferred).
 */
public class KiteImplicitAnyAnnotatorTest extends KiteTestBase {

    public void testAssignmentFromIdentifier() {
        configureByText("""
                var original = "test"
                var copy = original
                """);

        var hints = getImplicitAnyHints();
        assertFalse("Should hint for var copy = original", hints.isEmpty());
        assertTrue("Hint should mention implicit any",
                hints.stream().anyMatch(h -> h.getDescription().contains("any")));
    }

    public void testStringLiteral() {
        configureByText("""
                var name = "hello"
                """);

        var hints = getImplicitAnyHints();
        assertTrue("Should not hint for string literal", hints.isEmpty());
    }

    public void testNumberLiteral() {
        configureByText("""
                var count = 42
                """);

        var hints = getImplicitAnyHints();
        assertTrue("Should not hint for number literal", hints.isEmpty());
    }

    public void testBooleanLiteral() {
        configureByText("""
                var flag = true
                """);

        var hints = getImplicitAnyHints();
        assertTrue("Should not hint for boolean literal", hints.isEmpty());
    }

    public void testExplicitTypeAnnotation() {
        configureByText("""
                var original = "test"
                var string name = original
                """);

        var hints = getImplicitAnyHints();
        assertTrue("Should not hint for explicit type annotation", hints.isEmpty());
    }

    public void testNullLiteral() {
        configureByText("""
                var value = null
                """);

        // null is a known type
        var hints = getImplicitAnyHints();
        assertTrue("Should not hint for null literal", hints.isEmpty());
    }

    private List<HighlightInfo> getImplicitAnyHints() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.INFORMATION ||
                             h.getSeverity() == HighlightSeverity.WEAK_WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("implicit") &&
                             h.getDescription().toLowerCase().contains("any"))
                .collect(Collectors.toList());
    }
}
