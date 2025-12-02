package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for infinite loop detection (while true without break/return).
 */
public class KiteInfiniteLoopAnnotatorTest extends KiteTestBase {

    public void testWhileTrueNoBreak() {
        configureByText("""
                fun test() {
                    while true {
                        println("forever")
                    }
                }
                """);

        var warnings = getInfiniteLoopWarnings();
        assertFalse("Should warn for while true without break", warnings.isEmpty());
        assertTrue("Warning should mention infinite loop",
                warnings.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("infinite")));
    }

    public void testWhileTrueWithBreak() {
        configureByText("""
                fun test() {
                    while true {
                        if done {
                            break
                        }
                    }
                }
                """);

        var warnings = getInfiniteLoopWarnings();
        assertTrue("Should not warn for while true with break", warnings.isEmpty());
    }

    public void testWhileTrueWithReturn() {
        configureByText("""
                fun test() {
                    while true {
                        if finished {
                            return result
                        }
                    }
                }
                """);

        var warnings = getInfiniteLoopWarnings();
        assertTrue("Should not warn for while true with return", warnings.isEmpty());
    }

    public void testWhileVariable() {
        configureByText("""
                fun test() {
                    var running = true
                    while running {
                        process()
                    }
                }
                """);

        var warnings = getInfiniteLoopWarnings();
        assertTrue("Should not warn for while variable", warnings.isEmpty());
    }

    public void testWhileComparison() {
        configureByText("""
                fun test() {
                    var i = 0
                    while i < 10 {
                        i = i + 1
                    }
                }
                """);

        var warnings = getInfiniteLoopWarnings();
        assertTrue("Should not warn for while comparison", warnings.isEmpty());
    }

    public void testWhileFalse() {
        configureByText("""
                fun test() {
                    while false {
                        println("never")
                    }
                }
                """);

        // while false is a constant condition warning (already implemented)
        // but should NOT be infinite loop warning
        var warnings = getInfiniteLoopWarnings();
        assertTrue("Should not warn for while false as infinite loop", warnings.isEmpty());
    }

    private List<HighlightInfo> getInfiniteLoopWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("infinite"))
                .collect(Collectors.toList());
    }
}
