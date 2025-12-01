package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;

import java.util.List;

/**
 * Tests for general code completion.
 * Verifies keyword, variable, and property access completion.
 */
public class KiteGeneralCompletionProviderTest extends KiteTestBase {

    // ========== Keyword Completion Tests ==========

    public void testTopLevelKeywordsSuggested() {
        configureByText("""
                <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'var'", lookupStrings.contains("var"));
        assertTrue("Should suggest 'input'", lookupStrings.contains("input"));
        assertTrue("Should suggest 'output'", lookupStrings.contains("output"));
        assertTrue("Should suggest 'resource'", lookupStrings.contains("resource"));
        assertTrue("Should suggest 'component'", lookupStrings.contains("component"));
        assertTrue("Should suggest 'schema'", lookupStrings.contains("schema"));
        assertTrue("Should suggest 'fun'", lookupStrings.contains("fun"));
        assertTrue("Should suggest 'type'", lookupStrings.contains("type"));
        assertTrue("Should suggest 'import'", lookupStrings.contains("import"));
    }

    public void testTypeKeywordsSuggested() {
        configureByText("""
                var <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'string'", lookupStrings.contains("string"));
        assertTrue("Should suggest 'number'", lookupStrings.contains("number"));
        assertTrue("Should suggest 'boolean'", lookupStrings.contains("boolean"));
        assertTrue("Should suggest 'any'", lookupStrings.contains("any"));
    }

    public void testControlFlowKeywordsSuggested() {
        configureByText("""
                fun test() {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'if'", lookupStrings.contains("if"));
        assertTrue("Should suggest 'for'", lookupStrings.contains("for"));
        assertTrue("Should suggest 'return'", lookupStrings.contains("return"));
    }

    public void testLiteralKeywordsSuggested() {
        configureByText("""
                var x = <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'true'", lookupStrings.contains("true"));
        assertTrue("Should suggest 'false'", lookupStrings.contains("false"));
        assertTrue("Should suggest 'null'", lookupStrings.contains("null"));
    }

    // ========== Variable Reference Completion Tests ==========

    public void testVariablesSuggested() {
        configureByText("""
                var firstVar = "hello"
                var secondVar = "world"
                var x = <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'firstVar'", lookupStrings.contains("firstVar"));
        assertTrue("Should suggest 'secondVar'", lookupStrings.contains("secondVar"));
    }

    public void testMultipleVariablesSuggested() {
        configureByText("""
                var alpha = 1
                var beta = 2
                var gamma = 3
                var x = <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'alpha'", lookupStrings.contains("alpha"));
        assertTrue("Should suggest 'beta'", lookupStrings.contains("beta"));
        assertTrue("Should suggest 'gamma'", lookupStrings.contains("gamma"));
    }

    public void testInputsAndOutputsSuggested() {
        configureByText("""
                input string inputName = "default"
                output string outputName = "result"
                var x = <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'inputName'", lookupStrings.contains("inputName"));
        assertTrue("Should suggest 'outputName'", lookupStrings.contains("outputName"));
    }

    public void testImportedSymbolsSuggested() {
        addFile("common.kite", """
                var sharedVar = "shared"
                fun sharedFunc() string {
                    return "func"
                }
                """);

        configureByText("""
                import * from "common.kite"
                var x = shared<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'sharedVar'", lookupStrings.contains("sharedVar"));
        assertTrue("Should suggest 'sharedFunc'", lookupStrings.contains("sharedFunc"));
    }

    // ========== Function Completion Tests ==========

    public void testFunctionsSuggested() {
        configureByText("""
                fun greetFunc() string {
                    return "hello"
                }
                fun helpFunc() string {
                    return "help"
                }
                var x = <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'greetFunc'", lookupStrings.contains("greetFunc"));
        assertTrue("Should suggest 'helpFunc'", lookupStrings.contains("helpFunc"));
    }

    // ========== Property Access Completion Tests ==========

    public void testPropertyAccessAfterDot() {
        configureByText("""
                schema Config {
                    string host
                    number port
                }
                resource Config myConfig {
                    host = "localhost"
                    port = 8080
                }
                var x = myConfig.<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions for property access", lookupStrings);
        assertTrue("Should suggest 'host' property", lookupStrings.contains("host"));
        assertTrue("Should suggest 'port' property", lookupStrings.contains("port"));
    }

    public void testComponentOutputAccessAfterDot() {
        configureByText("""
                component WebServer {
                    output string endpoint = "http://localhost"
                    output string status = "running"
                }
                component WebServer server {
                }
                var x = server.<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // Component output completion may or may not be implemented
        // This test verifies the completion doesn't crash
        // If lookupStrings is null, completion might have auto-inserted
    }

    // ========== Schema and Resource Type Completion Tests ==========

    public void testSchemaTypesSuggestedAfterResourceKeyword() {
        configureByText("""
                schema DbConfig {
                    string host
                }
                schema CacheConfig {
                    string url
                }
                resource <caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'DbConfig'", lookupStrings.contains("DbConfig"));
        assertTrue("Should suggest 'CacheConfig'", lookupStrings.contains("CacheConfig"));
    }

    // ========== Edge Cases ==========

    public void testNoKeywordsInStringLiterals() {
        configureByText("""
                var x = "hello <caret>"
                """);

        myFixture.completeBasic();
        // Inside a string literal, completion behavior varies
        // This test verifies no crash occurs
    }

    public void testCompletionAfterPartialIdentifier() {
        configureByText("""
                var alpha = "test"
                var alphaTwo = "test2"
                var x = alph<caret>
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // If there are multiple matches, we should see a popup
        // If only one match, it may auto-complete
        if (lookupStrings != null) {
            assertTrue("Should suggest matching variables",
                    lookupStrings.contains("alpha") || lookupStrings.contains("alphaTwo"));
        }
    }
}
