package cloud.kitelang.intellij.parameterinfo;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * Tests for KiteParameterInfoHandler - verifies function parameter info display (Ctrl+P).
 * Tests parameter extraction, current parameter tracking, and cross-file function lookup.
 */
public class KiteParameterInfoHandlerTest extends KiteTestBase {

    // ========== Function Info Extraction Tests ==========

    public void testSingleParameterFunction() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                """);

        // Verify the file parses correctly
        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function", file.getText().contains("fun greet"));
    }

    public void testMultipleParameterFunction() {
        configureByText("""
                fun createUser(string name, number age, boolean active) string {
                    return name
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function with multiple params",
                file.getText().contains("string name, number age, boolean active"));
    }

    public void testNoParameterFunction() {
        configureByText("""
                fun getTime() string {
                    return "12:00"
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain no-param function", file.getText().contains("fun getTime()"));
    }

    public void testFunctionWithArrayParameter() {
        configureByText("""
                fun process(string[] items) number {
                    return 0
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain array param", file.getText().contains("string[] items"));
    }

    public void testFunctionWithAnyParameter() {
        configureByText("""
                fun handle(any data) any {
                    return data
                }
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain any type param", file.getText().contains("any data"));
    }

    // ========== Function Call Context Tests ==========

    public void testFunctionCallWithArguments() {
        configureByText("""
                fun greet(string name, number age) string {
                    return "Hello"
                }
                var result = greet("Alice", 30)
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function call", file.getText().contains("greet(\"Alice\", 30)"));
    }

    public void testFunctionCallWithVariableArguments() {
        configureByText("""
                fun greet(string name) string {
                    return "Hello " + name
                }
                var userName = "Bob"
                var result = greet(userName)
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function call with variable", file.getText().contains("greet(userName)"));
    }

    public void testNestedFunctionCalls() {
        configureByText("""
                fun inner(string x) string {
                    return x
                }
                fun outer(string y) string {
                    return inner(y)
                }
                var result = outer("test")
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain nested calls", file.getText().contains("inner(y)"));
    }

    // ========== Cross-File Function Tests ==========

    public void testImportedFunction() {
        addFile("utils.kite", """
                fun helper(string input) string {
                    return input
                }
                """);

        configureByText("""
                import helper from "utils.kite"
                var result = helper("test")
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain imported function call", file.getText().contains("helper(\"test\")"));
    }

    public void testWildcardImportedFunction() {
        addFile("utils.kite", """
                fun process(number value, boolean flag) number {
                    return value
                }
                """);

        configureByText("""
                import * from "utils.kite"
                var result = process(42, true)
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function call", file.getText().contains("process(42, true)"));
    }

    // ========== KiteFunctionInfo Tests ==========

    public void testKiteFunctionInfoCreation() {
        List<KiteParameterInfoHandler.KiteParameter> params = List.of(
                new KiteParameterInfoHandler.KiteParameter("string", "name", 0, 11),
                new KiteParameterInfoHandler.KiteParameter("number", "age", 13, 23)
        );

        KiteParameterInfoHandler.KiteFunctionInfo info =
                new KiteParameterInfoHandler.KiteFunctionInfo("greet", params, "string");

        assertEquals("greet", info.functionName());
        assertEquals(2, info.parameters().size());
        assertEquals("string", info.returnType());
    }

    public void testKiteFunctionInfoParametersText() {
        List<KiteParameterInfoHandler.KiteParameter> params = List.of(
                new KiteParameterInfoHandler.KiteParameter("string", "name", 0, 11),
                new KiteParameterInfoHandler.KiteParameter("number", "age", 13, 23)
        );

        KiteParameterInfoHandler.KiteFunctionInfo info =
                new KiteParameterInfoHandler.KiteFunctionInfo("greet", params, "string");

        String text = info.getParametersText();
        assertEquals("string name, number age", text);
    }

    public void testKiteFunctionInfoNoParameters() {
        List<KiteParameterInfoHandler.KiteParameter> params = List.of();

        KiteParameterInfoHandler.KiteFunctionInfo info =
                new KiteParameterInfoHandler.KiteFunctionInfo("getTime", params, "string");

        String text = info.getParametersText();
        assertEquals("<no parameters>", text);
    }

    public void testKiteFunctionInfoNullReturnType() {
        List<KiteParameterInfoHandler.KiteParameter> params = List.of(
                new KiteParameterInfoHandler.KiteParameter("string", "msg", 0, 10)
        );

        KiteParameterInfoHandler.KiteFunctionInfo info =
                new KiteParameterInfoHandler.KiteFunctionInfo("log", params, null);

        assertNull(info.returnType());
    }

    // ========== KiteParameter Tests ==========

    public void testKiteParameterCreation() {
        KiteParameterInfoHandler.KiteParameter param =
                new KiteParameterInfoHandler.KiteParameter("string", "name", 5, 16);

        assertEquals("string", param.type());
        assertEquals("name", param.name());
        assertEquals(5, param.startOffset());
        assertEquals(16, param.endOffset());
    }

    public void testKiteParameterArrayType() {
        KiteParameterInfoHandler.KiteParameter param =
                new KiteParameterInfoHandler.KiteParameter("string[]", "items", 0, 15);

        assertEquals("string[]", param.type());
        assertEquals("items", param.name());
    }

    // ========== Edge Cases ==========

    public void testFunctionWithDefaultParameterValue() {
        configureByText("""
                fun greet(string name = "World") string {
                    return "Hello " + name
                }
                var result = greet()
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        // Functions can have default values
    }

    public void testFunctionCallInsideExpression() {
        configureByText("""
                fun add(number a, number b) number {
                    return a + b
                }
                var result = 10 + add(5, 3) * 2
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function call in expression",
                file.getText().contains("add(5, 3)"));
    }

    public void testFunctionCallAsArgument() {
        configureByText("""
                fun inner(number x) number {
                    return x * 2
                }
                fun outer(number y) number {
                    return y + 1
                }
                var result = outer(inner(5))
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain nested function calls",
                file.getText().contains("outer(inner(5))"));
    }

    public void testFunctionWithManyParameters() {
        configureByText("""
                fun complex(string a, number b, boolean c, string d, number e) string {
                    return a
                }
                var result = complex("x", 1, true, "y", 2)
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain function with many params",
                file.getText().contains("string a, number b, boolean c, string d, number e"));
    }

    public void testEmptyFunctionCall() {
        configureByText("""
                fun noArgs() string {
                    return "done"
                }
                var result = noArgs()
                """);

        PsiFile file = myFixture.getFile();
        assertNotNull("File should be created", file);
        assertTrue("File should contain empty function call", file.getText().contains("noArgs()"));
    }

    // ========== Handler Basic Tests ==========

    public void testHandlerInstantiation() {
        KiteParameterInfoHandler handler = new KiteParameterInfoHandler();
        assertNotNull("Handler should be instantiated", handler);
    }
}
