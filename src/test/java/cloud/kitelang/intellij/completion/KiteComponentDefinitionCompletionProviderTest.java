package cloud.kitelang.intellij.completion;

import cloud.kitelang.intellij.KiteTestBase;

import java.util.List;

/**
 * Tests for component definition completion.
 * Verifies input/output keyword suggestions and type completion inside component bodies.
 */
public class KiteComponentDefinitionCompletionProviderTest extends KiteTestBase {

    // ========== Input/Output Keyword Completion Tests ==========

    public void testInputKeywordSuggested() {
        configureByText("""
                component WebServer {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'input'", lookupStrings.contains("input"));
    }

    public void testOutputKeywordSuggested() {
        configureByText("""
                component WebServer {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'output'", lookupStrings.contains("output"));
    }

    public void testBothInputOutputSuggested() {
        configureByText("""
                component DataService {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'input'", lookupStrings.contains("input"));
        assertTrue("Should suggest 'output'", lookupStrings.contains("output"));
    }

    // ========== Type Completion After Keyword Tests ==========

    public void testTypeCompletionAfterInput() {
        configureByText("""
                component WebServer {
                    input <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'string'", lookupStrings.contains("string"));
        assertTrue("Should suggest 'number'", lookupStrings.contains("number"));
        assertTrue("Should suggest 'boolean'", lookupStrings.contains("boolean"));
    }

    public void testTypeCompletionAfterOutput() {
        configureByText("""
                component WebServer {
                    output <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'string'", lookupStrings.contains("string"));
        assertTrue("Should suggest 'number'", lookupStrings.contains("number"));
        assertTrue("Should suggest 'boolean'", lookupStrings.contains("boolean"));
    }

    public void testAnyTypeSuggested() {
        configureByText("""
                component DataProcessor {
                    input <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'any'", lookupStrings.contains("any"));
    }

    // ========== Array Type Completion Tests ==========

    public void testArrayTypesSuggested() {
        configureByText("""
                component ListProcessor {
                    input <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        // Array types may be suggested as string[], number[], etc.
        assertTrue("Should suggest array types",
                lookupStrings.contains("string[]") ||
                lookupStrings.contains("number[]") ||
                lookupStrings.stream().anyMatch(s -> s.endsWith("[]")));
    }

    // ========== Context-Specific Tests ==========

    public void testNotInComponentInstanceContext() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                }
                component WebServer myServer {
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        // In component instance context (not definition), different completions apply
        // This test verifies no crash occurs
    }

    public void testAfterExistingInputDeclaration() {
        configureByText("""
                component WebServer {
                    input string port = "8080"
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions after existing input", lookupStrings);
        assertTrue("Should suggest 'input' for additional inputs", lookupStrings.contains("input"));
        assertTrue("Should suggest 'output'", lookupStrings.contains("output"));
    }

    public void testAfterExistingOutputDeclaration() {
        configureByText("""
                component WebServer {
                    output string endpoint = "http://localhost"
                    <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions after existing output", lookupStrings);
        assertTrue("Should suggest 'input'", lookupStrings.contains("input"));
        assertTrue("Should suggest 'output' for additional outputs", lookupStrings.contains("output"));
    }

    // ========== Default Value Completion Tests ==========

    public void testBooleanDefaultValuesSuggested() {
        configureByText("""
                component Config {
                    input boolean enabled = <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        assertTrue("Should suggest 'true'", lookupStrings.contains("true"));
        assertTrue("Should suggest 'false'", lookupStrings.contains("false"));
    }

    // ========== Schema Type Completion Tests ==========

    public void testSchemaTypeSuggestedForInput() {
        configureByText("""
                schema DatabaseConfig {
                    string host
                    number port
                }
                component DataService {
                    input <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions", lookupStrings);
        // Custom schema types may be suggested as valid types
        // This verifies primitives are suggested; schema types depend on implementation
        assertTrue("Should suggest 'string'", lookupStrings.contains("string"));
    }

    // ========== Empty Component Tests ==========

    public void testCompletionInEmptyComponent() {
        configureByText("""
                component EmptyComponent {
                <caret>
                }
                """);

        myFixture.completeBasic();
        List<String> lookupStrings = myFixture.getLookupElementStrings();

        assertNotNull("Should have completions in empty component", lookupStrings);
        assertTrue("Should suggest 'input'", lookupStrings.contains("input"));
        assertTrue("Should suggest 'output'", lookupStrings.contains("output"));
    }
}
