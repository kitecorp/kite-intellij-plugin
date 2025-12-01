package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for the type checking annotator.
 * Verifies undefined reference detection, type mismatch detection, and decorator validation.
 */
public class KiteTypeCheckingAnnotatorTest extends KiteTestBase {

    // ========== Undefined Reference Detection Tests ==========

    public void testUndefinedVariableShowsWarning() {
        configureByText("""
                var x = undefinedVar
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(1, warnings.size());
        assertEquals("Cannot resolve symbol 'undefinedVar'", warnings.get(0).getDescription());
    }

    public void testDefinedVariableNoWarning() {
        configureByText("""
                var myVar = "hello"
                var x = myVar
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testImportedVariableNoWarning() {
        addFile("common.kite", """
                var sharedVar = "shared"
                """);

        configureByText("""
                import * from "common.kite"
                var x = sharedVar
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testNamedImportVariableNoWarning() {
        addFile("common.kite", """
                var alpha = "a"
                var beta = "b"
                """);

        configureByText("""
                import alpha from "common.kite"
                var x = alpha
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testBuiltinFunctionsNoWarning() {
        configureByText("""
                var x = print("hello")
                var y = println("world")
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testInputDeclarationNoWarning() {
        configureByText("""
                input string name = "default"
                var x = name
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testOutputDeclarationNoWarning() {
        configureByText("""
                output string result = "value"
                var x = result
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testResourceDeclarationNoWarning() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config myDb {
                    host = "localhost"
                }
                var x = myDb
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testFunctionDeclarationNoWarning() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                var x = greet("World")
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testForLoopVariableNoWarning() {
        configureByText("""
                var items = ["a", "b", "c"]
                for item in items {
                    var x = item
                }
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    // ========== Type Mismatch Detection Tests ==========

    public void testStringToNumberMismatchShowsError() {
        configureByText("""
                var number x = "hello"
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getDescription().contains("Type mismatch"));
        assertTrue(errors.get(0).getDescription().contains("number"));
        assertTrue(errors.get(0).getDescription().contains("string"));
    }

    public void testNumberToStringMismatchShowsError() {
        configureByText("""
                var string x = 123
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getDescription().contains("Type mismatch"));
    }

    public void testBooleanMismatchShowsError() {
        configureByText("""
                var boolean x = "true"
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getDescription().contains("Type mismatch"));
    }

    public void testCorrectTypeAssignmentNoError() {
        configureByText("""
                var string s = "hello"
                var number n = 42
                var boolean b = true
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    public void testTypeInferenceNoError() {
        configureByText("""
                var x = "hello"
                var y = 42
                var z = true
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    // ========== Any Type Handling Tests ==========

    public void testAnyTypeAcceptsString() {
        configureByText("""
                var any x = "hello"
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    public void testAnyTypeAcceptsNumber() {
        configureByText("""
                var any x = 123
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    public void testAnyTypeAcceptsBoolean() {
        configureByText("""
                var any x = true
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    public void testInputAnyTypeNoError() {
        configureByText("""
                input any data = "anything"
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    public void testOutputAnyTypeNoError() {
        configureByText("""
                output any result = 123
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    // ========== Array Type Handling Tests ==========

    public void testArrayTypePropertyDefinitionNoWarning() {
        configureByText("""
                schema Config {
                    string[] tags
                }
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testNumberArrayTypeNoWarning() {
        configureByText("""
                schema Data {
                    number[] values
                }
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    public void testAnyArrayTypeNoWarning() {
        configureByText("""
                schema Container {
                    any[] items
                }
                """);

        var warnings = getUndefinedReferenceWarnings();
        assertEquals(0, warnings.size());
    }

    // ========== Decorator Validation Tests ==========

    public void testValidDecoratorNoWarning() {
        configureByText("""
                @description("A config value")
                input string name = "default"
                """);

        var warnings = getDecoratorWarnings();
        assertEquals(0, warnings.size());
    }

    public void testUnknownDecoratorShowsWarning() {
        configureByText("""
                @unknownDecorator
                input string name = "default"
                """);

        var warnings = getDecoratorWarnings();
        assertEquals(1, warnings.size());
        assertEquals("Unknown decorator '@unknownDecorator'", warnings.get(0).getDescription());
    }

    public void testMultipleValidDecoratorsNoWarning() {
        configureByText("""
                @description("Min value")
                @minValue(0)
                @maxValue(100)
                input number percent = 50
                """);

        var warnings = getDecoratorWarnings();
        assertEquals(0, warnings.size());
    }

    public void testCloudDecoratorNoWarning() {
        configureByText("""
                schema Config {
                    @cloud
                    string id
                }
                """);

        var warnings = getDecoratorWarnings();
        assertEquals(0, warnings.size());
    }

    public void testResourceDecoratorsNoWarning() {
        configureByText("""
                schema Server {
                    string host
                }
                @existing
                @dependsOn([])
                @tags({})
                resource Server myServer {
                    host = "localhost"
                }
                """);

        var warnings = getDecoratorWarnings();
        assertEquals(0, warnings.size());
    }

    // ========== Import Ordering Tests ==========

    public void testImportsAtTopNoError() {
        addFile("common.kite", """
                var x = 1
                """);

        configureByText("""
                import * from "common.kite"
                var y = x
                """);

        var errors = getImportOrderingErrors();
        assertEquals(0, errors.size());
    }

    public void testImportAfterDeclarationShowsError() {
        addFile("common.kite", """
                var x = 1
                """);

        configureByText("""
                var y = 1
                import * from "common.kite"
                """);

        var errors = getImportOrderingErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getDescription().contains("Import statements must appear at the beginning"));
    }

    // ========== Schema Property Type Checking Tests ==========

    public void testResourcePropertyCorrectTypeNoError() {
        configureByText("""
                schema DbConfig {
                    string host
                    number port
                }
                resource DbConfig myDb {
                    host = "localhost"
                    port = 5432
                }
                """);

        var errors = getTypeMismatchErrors();
        assertEquals(0, errors.size());
    }

    // ========== Helper Methods ==========

    private List<HighlightInfo> getUndefinedReferenceWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().contains("Cannot resolve symbol"))
                .collect(Collectors.toList());
    }

    private List<HighlightInfo> getTypeMismatchErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().contains("Type mismatch"))
                .collect(Collectors.toList());
    }

    private List<HighlightInfo> getDecoratorWarnings() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().contains("Unknown decorator"))
                .collect(Collectors.toList());
    }

    private List<HighlightInfo> getImportOrderingErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter(h -> h.getDescription() != null &&
                             h.getDescription().contains("Import statements must appear"))
                .collect(Collectors.toList());
    }
}
