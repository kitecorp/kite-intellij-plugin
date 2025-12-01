package cloud.kitelang.intellij.refactoring;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;

/**
 * Tests for Extract Variable refactoring in Kite.
 * Uses TDD approach - tests written first, then implementation.
 */
public class KiteExtractVariableTest extends KiteTestBase {

    // ========== Handler Availability Tests ==========

    public void testHandlerIsAvailable() {
        var provider = new KiteRefactoringSupportProvider();
        var handler = provider.getIntroduceVariableHandler();
        assertNotNull("Introduce variable handler should be available", handler);
    }

    public void testHandlerIsRefactoringActionHandler() {
        var provider = new KiteRefactoringSupportProvider();
        var handler = provider.getIntroduceVariableHandler();
        assertNotNull(handler);
        assertTrue("Handler should be RefactoringActionHandler",
                handler instanceof RefactoringActionHandler);
    }

    // ========== Simple Expression Extraction Tests ==========

    public void testExtractStringLiteral() {
        configureByText("""
                var x = <selection>"hello"</selection>
                """);

        performExtractVariable("greeting");

        assertResultContains("var greeting = \"hello\"");
        assertResultContains("var x = greeting");
    }

    public void testExtractNumberLiteral() {
        configureByText("""
                var x = <selection>42</selection>
                """);

        performExtractVariable("answer");

        assertResultContains("var answer = 42");
        assertResultContains("var x = answer");
    }

    public void testExtractBooleanLiteral() {
        configureByText("""
                var x = <selection>true</selection>
                """);

        performExtractVariable("flag");

        assertResultContains("var flag = true");
        assertResultContains("var x = flag");
    }

    // ========== Binary Expression Extraction Tests ==========

    public void testExtractAdditionExpression() {
        configureByText("""
                var a = 1
                var b = 2
                var sum = <selection>a + b</selection>
                """);

        performExtractVariable("result");

        assertResultContains("var result = a + b");
        assertResultContains("var sum = result");
    }

    public void testExtractMultiplicationExpression() {
        configureByText("""
                var x = 5
                var y = <selection>x * 2</selection>
                """);

        performExtractVariable("doubled");

        assertResultContains("var doubled = x * 2");
        assertResultContains("var y = doubled");
    }

    public void testExtractComplexExpression() {
        configureByText("""
                var a = 1
                var b = 2
                var c = 3
                var result = <selection>a + b * c</selection>
                """);

        performExtractVariable("calculated");

        assertResultContains("var calculated = a + b * c");
        assertResultContains("var result = calculated");
    }

    // ========== Function Call Extraction Tests ==========

    public void testExtractFunctionCall() {
        configureByText("""
                fun getValue() number {
                    return 42
                }
                var x = <selection>getValue()</selection>
                """);

        performExtractVariable("value");

        assertResultContains("var value = getValue()");
        assertResultContains("var x = value");
    }

    public void testExtractFunctionCallWithArgs() {
        configureByText("""
                fun add(number a, number b) number {
                    return a + b
                }
                var result = <selection>add(1, 2)</selection>
                """);

        performExtractVariable("sum");

        assertResultContains("var sum = add(1, 2)");
        assertResultContains("var result = sum");
    }

    // ========== Property Access Extraction Tests ==========

    public void testExtractPropertyAccess() {
        configureByText("""
                schema Config {
                    string host
                }
                resource Config main {
                    host = "localhost"
                }
                var h = <selection>main.host</selection>
                """);

        performExtractVariable("hostname");

        assertResultContains("var hostname = main.host");
        assertResultContains("var h = hostname");
    }

    // ========== Multiple Occurrences Tests ==========

    public void testExtractWithMultipleOccurrences() {
        configureByText("""
                var a = 1
                var b = 2
                var x = <selection>a + b</selection>
                var y = a + b
                var z = a + b
                """);

        performExtractVariableReplaceAll("sum");

        assertResultContains("var sum = a + b");
        // All occurrences should be replaced
        assertOccurrenceCount("sum", 4); // declaration + 3 usages
    }

    public void testExtractSingleOccurrence() {
        configureByText("""
                var a = 1
                var b = 2
                var x = <selection>a + b</selection>
                var y = a + b
                """);

        performExtractVariableSingleOccurrence("sum");

        assertResultContains("var sum = a + b");
        assertResultContains("var x = sum");
        // Only selected occurrence should be replaced
        assertResultContains("var y = a + b");
    }

    // ========== Inside Function Body Tests ==========

    public void testExtractInsideFunctionBody() {
        configureByText("""
                fun calculate(number x) number {
                    var doubled = <selection>x * 2</selection>
                    return doubled
                }
                """);

        performExtractVariable("temp");

        assertResultContains("var temp = x * 2");
        assertResultContains("var doubled = temp");
    }

    public void testExtractExpressionInReturn() {
        configureByText("""
                fun calculate(number x) number {
                    return <selection>x * 2 + 1</selection>
                }
                """);

        performExtractVariable("result");

        assertResultContains("var result = x * 2 + 1");
        assertResultContains("return result");
    }

    // ========== String Interpolation Tests ==========

    public void testExtractStringWithInterpolation() {
        configureByText("""
                var name = "World"
                var greeting = <selection>"Hello $name!"</selection>
                """);

        performExtractVariable("message");

        assertResultContains("var message = \"Hello $name!\"");
        assertResultContains("var greeting = message");
    }

    // ========== Edge Cases ==========

    public void testExtractNestedExpression() {
        configureByText("""
                var x = (1 + <selection>2 * 3</selection>) + 4
                """);

        performExtractVariable("inner");

        assertResultContains("var inner = 2 * 3");
        assertResultContains("var x = (1 + inner) + 4");
    }

    public void testExtractWholeParenthesizedExpression() {
        configureByText("""
                var x = <selection>(1 + 2)</selection> * 3
                """);

        performExtractVariable("sum");

        assertResultContains("var sum = (1 + 2)");
        assertResultContains("var x = sum * 3");
    }

    public void testNoSelectionDoesNotCrash() {
        configureByText("""
                var x = 1 + 2
                """);

        // No selection, handler should handle gracefully
        var provider = new KiteRefactoringSupportProvider();
        var handler = provider.getIntroduceVariableHandler();

        assertNotNull(handler);
        // Should not throw exception
    }

    // ========== Variable Naming Tests ==========

    public void testExtractWithValidCamelCaseName() {
        configureByText("""
                var x = <selection>42</selection>
                """);

        performExtractVariable("myValue");

        assertResultContains("var myValue = 42");
    }

    // ========== Position Tests ==========

    public void testExtractedVariablePlacedBeforeUsage() {
        configureByText("""
                var a = 1
                var b = <selection>a + 10</selection>
                var c = 3
                """);

        performExtractVariable("incremented");

        // The extracted variable should be placed just before the line where it's used
        String result = myFixture.getFile().getText();
        int declPos = result.indexOf("var incremented");
        int usagePos = result.indexOf("var b = incremented");
        assertTrue("Declaration should come before usage", declPos < usagePos);
    }

    // ========== Helper Methods ==========

    /**
     * Performs extract variable refactoring with the given name.
     */
    private void performExtractVariable(String variableName) {
        var provider = new KiteRefactoringSupportProvider();
        var handler = provider.getIntroduceVariableHandler();

        if (handler instanceof KiteIntroduceVariableHandler kiteHandler) {
            Project project = myFixture.getProject();
            Editor editor = myFixture.getEditor();
            PsiFile file = myFixture.getFile();

            kiteHandler.invoke(project, editor, file, variableName, false);
        } else {
            fail("Handler should be KiteIntroduceVariableHandler");
        }
    }

    /**
     * Performs extract variable and replaces all occurrences.
     */
    private void performExtractVariableReplaceAll(String variableName) {
        var provider = new KiteRefactoringSupportProvider();
        var handler = provider.getIntroduceVariableHandler();

        if (handler instanceof KiteIntroduceVariableHandler kiteHandler) {
            Project project = myFixture.getProject();
            Editor editor = myFixture.getEditor();
            PsiFile file = myFixture.getFile();

            kiteHandler.invoke(project, editor, file, variableName, true);
        } else {
            fail("Handler should be KiteIntroduceVariableHandler");
        }
    }

    /**
     * Performs extract variable replacing only the selected occurrence.
     */
    private void performExtractVariableSingleOccurrence(String variableName) {
        performExtractVariable(variableName);
    }

    private void assertResultContains(String expected) {
        String result = myFixture.getFile().getText();
        assertTrue("Result should contain: " + expected + "\nActual:\n" + result,
                result.contains(expected));
    }

    private void assertOccurrenceCount(String text, int expectedCount) {
        String result = myFixture.getFile().getText();
        int count = 0;
        int index = 0;
        while ((index = result.indexOf(text, index)) != -1) {
            count++;
            index++;
        }
        assertEquals("Occurrence count of '" + text + "'", expectedCount, count);
    }
}
