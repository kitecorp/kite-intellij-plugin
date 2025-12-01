package cloud.kitelang.intellij.inspection;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;

/**
 * Base test class for Kite inspection tests.
 * Provides common utilities for testing LocalInspectionTool implementations.
 */
public abstract class KiteInspectionTestBase extends KiteTestBase {

    /**
     * Returns the inspection instance to test.
     * Subclasses must implement this to provide their specific inspection.
     */
    protected abstract LocalInspectionTool getInspection();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(getInspection());
    }

    /**
     * Configures the file and runs highlighting, returning all highlight infos.
     */
    protected List<HighlightInfo> doHighlighting(String text) {
        configureByText(text);
        return myFixture.doHighlighting();
    }

    /**
     * Configures the file and runs highlighting with a specific file name.
     */
    protected List<HighlightInfo> doHighlighting(String fileName, String text) {
        configureByText(fileName, text);
        return myFixture.doHighlighting();
    }

    /**
     * Asserts that the given text produces no warnings or errors from the inspection.
     */
    protected void assertNoProblems(String text) {
        var highlights = doHighlighting(text);
        var problems = highlights.stream()
                .filter(h -> h.getSeverity().compareTo(HighlightSeverity.WARNING) >= 0)
                .toList();
        assertTrue("Expected no problems but found: " + problems, problems.isEmpty());
    }

    /**
     * Asserts that the given text produces at least one warning containing the message.
     */
    protected void assertHasWarning(String text, String expectedMessage) {
        var highlights = doHighlighting(text);
        var hasWarning = highlights.stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains(expectedMessage));
        assertTrue("Expected warning containing '" + expectedMessage + "' but none found. Highlights: " + highlights,
                hasWarning);
    }

    /**
     * Asserts that the given text produces at least one error containing the message.
     */
    protected void assertHasError(String text, String expectedMessage) {
        var highlights = doHighlighting(text);
        var hasError = highlights.stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains(expectedMessage));
        assertTrue("Expected error containing '" + expectedMessage + "' but none found. Highlights: " + highlights,
                hasError);
    }

    /**
     * Asserts that the given text produces at least one weak warning containing the message.
     */
    protected void assertHasWeakWarning(String text, String expectedMessage) {
        var highlights = doHighlighting(text);
        var hasWeakWarning = highlights.stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WEAK_WARNING)
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains(expectedMessage));
        assertTrue("Expected weak warning containing '" + expectedMessage + "' but none found. Highlights: " + highlights,
                hasWeakWarning);
    }

    /**
     * Asserts that the given text produces at least one info-level highlight containing the message.
     * Used for "like unused" style inspections.
     */
    protected void assertHasInfo(String text, String expectedMessage) {
        var highlights = doHighlighting(text);
        var hasInfo = highlights.stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.INFORMATION ||
                             h.getSeverity().toString().contains("UNUSED"))
                .anyMatch(h -> h.getDescription() != null && h.getDescription().contains(expectedMessage));
        assertTrue("Expected info containing '" + expectedMessage + "' but none found. Highlights: " + highlights,
                hasInfo);
    }

    /**
     * Counts problems with a specific severity and message.
     */
    protected long countProblems(String text, HighlightSeverity severity, String message) {
        var highlights = doHighlighting(text);
        return highlights.stream()
                .filter(h -> h.getSeverity() == severity)
                .filter(h -> h.getDescription() != null && h.getDescription().contains(message))
                .count();
    }

    /**
     * Asserts an exact count of problems with a specific message.
     */
    protected void assertProblemCount(String text, String message, int expectedCount) {
        var highlights = doHighlighting(text);
        var count = highlights.stream()
                .filter(h -> h.getDescription() != null && h.getDescription().contains(message))
                .count();
        assertEquals("Expected " + expectedCount + " problems with message '" + message + "'",
                expectedCount, count);
    }

    /**
     * Returns all highlight descriptions for debugging.
     */
    protected List<String> getAllHighlightDescriptions(String text) {
        var highlights = doHighlighting(text);
        return highlights.stream()
                .map(HighlightInfo::getDescription)
                .filter(d -> d != null)
                .toList();
    }
}
