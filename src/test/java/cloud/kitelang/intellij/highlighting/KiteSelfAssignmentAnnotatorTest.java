package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for self-assignment detection (x = x).
 */
public class KiteSelfAssignmentAnnotatorTest extends KiteTestBase {

    public void testSimpleSelfAssignment() {
        configureByText("""
                var x = 5
                x = x
                """);

        var warnings = getSelfAssignmentWarnings();
        assertFalse("Should report self-assignment", warnings.isEmpty());
        assertTrue("Warning should mention self-assignment",
                warnings.stream().anyMatch(h -> h.getDescription().toLowerCase().contains("self-assignment")));
    }

    public void testNoSelfAssignment() {
        configureByText("""
                var x = 5
                var y = 10
                x = y
                """);

        var warnings = getSelfAssignmentWarnings();
        assertTrue("Should not report anything for normal assignment", warnings.isEmpty());
    }

    public void testSelfAssignmentInFunction() {
        configureByText("""
                fun test() {
                    var count = 0
                    count = count
                }
                """);

        var warnings = getSelfAssignmentWarnings();
        assertFalse("Should report self-assignment in function", warnings.isEmpty());
    }

    public void testMultipleSelfAssignments() {
        configureByText("""
                var a = 1
                var b = 2
                a = a
                b = b
                """);

        var warnings = getSelfAssignmentWarnings();
        assertEquals("Should report 2 self-assignments", 2, warnings.size());
    }

    public void testAssignmentWithComputation() {
        configureByText("""
                var x = 5
                x = x + 1
                """);

        var warnings = getSelfAssignmentWarnings();
        assertTrue("x = x + 1 is not self-assignment", warnings.isEmpty());
    }

    public void testPlusAssignSelf() {
        configureByText("""
                var x = 5
                x += x
                """);

        // += is compound assignment, not a true self-assignment (it's x = x + x)
        // This is valid and useful, so should not warn
        var warnings = getSelfAssignmentWarnings();
        assertTrue("x += x is valid compound assignment", warnings.isEmpty());
    }

    public void testSelfAssignmentInResource() {
        configureByText("""
                schema Config {
                    string name
                }
                var name = "test"
                resource Config cfg {
                    name = name
                }
                """);

        // In resource blocks, this is valid - assigning variable 'name' to property 'name'
        var warnings = getSelfAssignmentWarnings();
        assertTrue("Resource property = variable of same name is valid", warnings.isEmpty());
    }

    public void testDifferentIdentifiersSameName() {
        configureByText("""
                fun test(string x) {
                    var x = x
                }
                """);

        // This is actually shadowing and assigning parameter to local var
        // The pattern is var x = x where x on right is the parameter
        // This is valid, so should not warn
        var warnings = getSelfAssignmentWarnings();
        assertTrue("var x = x (shadowing parameter) is valid", warnings.isEmpty());
    }

    private List<HighlightInfo> getSelfAssignmentWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().toLowerCase().contains("self-assignment"))
                .collect(Collectors.toList());
    }
}
