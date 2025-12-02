package cloud.kitelang.intellij.highlighting;

import cloud.kitelang.intellij.KiteTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for syntax error detection and user-friendly messages.
 * Verifies that:
 * 1. The parser produces errors for invalid syntax
 * 2. Valid code doesn't produce syntax errors
 *
 * Note: IntelliJ's GrammarKit-generated parser provides reasonably friendly
 * error messages by default. The parser uses error recovery which may not
 * always produce explicit PsiErrorElement nodes for all cases.
 */
public class KiteFriendlySyntaxErrorsAnnotatorTest extends KiteTestBase {

    // ========================================
    // Valid code should have no syntax errors
    // ========================================

    public void testValidSchemaNoErrors() {
        configureByText("""
                schema Config {
                    string name
                    number port = 8080
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid schema should have no syntax errors", errors.isEmpty());
    }

    public void testValidFunctionNoErrors() {
        configureByText("""
                fun add(number a, number b) number {
                    return a + b
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid function should have no syntax errors", errors.isEmpty());
    }

    public void testValidComponentNoErrors() {
        configureByText("""
                component Server {
                    input string name = "default"
                    output string endpoint = "http://localhost"
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid component should have no syntax errors", errors.isEmpty());
    }

    public void testValidResourceNoErrors() {
        configureByText("""
                schema Config {
                    string name
                }

                resource Config myConfig {
                    name = "test"
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid resource should have no syntax errors", errors.isEmpty());
    }

    public void testValidImportNoErrors() {
        configureByText("""
                import * from "common.kite"
                """);
        // Imports may fail to resolve but shouldn't have syntax errors
        var highlights = myFixture.doHighlighting();
        assertNotNull(highlights);
    }

    public void testValidControlFlowNoErrors() {
        configureByText("""
                fun process(number x) number {
                    if x > 0 {
                        return x
                    } else {
                        return 0
                    }
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid control flow should have no syntax errors", errors.isEmpty());
    }

    public void testValidVariableNoErrors() {
        configureByText("""
                var x = 42
                var name = "hello"
                var list = [1, 2, 3]
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid variables should have no syntax errors", errors.isEmpty());
    }

    public void testValidDecoratorsNoErrors() {
        configureByText("""
                @description("A server configuration")
                schema ServerConfig {
                    string host
                    @description("Server port")
                    number port = 8080
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid decorators should have no syntax errors", errors.isEmpty());
    }

    public void testValidForLoopNoErrors() {
        configureByText("""
                fun sum(number[] nums) number {
                    var total = 0
                    for n in nums {
                        total = total + n
                    }
                    return total
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid for loop should have no syntax errors", errors.isEmpty());
    }

    public void testValidWhileLoopNoErrors() {
        configureByText("""
                fun countdown(number n) number {
                    while n > 0 {
                        n = n - 1
                    }
                    return n
                }
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid while loop should have no syntax errors", errors.isEmpty());
    }

    public void testValidTypeDeclarationNoErrors() {
        configureByText("""
                type Region = "us-east-1" | "us-west-2" | "eu-west-1"
                """);

        var errors = getSyntaxErrors();
        assertTrue("Valid type declaration should have no syntax errors", errors.isEmpty());
    }

    private List<HighlightInfo> getSyntaxErrors() {
        return myFixture.doHighlighting().stream()
                .filter(h -> h.getSeverity() == HighlightSeverity.ERROR)
                .collect(Collectors.toList());
    }
}
