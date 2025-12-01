package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for the duplicate declaration annotator.
 * Verifies that duplicate declarations are correctly detected at file and scope level.
 */
public class KiteDuplicateDeclarationAnnotatorTest extends KiteTestBase {

    // ========== File-Level Duplicate Detection Tests ==========

    public void testDuplicateVariableShowsError() {
        configureByText("""
                var x = 1
                var x = 2
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'x' is already declared", errors.get(0).getDescription());
    }

    public void testDuplicateInputShowsError() {
        configureByText("""
                input string name = "a"
                input string name = "b"
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'name' is already declared", errors.get(0).getDescription());
    }

    public void testDuplicateOutputShowsError() {
        configureByText("""
                output string result = "a"
                output string result = "b"
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'result' is already declared", errors.get(0).getDescription());
    }

    public void testDuplicateResourceShowsError() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myDb {
                    host = "a"
                }
                resource Config myDb {
                    host = "b"
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'myDb' is already declared", errors.get(0).getDescription());
    }

    public void testDuplicateSchemaShowsError() {
        configureByText("""
                schema Config {
                    string host
                }
                schema Config {
                    number port
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'Config' is already declared", errors.get(0).getDescription());
    }

    public void testDuplicateFunctionShowsError() {
        configureByText("""
                fun greet() string {
                    return "hello"
                }
                fun greet() string {
                    return "hi"
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'greet' is already declared", errors.get(0).getDescription());
    }

    public void testDuplicateTypeShowsError() {
        configureByText("""
                type Region = "us-east" | "us-west"
                type Region = "eu-west"
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'Region' is already declared", errors.get(0).getDescription());
    }

    public void testDuplicateComponentShowsError() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer {
                    input string port = "3000"
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'WebServer' is already declared", errors.get(0).getDescription());
    }

    // ========== No False Positive Tests ==========

    public void testDifferentNamesNoError() {
        configureByText("""
                var x = 1
                var y = 2
                var z = 3
                """);

        var errors = getDuplicateErrors();
        assertEquals(0, errors.size());
    }

    public void testDifferentDeclarationTypesNoError() {
        configureByText("""
                var myName = "test"
                input string otherName = "input"
                output string resultName = "output"
                """);

        var errors = getDuplicateErrors();
        assertEquals(0, errors.size());
    }

    public void testMixedDeclarationsNoDuplicates() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myConfig {
                    host = "localhost"
                }
                var x = 1
                fun greet() string {
                    return "hello"
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(0, errors.size());
    }

    // ========== Cross-Type Collision Tests ==========

    public void testVariableAndInputSameNameShowsError() {
        configureByText("""
                var name = "var"
                input string name = "input"
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'name' is already declared", errors.get(0).getDescription());
    }

    public void testResourceAndSchemaCanShareName() {
        // Resource instances and schema types can have different names
        // but if they have the same name, it should be flagged
        configureByText("""
                schema Server {
                    string host
                }
                resource Server server {
                    host = "localhost"
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(0, errors.size());
    }

    // ========== Multiple Duplicates Tests ==========

    public void testMultipleDuplicatesEachFlagged() {
        configureByText("""
                var x = 1
                var x = 2
                var y = 3
                var y = 4
                """);

        var errors = getDuplicateErrors();
        assertEquals(2, errors.size());
    }

    public void testTripleDuplicateTwoErrors() {
        configureByText("""
                var x = 1
                var x = 2
                var x = 3
                """);

        var errors = getDuplicateErrors();
        // Should have 2 errors (second and third declarations are duplicates)
        assertEquals(2, errors.size());
    }

    // ========== Nested Scope Tests ==========

    public void testDuplicateInsideComponentShowsError() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    input string port = "3000"
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'port' is already declared", errors.get(0).getDescription());
    }

    public void testSameNameInDifferentComponentsNoError() {
        configureByText("""
                component ServerA {
                    input string port = "8080"
                }
                component ServerB {
                    input string port = "3000"
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(0, errors.size());
    }

    public void testComponentInstancesWithDifferentNamesNoError() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer serverA {
                }
                component WebServer serverB {
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(0, errors.size());
    }

    public void testComponentInstancesSameNameShowsError() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer myServer {
                }
                component WebServer myServer {
                }
                """);

        var errors = getDuplicateErrors();
        assertEquals(1, errors.size());
        assertEquals("'myServer' is already declared", errors.get(0).getDescription());
    }

    // ========== Helper Methods ==========

    private List<HighlightInfo> getDuplicateErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().contains("already declared"))
                .collect(Collectors.toList());
    }
}
