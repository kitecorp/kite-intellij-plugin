package cloud.kitelang.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface providing highlighting test utilities via default methods.
 * Implement this interface in test classes to gain access to highlighting helper methods.
 */
public interface HighlightingTestSupport {

    /**
     * Returns the test fixture. Must be implemented by the test class.
     */
    CodeInsightTestFixture getFixture();

    // ========== Generic Methods ==========

    /**
     * Get all highlights with a specific severity.
     */
    default List<HighlightInfo> getHighlights(HighlightSeverity severity) {
        return getFixture().doHighlighting().stream()
                .filter(h -> h.getSeverity() == severity)
                .collect(Collectors.toList());
    }

    /**
     * Get the first highlight with a specific severity, or null if none.
     */
    default HighlightInfo getFirstHighlight(HighlightSeverity severity) {
        return getFixture().doHighlighting().stream()
                .filter(h -> h.getSeverity() == severity)
                .findFirst()
                .orElse(null);
    }

    // ========== ERROR Severity ==========

    /**
     * Get all ERROR severity highlights.
     */
    default List<HighlightInfo> getErrors() {
        return getHighlights(HighlightSeverity.ERROR);
    }

    /**
     * Get the first ERROR severity highlight, or null if none.
     */
    default HighlightInfo getFirstError() {
        return getFirstHighlight(HighlightSeverity.ERROR);
    }

    // ========== WARNING Severity ==========

    /**
     * Get all WARNING severity highlights.
     */
    default List<HighlightInfo> getWarnings() {
        return getHighlights(HighlightSeverity.WARNING);
    }

    /**
     * Get the first WARNING severity highlight, or null if none.
     */
    default HighlightInfo getFirstWarning() {
        return getFirstHighlight(HighlightSeverity.WARNING);
    }

    // ========== WEAK_WARNING Severity ==========

    // ========== INFORMATION Severity ==========

    // ========== Formatting Helpers ==========

    /**
     * Format a list of highlights for assertion messages.
     * Returns descriptions in format: "[desc1, desc2, ...]" or "[]" if empty.
     */
    default String formatErrors(List<HighlightInfo> highlights) {
        if (highlights.isEmpty()) {
            return "[]";
        }
        return highlights.stream()
                .map(h -> h.getDescription() != null ? h.getDescription() : "null")
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
