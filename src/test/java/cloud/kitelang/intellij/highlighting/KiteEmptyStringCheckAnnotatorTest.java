package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for empty string check detection (str == "" vs len(str) == 0).
 */
public class KiteEmptyStringCheckAnnotatorTest extends KiteTestBase {

    public void testEmptyStringEqualsIdentifier() {
        configureByText("""
                var name = "test"
                if name == "" {
                    var x = 1
                }
                """);

        var hints = getEmptyStringCheckHints();
        assertFalse("Should suggest len() for name == \"\"", hints.isEmpty());
        assertTrue("Hint should mention len()",
                hints.stream().anyMatch(h -> h.getDescription().contains("len")));
    }

    public void testIdentifierEqualsEmptyString() {
        configureByText("""
                var value = "test"
                if "" == value {
                    var x = 1
                }
                """);

        var hints = getEmptyStringCheckHints();
        assertFalse("Should suggest len() for \"\" == value", hints.isEmpty());
    }

    public void testEmptyStringNotEquals() {
        configureByText("""
                var name = "test"
                if name != "" {
                    var x = 1
                }
                """);

        var hints = getEmptyStringCheckHints();
        assertFalse("Should suggest len() for name != \"\"", hints.isEmpty());
    }

    public void testNonEmptyStringComparison() {
        configureByText("""
                var name = "test"
                if name == "hello" {
                    var x = 1
                }
                """);

        var hints = getEmptyStringCheckHints();
        assertTrue("Should not warn for non-empty string comparison", hints.isEmpty());
    }

    public void testNumberComparison() {
        configureByText("""
                var count = 0
                if count == 0 {
                    var x = 1
                }
                """);

        var hints = getEmptyStringCheckHints();
        assertTrue("Should not warn for number comparison", hints.isEmpty());
    }

    private List<HighlightInfo> getEmptyStringCheckHints() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.INFORMATION ||
                             h.getSeverity() == HighlightSeverity.WEAK_WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("len"))
                .collect(Collectors.toList());
    }
}
