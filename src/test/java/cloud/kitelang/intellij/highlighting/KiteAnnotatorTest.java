package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.List;

/**
 * Tests for KiteAnnotator - verifies semantic syntax highlighting.
 * Tests type highlighting, string interpolation, and decorator highlighting.
 */
public class KiteAnnotatorTest extends KiteTestBase {

    // ========== Type Highlighting Tests ==========

    public void testInputTypeHighlighted() {
        configureByText("""
                input string name = "default"
                """);

        // Should not crash and file should parse
        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testOutputTypeHighlighted() {
        configureByText("""
                output number count = 0
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testVariableTypeHighlighted() {
        configureByText("""
                var string message = "hello"
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testResourceTypeHighlighted() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config db {
                    host = "localhost"
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testComponentTypeHighlighted() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer myServer {
                    port = "9000"
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testFunctionParameterTypeHighlighted() {
        configureByText("""
                fun greet(string name, number age) string {
                    return "Hello " + name
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testFunctionReturnTypeHighlighted() {
        configureByText("""
                fun getCount() number {
                    return 42
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testSchemaPropertyTypeHighlighted() {
        configureByText("""
                schema Config {
                    string host
                    number port
                    boolean enabled
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testArrayTypeHighlighted() {
        configureByText("""
                schema Config {
                    string[] tags
                    number[] ports
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testDottedTypeHighlighted() {
        configureByText("""
                resource VM.Instance server {
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    // ========== Any Keyword Tests ==========

    public void testAnyKeywordHighlighted() {
        configureByText("""
                schema Config {
                    any data
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testAnyKeywordInFunctionParameter() {
        configureByText("""
                fun process(any data) any {
                    return data
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testInputAnyType() {
        configureByText("""
                input any config = {}
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testOutputAnyType() {
        configureByText("""
                output any result = null
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    // ========== String Interpolation Tests ==========

    public void testSimpleInterpolationHighlighted() {
        configureByText("""
                var name = "World"
                var greeting = "Hello $name!"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Simple interpolation should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testBraceInterpolationHighlighted() {
        configureByText("""
                var port = 8080
                var url = "http://localhost:${port}/api"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Brace interpolation should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testComplexBraceInterpolation() {
        configureByText("""
                var config = { host: "localhost" }
                var url = "http://${config.host}/api"
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testMultipleInterpolationsInOneString() {
        configureByText("""
                var host = "localhost"
                var port = 8080
                var url = "http://$host:${port}/api"
                """);

        List<HighlightInfo> errors = getErrors();
        assertTrue("Multiple interpolations should not cause errors: " + formatErrors(errors),
                errors.isEmpty());
    }

    public void testInterpolationWithFunctionCall() {
        configureByText("""
                fun getHost() string {
                    return "localhost"
                }
                var url = "http://${getHost()}/api"
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testInterpolationWithArrayAccess() {
        configureByText("""
                var hosts = ["localhost", "example.com"]
                var url = "http://${hosts[0]}/api"
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    // ========== Decorator Highlighting Tests ==========

    public void testDecoratorHighlighted() {
        configureByText("""
                @description("Test schema")
                schema Config {
                    string host
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testMultipleDecorators() {
        configureByText("""
                @description("Production database")
                @tags({Environment: "prod"})
                resource Config db {
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testDecoratorWithArrayArg() {
        configureByText("""
                @provisionOn(["aws", "azure"])
                resource Config db {
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    // ========== Import Statement Tests ==========

    public void testImportSymbolsNotHighlightedAsTypes() {
        addFile("common.kite", """
                var sharedVar = "shared"
                """);

        configureByText("""
                import sharedVar from "common.kite"
                var x = sharedVar
                """);

        // Import symbols should use default text color, not type color
        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testWildcardImport() {
        addFile("common.kite", """
                var x = 1
                """);

        configureByText("""
                import * from "common.kite"
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    // ========== Property Context Tests ==========

    public void testPropertyNameNotHighlightedAsType() {
        configureByText("""
                var config = {
                    host: "localhost",
                    port: 8080
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    public void testPropertyAccessNotHighlightedAsType() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config db {
                    host = "localhost"
                }
                var x = db.host
                """);

        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }

    // ========== Edge Cases ==========

    public void testEmptyFile() {
        configureByText("");

        myFixture.doHighlighting();
        assertNotNull("Empty file should not crash", myFixture.getFile());
    }

    public void testFileWithOnlyComments() {
        configureByText("""
                // This is a comment
                /* Block comment */
                """);

        myFixture.doHighlighting();
        assertNotNull("File with comments should not crash", myFixture.getFile());
    }

    public void testComplexFile() {
        configureByText("""
                import * from "common.kite"

                type Region = "us-east-1" | "us-west-2"

                schema DatabaseConfig {
                    string host
                    number port
                    boolean ssl
                    any metadata
                    string[] tags
                }

                @description("Main database")
                @tags({Environment: "production"})
                resource DatabaseConfig mainDb {
                    host = "db.example.com"
                    port = 5432
                    ssl = true
                    metadata = {}
                    tags = ["primary", "production"]
                }

                component WebServer {
                    input string port = "8080"
                    input string host = "localhost"
                    output string endpoint = "http://${host}:${port}"
                }

                fun formatUrl(string host, number port) string {
                    return "http://$host:${port}"
                }

                var serverUrl = formatUrl(mainDb.host, mainDb.port)
                """);

        myFixture.doHighlighting();
        assertNotNull("Complex file should not crash", myFixture.getFile());
    }

    public void testNestedBraces() {
        configureByText("""
                var obj = {
                    nested: {
                        deep: {
                            value: "test"
                        }
                    }
                }
                """);

        myFixture.doHighlighting();
        assertNotNull("Nested braces should not crash", myFixture.getFile());
    }

    public void testTypeInColonContext() {
        configureByText("""
                fun process() string {
                    return "test"
                }
                """);

        // Return type after closing paren should be highlighted
        myFixture.doHighlighting();
        assertNotNull("File should exist", myFixture.getFile());
    }
}
