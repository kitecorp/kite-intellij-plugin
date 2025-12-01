package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.List;

/**
 * Tests for KiteReferenceContributor - verifies reference provider registration.
 * Uses highlighting to verify reference resolution works correctly.
 */
public class KiteReferenceContributorTest extends KiteTestBase {

    // ========== Declaration Name Exclusion Tests ==========

    public void testDeclarationNameNotFlagged() {
        configureByText("""
                var myVar = "hello"
                """);

        // Declaration names should not cause errors
        List<HighlightInfo> errors = getErrors();
        assertTrue("Declaration should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testInputDeclarationNameNotFlagged() {
        configureByText("""
                input string portName = "8080"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Input declaration should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testOutputDeclarationNameNotFlagged() {
        configureByText("""
                output string result = "value"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Output declaration should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testResourceDeclarationNameNotFlagged() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myDb {
                    host = "localhost"
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Resource declaration should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testComponentDeclarationNameNotFlagged() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Component declaration should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testSchemaDeclarationNameNotFlagged() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Schema declaration should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testFunctionDeclarationNameNotFlagged() {
        configureByText("""
                fun greet() string {
                    return "hello"
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Function declaration should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Reference Value Tests ==========

    public void testValueAfterEqualsResolves() {
        configureByText("""
                var source = "value"
                var target = source
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Value reference should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testPropertyNameNotFlaggedAsUnresolved() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config db {
                    host = "localhost"
                }
                """);

        // Property names in resource blocks should not be flagged
        List<HighlightInfo> errors = getErrors();
        assertTrue("Property name should not be flagged: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testSchemaPropertyNameNotFlagged() {
        configureByText("""
                schema Config {
                    string hostName
                    number portNumber
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Schema property names should not be flagged: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testArrayTypeSchemaPropertyNameNotFlagged() {
        configureByText("""
                schema Config {
                    string[] tags
                    number[] ports
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Array type property names should not be flagged: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testAnyTypeSchemaPropertyNameNotFlagged() {
        configureByText("""
                schema Config {
                    any data
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Any type property names should not be flagged: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== For Loop Variable Tests ==========

    public void testForLoopVariableResolves() {
        configureByText("""
                var items = [1, 2, 3]
                for item in items {
                    var x = item
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("For loop variable should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testForLoopIterableResolves() {
        configureByText("""
                var items = [1, 2, 3]
                for item in items {
                    var x = item
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Iterable reference should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== String Interpolation Tests ==========

    public void testSimpleInterpolationResolves() {
        configureByText("""
                var name = "World"
                var greeting = "Hello $name!"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Simple interpolation should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testBraceInterpolationResolves() {
        configureByText("""
                var port = 8080
                var url = "http://localhost:${port}/api"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Brace interpolation should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Import Symbol Tests ==========

    public void testImportSymbolResolves() {
        addFile("common.kite", """
                var sharedVar = "shared"
                """);

        configureByText("""
                import sharedVar from "common.kite"
                var x = sharedVar
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Import symbol should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Keyword Tests ==========

    public void testKeywordNotFlagged() {
        configureByText("""
                var x = true
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Keywords should not be flagged: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testTypeKeywordNotFlagged() {
        configureByText("""
                input string name = "test"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Type keywords should not be flagged: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Function Parameter Tests ==========

    public void testFunctionParameterUsageResolves() {
        configureByText("""
                fun greet(string userName) string {
                    return "Hello " + userName
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Function parameter usage should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Object Literal Property Tests ==========

    public void testObjectLiteralPropertyNameNotFlagged() {
        configureByText("""
                var config = {
                    host: "localhost",
                    port: 8080
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Object literal property names should not be flagged: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testObjectLiteralPropertyValueResolves() {
        configureByText("""
                var myHost = "localhost"
                var config = {
                    host: myHost
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Object literal property value should resolve: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        configureByText("");

        // Should not crash
        myFixture.doHighlighting();
    }

    public void testFileWithOnlyComments() {
        configureByText("""
                // This is a comment
                """);

        // Should not crash
        myFixture.doHighlighting();
    }
}
