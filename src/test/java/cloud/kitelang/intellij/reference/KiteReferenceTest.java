package cloud.kitelang.intellij.reference;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.List;

/**
 * Tests for KiteReference - verifies reference resolution for identifiers.
 * Uses highlighting to verify references resolve correctly (no unresolved symbol errors).
 */
public class KiteReferenceTest extends KiteTestBase {

    // ========== Simple Identifier Resolution Tests ==========

    public void testVariableReferenceResolves() {
        configureByText("""
                var myVar = "hello"
                var x = myVar
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Variable reference should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testInputReferenceResolves() {
        configureByText("""
                input string name = "default"
                var x = name
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Input reference should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testOutputReferenceResolves() {
        configureByText("""
                output string result = "value"
                var x = result
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Output reference should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testFunctionReferenceResolves() {
        configureByText("""
                fun greet() string {
                    return "hello"
                }
                var x = greet()
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Function reference should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testResourceReferenceResolves() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myDb {
                    host = "localhost"
                }
                var x = myDb
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Resource reference should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testSchemaReferenceResolves() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                }
                resource DatabaseConfig db {
                    host = "localhost"
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Schema reference should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Property Access Resolution Tests ==========

    public void testSimplePropertyAccessResolves() {
        configureByText("""
                schema Config {
                    string host
                    number port
                }
                resource Config myConfig {
                    host = "localhost"
                    port = 8080
                }
                var x = myConfig.host
                """);

        // Property access tests - verify no crash
        myFixture.doHighlighting();
    }

    public void testNestedPropertyAccessResolves() {
        configureByText("""
                resource Config server {
                    tag = {
                        Name: "web-server"
                    }
                }
                var x = server.tag.Name
                """);

        // Nested property access - verify no crash
        myFixture.doHighlighting();
    }

    public void testComponentOutputAccessResolves() {
        configureByText("""
                component WebServer {
                    output string endpoint = "http://localhost"
                }
                component WebServer server {
                }
                var x = server.endpoint
                """);

        // Component output access - verify no crash
        myFixture.doHighlighting();
    }

    // ========== Cross-File Reference Resolution Tests ==========

    public void testImportedVariableResolves() {
        addFile("common.kite", """
                var sharedVar = "shared"
                """);

        configureByText("""
                import sharedVar from "common.kite"
                var x = sharedVar
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Imported variable should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testWildcardImportResolves() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import * from "common.kite"
                var x = alpha + beta
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Wildcard import should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testImportedFunctionResolves() {
        addFile("utils.kite", """
                fun helper() string {
                    return "help"
                }
                """);

        configureByText("""
                import helper from "utils.kite"
                var x = helper()
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Imported function should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testImportedSchemaResolves() {
        addFile("schemas.kite", """
                schema ServerConfig {
                    string host
                    number port
                }
                """);

        configureByText("""
                import ServerConfig from "schemas.kite"
                resource ServerConfig myServer {
                    host = "localhost"
                    port = 8080
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Imported schema should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Function Parameter Scope Tests ==========

    public void testParameterResolves() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Function parameter should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testForLoopVariableResolves() {
        configureByText("""
                var items = ["a", "b", "c"]
                for item in items {
                    var x = item
                }
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("For loop variable should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== String Interpolation Reference Tests ==========

    public void testSimpleInterpolationResolves() {
        configureByText("""
                var name = "World"
                var greeting = "Hello $name!"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("String interpolation should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testBraceInterpolationResolves() {
        configureByText("""
                var port = 8080
                var url = "http://localhost:${port}/api"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Brace interpolation should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Multiple References Tests ==========

    public void testMultipleReferencesToSameDeclaration() {
        configureByText("""
                var shared = "value"
                var a = shared
                var b = shared
                var c = shared
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Multiple references should resolve without errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    // ========== Edge Cases ==========

    public void testUndefinedSymbolShowsError() {
        configureByText("""
                var x = undefinedSymbol
                """);

        List<HighlightInfo> warnings = getWarnings();
        assertFalse("Undefined symbol should show warning", warnings.isEmpty());
    }

    public void testKeywordNotResolved() {
        configureByText("""
                var x = true
                """);

        // Keywords should not cause errors
        List<HighlightInfo> errors = getErrors();
        assertTrue("Keywords should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testSelfReferenceInDeclaration() {
        configureByText("""
                var myVar = 123
                """);

        // Should not crash
        myFixture.doHighlighting();
    }
}
